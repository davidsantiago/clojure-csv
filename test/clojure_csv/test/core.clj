(ns clojure-csv.test.core
  (:import [java.io StringReader])
  (:use clojure.test
        clojure.java.io
        clojure-csv.core))

(deftest basic-functionality
  (is (= [["a" "b" "c"]] (parse-csv "a,b,c")))
  (is (= [["" ""]] (parse-csv ",")))
  (is (= [["a" "b"]] (parse-csv "a,b\r\n"))) ;; Linebreak on eof won't add line.
  (is (= [] (parse-csv ""))))

(deftest alternate-sources
  (is (= [["a" "b" "c"]] (parse-csv (StringReader. "a,b,c"))))
  (is (= [["" ""]] (parse-csv (StringReader. ","))))
  (is (= [] (parse-csv (StringReader. ""))))
  (is (= [["First", "Second"]] (parse-csv
                                (reader (.toCharArray "First,Second"))))))

(deftest quoting
  (is (= [[""]] (parse-csv "\"")))
  (is (= [["\""]] (parse-csv "\"\"\"")))
  (is (= [["Before", "\"","After"]] (parse-csv "Before,\"\"\"\",After")))
  (is (= [["Before", "", "After"]] (parse-csv "Before,\"\",After")))
  (is (= [["", "start&end", ""]] (parse-csv "\"\",\"start&end\",\"\"")))
  (is (= [[",", "\"", ",,", ",,,"]]
         (parse-csv "\",\",\"\"\"\",\",,\",\",,,\"")))
  (is (= [["quoted", "\",\"", "comma"]]
         (parse-csv "quoted,\"\"\",\"\"\",comma")))
  (is (= [["Hello"]] (parse-csv "\"Hello\"")))
  (is (thrown? Exception (dorun (parse-csv "\"Hello\" \"Hello2\""))))
  (is (thrown? Exception (dorun (parse-csv "\"Hello\" \"Hello2\" \"Hello3\""))))
  (is (thrown? Exception (dorun (parse-csv "\"Hello\",\"Hello2\" \"Hello3\""))))
  (is (= [["Hello\"Hello2"]] (parse-csv "\"Hello\"\"Hello2\"")))
  (is (thrown? Exception (dorun (parse-csv "\"Hello\"Hello2"))))
  (is (= [["Hello"]] (parse-csv "\"Hello"))))

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

(deftest force-quote-on-output
  (is (= "test1,test2\n" (write-csv [["test1" "test2"]])))
  (is (= "test1,test2\n" (write-csv [["test1" "test2"]] :force-quote false)))
  (is (= "\"test1\",\"test2\"\n" (write-csv [["test1" "test2"]]
                                            :force-quote true)))
  (is (= "stillquoted:,\"needs,quote\"\n"
         (write-csv [["stillquoted:" "needs,quote"]]
                    :force-quote false)))
  (is (= "\"allquoted:\",\"needs,quote\"\n"
         (write-csv [["allquoted:" "needs,quote"]]
                    :force-quote true))))

(deftest alternate-delimiters
  (is (= [["First", "Second"]]
           (parse-csv "First\tSecond" :delimiter \tab)))
  (is (= "First\tSecond\n"
         (write-csv [["First", "Second"]] :delimiter \tab)))
  (is (= "First\tSecond,Third\n"
         (write-csv [["First", "Second,Third"]] :delimiter \tab)))
  (is (= "First\t\"Second\tThird\"\n"
         (write-csv [["First", "Second\tThird"]] :delimiter \tab))))

(deftest alternate-quote-char
  (is (= [["a", "b", "c"]]
           (parse-csv "a,|b|,c" :quote-char \|)))
  (is (= [["a", "b|c", "d"]]
           (parse-csv "a,|b||c|,d" :quote-char \|)))
  (is (= [["a", "b\"\nc", "d"]]
           (parse-csv "a,|b\"\nc|,d" :quote-char \|)))
  (is (= "a,|b||c|,d\n"
         (write-csv [["a", "b|c", "d"]] :quote-char \|)))
  (is (= "a,|b\nc|,d\n"
         (write-csv [["a", "b\nc", "d"]] :quote-char \|)))
  (is (= "a,b\"c,d\n"
         (write-csv [["a", "b\"c", "d"]] :quote-char \|))))

