(ns csv.benchmarks.core
  (:use perforate.core
        clojure-csv.core)
  (:require [clojure.java.shell :as sh]
            [clojure.java.io :as io]
            [clojure.string :as string]))

(def data-url "http://www2.census.gov/econ2002/CBP_CSV/cbp02st.zip")
(def data-dir "benchmarks/data")
(def data-file (str data-dir "/cbp02st.txt"))

(defn data-present?
  "Check if the benchmark test data is available in the benchmark/data dir.
   Simply checks for the presence of the directory."
  []
  (.exists (io/file data-dir)))

(defn get-cbp-data
  []
  (let [filename (last (string/split data-url #"/"))]
    (try (sh/sh "wget" data-url)
         (sh/sh "unzip" filename "-p" "benchmarks/data")
         (sh/sh "rm" filename)
         (catch java.io.IOException e))))

(defn get-cbp-data-if-missing
  "Get the data if it isn't already there."
  []
  (when (not (data-present?))
    (get-cbp-data)))


(defgoal read-test "CSV Read Speed"
  :setup get-cbp-data-if-missing)

(defcase* read-test :clojure-csv
  (fn []
    (let [csvfile (slurp data-file)]
      [(fn [] (dorun 50000 (parse-csv csvfile)))])))

(defgoal write-test "CSV Write Speed"
  :setup get-cbp-data-if-missing)

(defcase* write-test :clojure-csv
  (fn []
    (let [csvfile (slurp data-file)
          cbp02 (doall (take 50000 (parse-csv csvfile)))]
      [(fn [] (doall (write-csv cbp02)))])))
