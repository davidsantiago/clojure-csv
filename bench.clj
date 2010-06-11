(def csvfile (slurp "/Users/David/Downloads/cbp02st.txt"))
(dotimes [_ 10] (time (dorun 50000 (parse-csv csvfile))))

;;; Results from clojure-csv 1.0.0
"Elapsed time: 4600.112 msecs"
"Elapsed time: 4863.334 msecs"
"Elapsed time: 4611.27 msecs"
"Elapsed time: 4541.954 msecs"
"Elapsed time: 4360.738 msecs"
"Elapsed time: 4279.428 msecs"
"Elapsed time: 4283.482 msecs"
"Elapsed time: 3936.211 msecs"
"Elapsed time: 3838.169 msecs"
"Elapsed time: 3796.73 msecs"

(def cbp02 (doall (take 5000 (parse-csv csvfile))))
(dotimes [_ 10] (time (doall (write-csv cbp02))))

;;; Results from clojure-csv 1.0.0
"Elapsed time: 499.091 msecs"
"Elapsed time: 389.241 msecs"
"Elapsed time: 349.681 msecs"
"Elapsed time: 268.284 msecs"
"Elapsed time: 274.996 msecs"
"Elapsed time: 308.593 msecs"
"Elapsed time: 267.072 msecs"
"Elapsed time: 267.436 msecs"
"Elapsed time: 267.495 msecs"
"Elapsed time: 266.011 msecs"