(deftest strictness
  (is (thrown? Exception (dorun (parse-csv "a,b,c,\"d" :strict true))))
  (is (thrown? Exception (dorun (parse-csv "a,b,c,d\"e" :strict true))))
  (is (= [["a","b","c","d"]]
           (parse-csv "a,b,c,\"d" :strict false)))
  (is (= [["a","b","c","d"]]
           (parse-csv "a,b,c,\"d\"" :strict true)))
  (is (= [["a","b","c","d\""]]
           (parse-csv "a,b,c,d\"" :strict false)))
  (is (= [["120030" "BLACK COD FILET MET VEL \"MSC\"" "KG" "0" "1"]]
           (parse-csv "120030;BLACK COD FILET MET VEL \"MSC\";KG;0;1"
                      :strict false :delimiter \;))))

(deftest reader-cases
  ;; reader will be created and closed in with-open, but used outside.
  ;; this is actually a java.io.IOException, but thrown at runtime so...
  (is (thrown? java.lang.RuntimeException
               (dorun (with-open [sr (StringReader. "a,b,c")]
                        (parse-csv sr))))))

(deftest custom-eol
    ;; Test the use of this option.
  (is (= [["a" "b"] ["c" "d"]] (parse-csv "a,b\rc,d" :end-of-line "\r")))
  (is (= [["a" "b"] ["c" "d"]] (parse-csv "a,babcc,d" :end-of-line "abc")))
  ;; The presence of an end-of-line option turns off the parsing of \n and \r\n
  ;; as EOLs, so they can appear unquoted in fields when they do not interfere
  ;; with the EOL.
  (is (= [["a" "b\n"] ["c" "d"]] (parse-csv "a,b\n\rc,d" :end-of-line "\r")))
  (is (= [["a" "b"] ["\nc" "d"]] (parse-csv "a,b\r\nc,d" :end-of-line "\r")))
  ;; Custom EOL can still be quoted into a field.
  (is (= [["a" "b\r"] ["c" "d"]] (parse-csv "a,\"b\r\"\rc,d"
                                            :end-of-line "\r")))
  (is (= [["a" "bHELLO"] ["c" "d"]] (parse-csv "a,\"bHELLO\"HELLOc,d"
                                            :end-of-line "HELLO")))
  (is (= [["a" "b\r"] ["c" "d"]] (parse-csv "a,|b\r|\rc,d"
                                            :end-of-line "\r" :quote-char \|))))

(deftest data-cleansing
  (let [data "Name;MP;Area;County;Electorate;CON;LAB;LIB;UKIP;Green;NAT;MIN;OTH
        Aldershot;Leo Docherty;12;Hampshire;76205;26955;15477;3637;1796;1090;0;0;0
        Aldridge-Brownhills;Wendy Morton;7;Black Country;60363;26317;12010;1343;0;0;0;0;565
        Altrincham and Sale West;Graham Brady;4;Central Manchester;73220;26933;20507;4051;0;1000;0;0;299
        Amber Valley;Nigel Mills;8;Derbyshire;68065;25905;17605;1100;0;650;0;0;551
        Arundel and South Downs;Nick Herbert;12;West Sussex;80766;37573;13690;4783;1668;2542;0;0;0
        Ashfield;Gloria De Piero;8;Nottinghamshire;78099;20844;21285;969;1885;398;0;4612;0
        Ashford;Damian Green;12;Kent;87396;35318;17840;3101;2218;1402;0;0;0"]
    (testing "number recognition"
      (let [expected "76205"
            actual (nth (nth (parse-csv data :delimiter \;) 1) 4)]
        (is (= actual expected) "Number recognition off"))
      (let [expected 76205
            actual (nth (nth (parse-csv data :delimiter \; :numbers true) 1) 4)]
        (is (= actual expected) "Number recognition on")))
    (testing "field names"
      (let [expected 76205
            actual (:Electorate (first (parse-csv data
                                                  :delimiter \;
                                                  :numbers true
                                                  :field-names true)))]
        (is (= actual expected) "Field names from first row"))
      (let [expected 76205
            actual (:e (nth (parse-csv data
                                         :delimiter \;
                                         :numbers true
                                         :field-names [:a :b :c :d :e]) 1))]
        (is (= actual expected) "Field names passed as vector"))
      (let [expected 60363
            actual (:e (nth (parse-csv data
                                         :delimiter \;
                                         :numbers true
                                         :field-names '(:a :b :c :d :e)) 2))]
        (is (= actual expected) "Field names passed as list")))))


