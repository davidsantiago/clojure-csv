(ns clojure-csv.test.data-cleaning
  (:require [clojure.test :refer :all]
            [clojure-csv.data-cleaning :refer :all]
            [clj-time.core :as t]
            [clj-time.format :as f]))


(deftest number-recognition
  (testing "Recognition of integers"
    (let [expected 123456
          actual (number-as-number "123456")]
      (is (= actual expected) "integer 123456"))
    (let [expected -1
          actual (number-as-number "-1")]
      (is (= actual expected) "integer negative one")))
  (testing "Recognition of floats"
    (let [expected 0.1
          actual (number-as-number "0.1")]
      (is (= actual expected) "float zero point one"))
    (let [expected -0.1
          actual (number-as-number "-0.1")]
      (is (= actual expected) "float negative zero point one"))
    (let [expected 3.142857
          actual (number-as-number "3.142857")]
      (is (= actual expected) "float approximation of π")))
  (testing "Recognition of rationals"
    (let [expected 22/7
          actual (number-as-number "22/7")]
      (is (= actual expected) "rational approximation of π"))
    (let [expected 1/4
          actual (number-as-number "2/8")]
      (is (= actual expected) "two eighths -> one quarter")))
  (testing "Recognition of numbers"
    (let [expected '("Fred" "2019-03-23" 22/7 3.142857 123456 -8)
          actual (numbers-as-numbers
                  '("Fred" "2019-03-23" "22/7" "3.142857" "123456" "-8"))]
      (is (= actual expected) "List including numbers in various formats"))))

(deftest date-recognition
  (testing "recognition of dates; format is string"
    (let [expected "class org.joda.time.DateTime"
          actual (str (type (date-as-date "2019-03-23" "yyyy-MM-dd")))]
      (is (= actual expected) "format is string; match expected"))
    (let [expected "class java.lang.String"
          actual (str (type (date-as-date "2019/03/23" "yyyy-MM-dd")))]
      (is (= actual expected) "format is string; match not expected")))
  (testing "recognition of dates; format is keyword"
    (let [expected "class org.joda.time.DateTime"
          actual (str (type (date-as-date "2019-03-23" :date)))]
      (is (= actual expected) "format is keyword; match expected"))
    (let [expected "class java.lang.String"
          actual (str (type (date-as-date "2019/03/23" :date)))]
      (is (= actual expected) "format is keyword; match not expected")))
  (testing "recognition of dates; format is formatter"
    (let [expected "class org.joda.time.DateTime"
          actual (str (type (date-as-date "2019-03-23" (f/formatter "2019-03-23" ))))]
      (is (= actual expected) "format is formatter; match expected"))
    (let [expected "class java.lang.String"
          actual (str (type (date-as-date "2019/03/23" (f/formatter "2019-03-23" ))))]
      (is (= actual expected) "format is formatter; match not expected"))
    (let [expected "class org.joda.time.DateTime"
          actual (str
                  (type
                  (date-as-date
                   "2019/03/23"
                   (f/formatter
                    (t/default-time-zone)
                    "YYYY-MM-dd"
                    "YYYY/MM/dd"))))]
      (is (= actual expected) "format is composite formatter; match expected"))))
