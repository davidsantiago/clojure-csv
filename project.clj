(defproject opploans/clojure-csv "2.0.3"
  :description "A simple library to read and write CSV files."
  :dependencies [[org.clojure/clojure "1.3.0"]]
  :plugins [[perforate "0.3.2"]]
  :jvm-opts ["-Xmx1g"]
  :profiles {:current {:source-paths ["src/"]}
             :clj1.4 {:dependencies [[org.clojure/clojure "1.4.0-beta5"]]}
             :clj1.3 {:dependencies [[org.clojure/clojure "1.3.0"]]}
             :csv1.3 {:dependencies [[clojure-csv "1.3.0"]]}
             :csv2.0 {:dependencies [[clojure-csv "2.0.0-alpha1"]]}}
  :perforate {:environments [{:name :clojure-csv2
                              :profiles [:clj1.3 :csv2.0]
                              :namespaces [csv.benchmarks.core]}
                             {:name :clojure-csv1
                              :profiles [:clj1.3 :csv1.3]
                              :namespaces [csv.benchmarks.core]}
                             {:name :current
                              :profiles [:clj1.4 :current]
                              :namespaces [csv.benchmarks.core]}]})
