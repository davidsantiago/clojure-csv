(ns test-csv
  (:import [java.io StringReader])
  (:use clojure.test
        clojure-csv.core))

(defn- i-arr [^String s] (int-array (map int s)))

(defn- b-arr [^String s] (byte-array (map (comp byte int) s)))

(defn- c-arr [^String s] (char-array s))

(deftest basic-functionality
  (is (= [["a" "b" "c"]] (parse-csv "a,b,c")))
  (is (= [["" ""]] (parse-csv ",")))
  (is (= [[""]] (parse-csv ""))))

(deftest alternate-sources
  (is (= [["a" "b" "c"]] (parse-csv (char-seq (i-arr "a,b,c")))))
  (is (= [["" ""]] (parse-csv (char-seq (i-arr ",")))))
  (is (= [[""]] (parse-csv (char-seq (i-arr "")))))
  (is (= [["a" "b" "c"]] (parse-csv (char-seq (b-arr "a,b,c")))))
  (is (= [["" ""]] (parse-csv (char-seq (b-arr ",")))))
  (is (= [[""]] (parse-csv (char-seq (b-arr "")))))
  (is (= [["a" "b" "c"]] (parse-csv (char-seq (c-arr "a,b,c")))))
  (is (= [["" ""]] (parse-csv (char-seq (c-arr ",")))))
  (is (= [[""]] (parse-csv (char-seq (c-arr "")))))
  (is (= [["a" "b" "c"]] (parse-csv (char-seq (StringReader. "a,b,c")))))
  (is (= [["" ""]] (parse-csv (char-seq (StringReader. ",")))))
  (is (= [[""]] (parse-csv (char-seq (StringReader. ""))))))

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
  (is (= "quoted:,\"line\nfeed\"\n"
         (write-csv [["quoted:" "line\nfeed"]])))
  (is (= "quoted:,\"carriage\rreturn\"\n"
         (write-csv [["quoted:" "carriage\rreturn"]])))
  (is (= "quoted:,\"embedded,comma\"\n"
         (write-csv [["quoted:" "embedded,comma"]])))
  (is (= "quoted:,\"escaped\"\"quotes\"\"\"\n"
         (write-csv [["quoted:" "escaped\"quotes\""]]))))

(deftest nonstring-inputs
  (is (= [["First", "Second"]] (parse-csv (seq "First,Second"))))
  (is (= [["First", "Second"]] (parse-csv (.toCharArray "First,Second")))))

(deftest alternate-delimiters
  (is (= [["First", "Second"]]
           (binding [*delimiter* \tab] (parse-csv "First\tSecond"))))
  (is (= "First\tSecond\n"
           (binding [*delimiter* \tab] (write-csv [["First", "Second"]]))))
  (is (= "First\tSecond,Third\n"
         (binding [*delimiter* \tab] (write-csv [["First", "Second,Third"]]))))
  (is (= "First\t\"Second\tThird\"\n"
         (binding [*delimiter* \tab] (write-csv [["First", "Second\tThird"]])))))

(deftest strictness
  (is (thrown? Exception (binding [*strict* true] (dorun (parse-csv "a,b,c,\"d")))))
  (is (thrown? Exception (binding [*strict* true] (dorun (parse-csv "a,b,c,d\"e")))))
  (is (= [["a","b","c","d"]]
         (binding [*strict* false] (parse-csv "a,b,c,\"d"))))
  (is (= [["a","b","c","d"]]
         (binding [*strict* true] (parse-csv "a,b,c,\"d\""))))
  (is (= [["a","b","c","d\""]]
           (binding [*strict* false] (parse-csv "a,b,c,d\""))))
  (is (= [["120030" "BLACK COD FILET MET VEL \"MSC\"" "KG" "0" "1"]]
       (binding [*strict* false *delimiter* \;]
         (parse-csv "120030;BLACK COD FILET MET VEL \"MSC\";KG;0;1")))))

(deftest reader-cases
  ;; reader will be created and closed in with-open, but used outside.
  ;; this is actually a java.io.IOException, but thrown at runtime so...
  (is (thrown? java.lang.RuntimeException
               (dorun (with-open [sr (StringReader. "a,b,c")]
                        (parse-csv (char-seq sr)))))))

