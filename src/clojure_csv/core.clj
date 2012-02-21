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
  [^Reader reader]
  (.mark reader 1)
  (let [c (.read reader)]
    (.reset reader)
    c))

;;
;; CSV Input
;;

;; Tests for LF and CR-LF.
;;
;; These functions take current-char as a long so that it doesn't get
;; boxed on each call in Clojure 1.3. Significantly faster, but requires
;; casting to int before calling.
(defn- lf?
  [^long current-char]
  (= (int \newline) current-char))

(defn- crlf?
  [^long current-char ^long next-char]
  (and (= (int \return) current-char)
       (= (int \newline) next-char)))

;; This is a last-resort function to check a custom-specified EOF. Probably
;; much slower. Leaves the reader where it was when it started. Takes as its
;; first arg the "current char," with the rest of the sequence in reader, so it
;; can read any more it needs to.
(defn- custom-eof?
  [^long current-char ^Reader reader eof]
  (.mark reader 16)
  (let [result (loop [curr-rdr-char current-char
                      remaining-eof (seq eof)]
                 (if (nil? remaining-eof)
                   ;; Reached the end of the EOF to check for, so
                   ;; return success.
                   true
                   ;; Didn't reach the EOF, so check the next char and
                   ;; recur if they match. Otherwise fail.
                   (if (= curr-rdr-char (int (first remaining-eof)))
                     (recur (.read reader) (next remaining-eof))
                     false)))]
    (.reset reader)
    result))

(defn- parse-csv-line
  "Takes a CSV-formatted string or char seq (or something that becomes a char
   seq when seq is applied) as input and returns a vector containing two values.
   The first is the first row of the CSV file, parsed into cells (an array of
   strings). The second is the remainder of the CSV file. Correctly deals with
   commas in quoted strings and double-quotes as quote-escape in a quoted
   string."
  [^Reader csv-reader {:keys [strict delimiter quote-char end-of-line]}]
  (loop [fields (transient []) ;; Will return this as the vector of fields.
         current-field (StringBuilder.) ;; Buffer for cell we are working on.
         quoting? false ;; Are we inside a quoted cell at this point?
         current-char (.read csv-reader)]
    (letfn [(unquoted-comma? [^long chr]
              (and (= (int delimiter) chr)
                   (not quoting?)))
            ;; field-with-remainder makes the vector of return values.
            (field-with-remainder [eof?] ;; eof? = true if current-char = eof.
              (if (and eof? quoting? strict)
                (throw (Exception.
                        "Reached end of input before end of quoted field."))
                (persistent! (conj! fields (.toString current-field)))))]
      ;; If our current-char is nil, then we've reached the end of the seq
      ;; and can return fields.
      (cond (= -1 current-char) (field-with-remainder true)
            ;; If we are on a newline while not quoting, then we can end this
            ;; line and return.
            ;; Two cases for the different number of characters to skip.
            (and (not quoting?) (not end-of-line)
                 (lf? (int current-char)))
            (field-with-remainder false)
            (and (not quoting?) (not end-of-line)
                 (crlf? (int current-char) (reader-peek csv-reader)))
            (do (.skip csv-reader 1)
                (field-with-remainder false))
            ;; Check for the custom EOF. If found, skip one less than length
            ;; of eol (since we already read the first char of it).
            (and (not quoting?) end-of-line
                 (custom-eof? (int current-char) csv-reader end-of-line))
            (do (.skip csv-reader (dec (count end-of-line)))
                (field-with-remainder false))
            ;; If we see a comma and aren't in a quote, then end the current
            ;; field and add to fields.
            (unquoted-comma? (int current-char))
            (recur (conj! fields (.toString current-field))
                   (StringBuilder.) quoting?
                   (.read csv-reader))
            (= (int quote-char) (int current-char))
            (if (and (not (= 0 (.length current-field)))
                     (not quoting?))
              ;; There's a double-quote present in an unquoted field, which
              ;; we can either signal or ignore completely, depending on
              ;; *strict*. Note that if we are not strict, we take the
              ;; double-quote as a literal character, and don't change
              ;; quoting state.
              (if strict
                (throw (Exception. "Double quote present in unquoted field."))
                (recur fields
                       (.append current-field (char quote-char)) quoting?
                       (.read csv-reader)))
              (if (and (= (int quote-char) (reader-peek csv-reader))
                       quoting?)
                ;; Saw "" so don't change quoting, just go to next character.
                (recur fields
                       (.append current-field (char quote-char)) quoting?
                       (do (.skip csv-reader 1)
                           (.read csv-reader)))
                ;; Didn't see the second quote char, so change quoting state.
                (recur fields
                       current-field (not quoting?)
                       (.read csv-reader))))
            ;; In any other case, just add the character to the current field
            ;; and recur.
            true (recur fields
                        (.append current-field (char current-char))
                        quoting?
                        (.read csv-reader))))))

(defn- parse-csv-with-options
  [csv-reader opts]
  (lazy-seq
   (when (not (= -1 (reader-peek csv-reader)))
     (let [row (parse-csv-line csv-reader opts)]
       (cons row (parse-csv-with-options csv-reader opts))))))

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
  [cell delimiter quote-char]
  (if (needs-quote? cell delimiter quote-char)
    (str quote-char
         (apply str (map #(escape % delimiter quote-char)
                                    cell))
         quote-char)
    cell))

(defn- quote-and-escape-row
  "Given a row (vector of strings), quotes and escapes any cells where that
   is necessary and then joins all the text into a string for that entire row."
  [row delimiter quote-char]
  (string/join delimiter (map #(quote-and-escape % delimiter quote-char) row)))

(defn write-csv
  "Given a sequence of sequences of strings, returns a string of that table
   in CSV format, with all appropriate quoting and escaping.

   Accepts a number of keyword arguments to change the output:
       :delimiter - A character that contains the cell separator for
                    each column in a row.  Default value: \\,
       :end-of-line - A string containing the end-of-line character
                      for writing CSV files.  Default value: \\n
       :quote-char - A character that is used to begin and end a quoted cell.
                     Default value: \\\""
  [table & {:keys [delimiter quote-char end-of-line]
            :or {delimiter \, quote-char \" end-of-line "\n"}}]
  (loop [csv-string (StringBuilder.)
         quoted-table (map #(quote-and-escape-row %
                                                  (str delimiter)
                                                  quote-char)
                           table)]
    (if (empty? quoted-table)
      (.toString csv-string)
      (recur (.append csv-string (str (first quoted-table) end-of-line))
             (rest quoted-table)))))
