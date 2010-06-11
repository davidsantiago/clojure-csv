(def csvfile (slurp "/Users/David/Downloads/cbp02st.txt"))
(dotimes [_ 10] (time (def cbp02 (dorun 50000 (parse-csv csvfile)))))

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


