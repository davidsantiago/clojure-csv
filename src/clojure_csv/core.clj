(ns
    ^{:author "David Santiago",
      :doc "Clojure-CSV is a small library for reading and writing CSV files.
It correctly handles common CSV edge-cases, such as embedded newlines, commas,
and quotes. The main functions are parse-csv and write-csv."}
  clojure-csv.core
  (:require [clojure.string :as string])
  (:import [java.io Reader StringReader]))


;;
;; Utilities
;;

(defn- reader-peek
  ^long [^Reader reader]
  (.mark reader 1)
  (let [c (.read reader)]
    (.reset reader)
    c))

;;
;; CSV Input
;;

(defn- lf-at-reader-pos?
  "Given a reader, returns true if the reader is currently pointing at an \n
   character. Reader will not be changed when the function returns."
  [^Reader reader]
  (let [next-char (reader-peek reader)]
    (== next-char (int \newline))))

(defn- crlf-at-reader-pos?
  "Given a reader, returns true if the reader is currently pointing at an \r\n
   character sequence. Reader will not be changed when the function returns."
  [^Reader reader]
  (.mark reader 2)
  (let [result (and (== (int \return) (.read reader))
                    (== (int \newline) (.read reader)))]
    (.reset reader)
    result))

(defn- cr-at-reader-pos?
  "Given a reader, returns true if the reader is currently pointing at an \n
   character. Reader will not be changed when the function returns."
  [^Reader reader]
  (let [next-char (reader-peek reader)]
    (== next-char (int \return))))

(defn- custom-eol-at-reader-pos?
  "Given a reader and an end-of-line string, returns true if the reader is
   currently pointing at an instance of the end-of-line string. Reader will not
   be changed when the function returns."
  [^Reader reader ^String end-of-line]
  (.mark reader 16)
  (let [result (loop [curr-rdr-char (int (.read reader))
                      eol-pos (int 0)]
                 (if (>= eol-pos (int (count end-of-line)))
                   ;; Reached the end of the EOL to check for, so return success
                   true
                   ;; Didn't reach the end of the EOL string, so recur if the
                   ;; next char matches the next EOL char. Otherwise, fail.
                   (if (== curr-rdr-char (.codePointAt end-of-line eol-pos))
                     (recur (.read reader) (inc eol-pos))
                     false)))]
    (.reset reader)
    result))

(defn- eol-at-reader-pos?
  "Given a reader and optionally an end-of-line string, returns true if the
   reader is currently pointing at an end-of-line (LF/CRLF/the end-of-line arg).
   Reader will not be changed when the function returns. Note that if the
   EOL is specified, it will not check for LF/CRLF."
  ([^Reader reader]
     (or (lf-at-reader-pos? reader)
         (crlf-at-reader-pos? reader)
         (cr-at-reader-pos? reader)))
  ([^Reader reader end-of-line]
     (if end-of-line
       (custom-eol-at-reader-pos? reader end-of-line)
       (eol-at-reader-pos? reader))))

(defn- skip-past-eol
  "Given a reader that is pointing at an end-of-line
   (LF/CRLF/the end-of-line arg), moves the reader forward to the
   first character after the end-of-line sequence. Note that if the EOL is
   specified, it will not check for LF/CRLF."
  ([^Reader reader]
     ;; If we peek and see a newline (LF), then the EOL is just an LF, skip 1.
     ;; Otherwise, the EOL is a CRLF, so skip 2.
     (if (== (int \newline) (reader-peek reader))
       (.skip reader 1)
       (.skip reader 2)))
  ([^Reader reader end-of-line]
     (if end-of-line
       ;; end-of-line is specified, and we can assume we are positioned at
       ;; an eol.
       (.skip reader (count end-of-line))
       (skip-past-eol reader))))

(defn- read-unquoted-field
  "Given a reader that is queued up to the beginning of an unquoted field,
   reads the field and returns it as a string. The reader will be left at the
   first character past the end of the field."
  [^Reader reader delimiter quote-char strict end-of-line]
  (let [delimiter (int delimiter)
        quote-char (int quote-char)
        field-str (StringBuilder.)]
    (loop [c (reader-peek reader)]
      (cond (or (== c -1)
                (== c delimiter))
            (.toString field-str)
            (eol-at-reader-pos? reader end-of-line)
            (.toString field-str)
            (and strict (== c quote-char))
            (throw (Exception. "Double quote present in unquoted field."))
            :else ;; Saw a regular character that is part of the field.
            (do (.appendCodePoint field-str (.read reader))
                (recur (reader-peek reader)))))))

(defn- escaped-quote-at-reader-pos?
  "Given a reader, returns true if it is currently pointing at a character that
   is the same as quote-char. The reader position will not be changed when the
   function returns."
  [^Reader reader ^long quote-char]
  (.mark reader 2)
  (let [result (and (== quote-char (.read reader))
                    (== quote-char (.read reader)))]
    (.reset reader)
    result))

(defn- read-quoted-field
  "Given a reader that is queued up to the beginning of a quoted field,
   reads the field and returns it as a string. The reader will be left at the
   first character past the end of the field."
  [^Reader reader ^long delimiter ^long quote-char strict]
  (let [field-str (StringBuilder.)]
    (.skip reader 1) ;; Discard the quote that starts the field.
    (loop [c (reader-peek reader)]
      (cond (== c -1)
            (if strict
              (throw (Exception.
                      "Reached end of input before end of quoted field."))
              ;; Otherwise, return what we've got so far.
              (.toString field-str))
            ;; If we see two quote chars in a row, only add one of them to the
            ;; output, skip both of the characters, and continue.
            (escaped-quote-at-reader-pos? reader quote-char)
            (do (.appendCodePoint field-str quote-char)
                (.skip reader 2)
                (recur (reader-peek reader)))
            ;; Otherwise, if we see a single quote char, this field has ended.
            ;; Skip past the ending quote and return the field.
            (== c quote-char)
            (do (.skip reader 1) ;; Skip past that quote character.
                (.toString field-str))
            :else
            (do (.appendCodePoint field-str (.read reader))
                (recur (reader-peek reader)))))))

(defn- parse-csv-line
  "Takes a Reader as input and returns the first row of the CSV file,
   parsed into cells (an array of strings). The reader passed in will be
   positioned for the start of the next line."
  [^Reader csv-reader delimiter quote-char strict end-of-line]
   ;; We build the last-field variable, and then add it to fields when we
   ;; encounter some event (delimiter/eol/eof) that signals the end of
   ;; the field. This lets us correctly handle input with empty fields, like
   ;; ",,,".
   (let [delimiter (int delimiter)
         quote-char (int quote-char)]
     (loop [fields (transient []) ;; Will return this as the vector of fields.
            last-field ""
            look-ahead (reader-peek csv-reader)]
       (cond (== -1 look-ahead)
             (persistent! (conj! fields last-field))
             (== look-ahead (int delimiter))
             (do (.skip csv-reader 1)
                 (recur (conj! fields last-field) "" (reader-peek csv-reader)))
             (eol-at-reader-pos? csv-reader end-of-line)
             (do (skip-past-eol csv-reader end-of-line)
                 (persistent! (conj! fields last-field)))
             (== look-ahead (int quote-char))
             (recur fields
                    (read-quoted-field csv-reader delimiter quote-char strict)
                    (reader-peek csv-reader))
             :else
             (recur fields
                    (read-unquoted-field csv-reader delimiter quote-char
                                         strict end-of-line)
                    (reader-peek csv-reader))))))

(defn- parse-csv-with-options
  ([csv-reader {:keys [delimiter quote-char strict end-of-line]}]
     (parse-csv-with-options csv-reader delimiter quote-char
       strict end-of-line))
  ([csv-reader delimiter quote-char strict end-of-line]
      (lazy-seq
       (when (not (== -1 (reader-peek csv-reader)))
         (let [row (parse-csv-line csv-reader delimiter quote-char
                                   strict end-of-line)]
           (cons row (parse-csv-with-options csv-reader delimiter quote-char
                       strict end-of-line)))))))

(defn parse-csv
  "Takes a CSV as a string or Reader and returns a seq of the parsed CSV rows,
   in the form of a lazy sequence of vectors: a vector per row, a string for
   each cell.

   Accepts a number of keyword arguments to change the parsing behavior:
        :delimiter - A character that contains the cell separator for
                     each column in a row.  Default value: \\,
        :end-of-line - A string containing the end-of-line character
                       for reading CSV files. If this setting is nil then
                       \\n and \\r\\n are both accepted.  Default value: nil
        :quote-char - A character that is used to begin and end a quoted cell.
                      Default value: \\\"
        :strict - If this variable is true, the parser will throw an
                  exception on parse errors that are recoverable but
                  not to spec or otherwise nonsensical.  Default value: false"
  ([csv & {:as opts}]
     (let [csv-reader (if (string? csv) (StringReader. csv) csv)]
       (parse-csv-with-options csv-reader (merge {:strict false
                                                  :delimiter \,
                                                  :quote-char \"}
                                                 opts)))))

;;
;; CSV Output
;;

(defn- needs-quote?
  "Given a string (cell), determine whether it contains a character that
   requires this cell to be quoted."
  [^String cell delimiter quote-char]
  (or (.contains cell delimiter)
      (.contains cell (str quote-char))
      (.contains cell "\n")
      (.contains cell "\r")))

(defn- escape
  "Given a character, returns the escaped version, whether that is the same
   as the original character or a replacement. The return is a string or a
   character, but it all gets passed into str anyways."
  [chr delimiter quote-char]
  (if (= quote-char chr) (str quote-char quote-char) chr))

(defn- quote-and-escape
  "Given a string (cell), returns a new string that has any necessary quoting
   and escaping."
  [cell delimiter quote-char force-quote]
  (if (or force-quote (needs-quote? cell delimiter quote-char))
    (str quote-char
         (apply str (map #(escape % delimiter quote-char)
                                    cell))
         quote-char)
    cell))

(defn- quote-and-escape-row
  "Given a row (vector of strings), quotes and escapes any cells where that
   is necessary and then joins all the text into a string for that entire row."
  [row delimiter quote-char force-quote]
  (string/join delimiter (map #(quote-and-escape %
                                                 delimiter
                                                 quote-char
                                                 force-quote)
                              row)))

(defn write-csv
  "Given a sequence of sequences of strings, returns a string of that table
   in CSV format, with all appropriate quoting and escaping.

   Accepts a number of keyword arguments to change the output:
       :delimiter - A character that contains the cell separator for
                    each column in a row.  Default value: \\,
       :end-of-line - A string containing the end-of-line character
                      for writing CSV files.  Default value: \\n
       :quote-char - A character that is used to begin and end a quoted cell.
                     Default value: \\\"
       :force-quote - Forces every cell to be quoted (useful for Excel interop)
                      Default value: false"
  [table & {:keys [delimiter quote-char end-of-line force-quote]
            :or {delimiter \, quote-char \" end-of-line "\n"
                 force-quote false}}]
  (loop [csv-string (StringBuilder.)
         quoted-table (map #(quote-and-escape-row %
                                                  (str delimiter)
                                                  quote-char
                                                  force-quote)
                           table)]
    (if (empty? quoted-table)
      (.toString csv-string)
      (recur (.append csv-string (str (first quoted-table) end-of-line))
             (rest quoted-table)))))
