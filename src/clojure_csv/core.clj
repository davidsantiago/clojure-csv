(ns
    ^{:author "David Santiago",
      :doc "Clojure-CSV is a small library for reading and writing CSV files.
It correctly handles common CSV edge-cases, such as embedded newlines, commas,
and quotes. The main functions are parse-csv and write-csv."}
  clojure-csv.core
  (:require [clojure.contrib.str-utils2 :as s]))

;(set! *warn-on-reflection* true)

(def
 ^{:doc
   "A character that contains the cell separator for each column in a row.
    Default value: \\,"}
    *delimiter* \,)

(def
 ^{:doc
   "A string containing the end-of-line character for writing CSV files.
    This setting is ignored for reading (\n and \r\n are both accepted).
    Default value: \"\\n\""}
    *end-of-line* "\n")

(def
 ^{:doc
   "If this variable is true, the parser will throw an exception on invalid
    input.
    Default value: false"}
    *strict* false)

;;
;; Adapt to char-seq
;;
(defmulti char-seq
  "Adapt object to character seq, based on class. Pass-through for ISeq."
  class)

(defmethod char-seq java.lang.String [a-str] (seq a-str))

(defmethod char-seq (Class/forName "[I") [arr] (map char arr))

(defmethod char-seq (Class/forName "[B") [arr] (map char arr))

(defmethod char-seq (Class/forName "[C") [arr] arr)

(defmethod char-seq java.io.Reader [a-reader]
  (letfn [(read-one []
            (try
              (let [^int c (.read a-reader)]
                (when (not= -1 c)
                  (cons (char c) (lazy-cat (read-one)))))
              (catch java.io.EOFException eof nil)))]
    (lazy-seq (read-one))))

(defmethod char-seq clojure.lang.ISeq [se] se)

(defmethod char-seq :default [_]
  (throw (java.io.IOException. "Don't know how to proceed.")))


;;
;; CSV Input
;;

;; Tests for LF and CR-LF.
(defn- lf?
  [current-char]
  (= \newline current-char))

(defn- crlf?
  [current-char remaining-chars]
  (and (= \return current-char)
       (= \newline (first remaining-chars))))

(defn- parse-csv-line
  "Takes a CSV-formatted string or char seq (or something that becomes a char
   seq when seq is applied) as input and returns a vector containing two values.
   The first is the first row of the CSV file, parsed into cells (an array of
   strings). The second is the remainder of the CSV file. Correctly deals with
   commas in quoted strings and double-quotes as quote-escape in a quoted
   string."
  [csv-line]
  (let [csv-chars (seq csv-line)]
    (loop [fields (transient []) ;; Will return this as the vector of fields.
           current-field (StringBuffer.) ;; Buffer for cell we are working on.
           quoting? false     ;; Are we inside a quoted cell at this point?
           current-char (first csv-chars)
           remaining-chars (rest csv-chars)]
      (letfn [(unquoted-comma? [chr]
                               (and (= *delimiter* chr)
                                    (not quoting?)))
              ;; field-with-remainder makes the vector of return values.
              (field-with-remainder [remaining-chars]
                (if (and (nil? remaining-chars) quoting? *strict*)
                  (throw (Exception.
                          "Reached end of input before end of quoted field."))
                  (vector (persistent! (conj! fields (.toString current-field)))
                          remaining-chars)))]
      ;; If our current-char is nil, then we've reached the end of the seq
      ;; and can return fields.
      (cond (nil? current-char) (field-with-remainder nil)
            ;; If we are on a newline while not quoting, then we can end this
            ;; line and return.
            ;; Two cases for the different number of characters to skip.
            (and (not quoting?)
                 (lf? current-char))
            (field-with-remainder remaining-chars)
            (and (not quoting?)
                 (crlf? current-char remaining-chars))
            (field-with-remainder (rest remaining-chars))
            ;; If we see a comma and aren't in a quote, then end the current
            ;; field and add to fields.
            (unquoted-comma? current-char)
            (recur (conj! fields (.toString current-field))
                   (StringBuffer.) quoting?
                   (first remaining-chars) (rest remaining-chars))
            (= \" current-char)
            (if (and (not (= 0 (.length current-field)))
                     (not quoting?))
              ;; There's a double-quote present in an unquoted field, which we
              ;; can either signal or ignore completely, depending on *strict*.
              ;; Note that if we are not strict, we take the double-quote as a
              ;; literal character, and don't change quoting state.
              (if *strict*
                (throw (Exception. "Double quote present in unquoted field."))
                (recur fields
                       (.append current-field \") quoting?
                       (first remaining-chars)
                       (rest remaining-chars)))
              (if (and (= \" (first remaining-chars))
                       quoting?)
                ;; Saw "" so don't change quoting, just go to next character.
                (recur fields
                       (.append current-field \") quoting?
                       (first (rest remaining-chars))
                       (rest (rest remaining-chars)))
                ;; Didn't see the second ", so change quoting state.
                (recur fields
                       current-field (not quoting?)
                       (first remaining-chars)
                       (rest remaining-chars))))
            ;; In any other case, just add the character to the current field
            ;; and recur.
            true (recur fields
                        (.append current-field current-char)
                        quoting?
                        (first remaining-chars)
                        (rest remaining-chars)))))))

(defn- parse-csv-with-bindings
  "Because we do parsing lazily, we have to make special provisions for the
   bindings in place at call-time to still be in place when things are
   actually evaluated. This function makes that transparent to callers."
  [csv bind-map]
  (lazy-seq
   (with-bindings bind-map
     (when (not (nil? csv))
       (let [[row remainder] (parse-csv-line csv)]
         (cons row (parse-csv-with-bindings remainder bind-map)))))))

(defn parse-csv
  "Takes a CSV as a string or char seq and returns a seq of the parsed CSV rows,
   in the form of a lazy sequence of vectors: a vector per row, a string for
   each cell."
  ([csv]
     (parse-csv-with-bindings csv {#'*strict* *strict*
                                   #'*delimiter* *delimiter*
                                   #'*end-of-line* *end-of-line*})))

;;
;; CSV Output
;;

(defn- needs-quote?
  "Given a string (cell), determine whether it contains a character that
   requires this cell to be quoted."
  [^String cell]
  (or (s/contains? cell (str *delimiter*))
      (s/contains? cell "\"")
      (s/contains? cell "\n")
      (s/contains? cell "\r\n")))

(defn- escape
  "Given a character, returns the escaped version, whether that is the same
   as the original character or a replacement. The return is a string or a
   character, but it all gets passed into str anyways."
  [chr]
  (if (= \" chr) "\"\"" chr))

(defn- quote-and-escape
  "Given a string (cell), returns a new string that has any necessary quoting
   and escaping."
  [cell]
  (if (needs-quote? cell)
    (str "\"" (apply str (map escape cell)) "\"")
    cell))

(defn- quote-and-escape-row
  "Given a row (vector of strings), quotes and escapes any cells where that
   is necessary and then joins all the text into a string for that entire row."
  [row]
  (s/join *delimiter* (map quote-and-escape row)))

(defn write-csv
  "Given a sequence of sequences of strings, returns a string of that table
   in CSV format, with all appropriate quoting and escaping."
  [table]
    (loop [csv-string (StringBuffer.)
           quoted-table (map quote-and-escape-row table)]
      (if (empty? quoted-table)
        (.toString csv-string)
        (recur (.append csv-string (str (first quoted-table) *end-of-line*))
               (rest quoted-table)))))
