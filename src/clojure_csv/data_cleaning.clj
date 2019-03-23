(ns
    ^{:author "Simon Brooke",
      :doc "Recognise numbers as numbers, and (#TODO)
      dates/times as dates times, etc"}
  clojure-csv.data-cleaning
  (:require [clj-time.core :as t]
            [clj-time.format :as f]))

(defn number-as-number
  "if `o` is the string representation of a number, return that number; else
  return `o`."
  [o]
  (if
    (string? o)
    (try
      (let [n (read-string o)]
        (if (number? n) n o))
      (catch Exception e o))
    o))

(defmacro numbers-as-numbers
  "Return a list like the sequence `l`, but with all those elements
  which are string representations of numbers replaced with numbers."
  [l]
  `(map number-as-number ~l))

(defn date-as-date
  "if `o` is the string representation of a date or timestamp comforming to
  `date-format`, return that timestamp; else return `o`. `date-format` is
  expected to be either
  1. A string in the format understood by `clj-time.formatters/formatter`, or
  2. A keyword representing one of `clj-time.formatters` built-in formatters,
  3. A custom formatter as constructed by `clj-time.formatters/formatter`"
  [o date-format]
  (if
    (string? o)
    (try
      (let [f (cond
               (string? date-format) (f/formatter date-format)
               (keyword? date-format) (f/formatters date-format)
               (=
                (type date-format)
                org.joda.time.format.DateTimeFormatter) date-format)]
        (f/parse f o))
      (catch Exception e
        o))
    o))

(defmacro dates-as-dates
  "Return a list like the sequence `l`, but with all those elements
  which are string representations of numbers replaced with numbers."
  [l date-format]
  `(map #(date-as-date % ~date-format) ~l))

