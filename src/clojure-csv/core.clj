(ns
    #^{:author "David Santiago",
       :doc "Clojure-CSV is a small library for reading and writing CSV files.
It correctly handles common CSV edge-cases, such as embedded newlines, commas,
and quotes. The main functions are parse-csv and write-csv."}
  clojure-csv.core
  (:use clojure.contrib.test-is)
  (:require [clojure.contrib.str-utils2 :as s]))

(set! *warn-on-reflection* true)

(def
 #^{:doc
    "A character that contains the cell separator for each column in a row.
     Default value: \\,"}
     *delimiter* \,)

(def
 #^{:doc
    "A string containing the end-of-line character for writing CSV files.
     This setting is ignored for reading (\n and \r\n are both accepted).
     Default value: \"\\n\""}
     *end-of-line* "\n")

;;
;; CSV Input
;;

(defn- parse-csv-line
  "Takes a CSV-formatted string or char seq (or something that becomes a char
   seq when seq is applied) as input and returns a vector containing two values.
   The first is the first row of the CSV file, parsed into cells (an array of
   strings). The second is the remainder of the CSV file. Correctly deals with
   commas in quoted strings and double-quotes as quote-escape in a quoted
   string."
  [csv-line]
  (let [csv-chars (seq csv-line)]
    (loop [fields []          ;; Going to return this as the vector of fields.
	   current-field []   ;; Buffer for the cell we are working on.
	   quoting? false     ;; Are we inside a quoted cell at this point?
	   current-char (first csv-chars)
	   remaining-chars (rest csv-chars)]
      (let [unquoted-comma? (fn [char] (and (= *delimiter* char)
					    (not quoting?)))
	    ;; Tests for LF and CR-LF.
	    lf? (fn [current-char] (= \newline current-char))
	    crlf? (fn [current-char remaining-chars]
		    (and (= \return current-char)
			 (= \newline (first remaining-chars))))
	    ;; field-with-remainder makes the vector of return values.
	    field-with-remainder (fn [remaining-chars]
				   (vector (conj fields
						 (apply str current-field))
					   remaining-chars))]
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
	    (recur (conj fields (apply str current-field))
		   [] quoting? (first remaining-chars) (rest remaining-chars))
	    (= \" current-char)
	    (if (and (= \" (first remaining-chars))
		     quoting?)
	      ;; Saw "" so don't change quoting, just go to next character.
	      (recur fields
		     (conj current-field \") quoting?
		     (first (rest remaining-chars))
		     (rest (rest remaining-chars)))
	      ;; Didn't see the second ", so change quoting state.
	      (recur fields
		     current-field (not quoting?)
		     (first remaining-chars)
		     (rest remaining-chars)))
	    ;; In any other case, just add the character to the current field
	    ;; and recur.
	    true (recur fields
			(conj current-field current-char)
			quoting?
			(first remaining-chars)
			(rest remaining-chars)))))))

(defn parse-csv
  "Takes a CSV as a string or char seq and returns a seq of the parsed CSV rows,
   in the form of a lazy sequence of vectors: a vector per row, a string for
   each cell."
  [csv]
  (lazy-seq
   (when (not (nil? csv))
     (let [[row remainder] (parse-csv-line csv)]
       (cons row (parse-csv remainder))))))

;;
;; CSV Output
;;

(defn- needs-quote?
  "Given a string (cell), determine whether it contains a character that
   requires this cell to be quoted."
  [cell]
  (or (s/contains? cell ",")
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
    (loop [csv-string []
	   quoted-table (map quote-and-escape-row table)]
      (if (empty? quoted-table)
	(apply str csv-string)
	(recur (conj csv-string (str (first quoted-table) *end-of-line*))
	       (rest quoted-table)))))

;;
;; Tests
;;

(deftest basic-functionality
  (is (= [["a" "b" "c"]] (parse-csv "a,b,c")))
  (is (= [["" ""]] (parse-csv ",")))
  (is (= [[""]] (parse-csv ""))))

(deftest quoting
  (is (= [["Before", "\"","After"]] (parse-csv "Before,\"\"\"\",After")))
  (is (= [["Before", "", "After"]] (parse-csv "Before,\"\",After")))
  (is (= [["", "start&end", ""]] (parse-csv "\"\",\"start&end\",\"\"")))
  (is (= [[",", "\"", ",,", ",,,"]]
	 (parse-csv "\",\",\"\"\"\",\",,\",\",,,\"")))
  (is (= [["quoted", "\",\"", "comma"]]
	 (parse-csv "quoted,\"\"\",\"\"\",comma"))))

(deftest newlines
  (is (= [["test1","test2"] ["test3","test4"]]
	 (parse-csv "test1,test2\ntest3,test4")))
  (is (= [["test1","test2"] ["test3","test4"]]
	 (parse-csv "test1,test2\r\ntest3,test4")))
  (is (= [["embedded","line\nbreak"]] (parse-csv "embedded,\"line\nbreak\"")))
  (is (= [["embedded", "line\r\nbreak"]]
	 (parse-csv "embedded,\"line\r\nbreak\""))))

(deftest writing
  (is (= "test1,test2\n" (write-csv [["test1" "test2"]])))
  (is (= "test1,test2\ntest3,test4\n"
	 (write-csv [["test1" "test2"] ["test3" "test4"]])))
  (is (= "quoted:,\"carriage\nreturn\"\n"
	 (write-csv [["quoted:" "carriage\nreturn"]])))
  (is (= "quoted:,\"embedded,comma\"\n"
	 (write-csv [["quoted:" "embedded,comma"]])))
  (is (= "quoted:,\"escaped\"\"quotes\"\"\"\n"
	 (write-csv [["quoted:" "escaped\"quotes\""]]))))

(deftest nonstring-inputs
  (is (= [["First", "Second"]] (parse-csv (seq "First,Second"))))
  (is (= [["First", "Second"]] (parse-csv (.toCharArray "First,Second")))))
