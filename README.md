Clojure-CSV
===========
Clojure-CSV is a small library for reading and writing CSV files. The main 
features: 

* Both common line terminators are accepted. 
* Quoting and escaping inside CSV fields are handled correctly (specifically 
  commas and double-quote characters). 
* Unescaped newlines embedded in CSV fields are supported when 
  parsing. 
* Reading is lazy. 

Obtaining
---------
If you are using Leiningen, you can simply add 

[clojure-csv/clojure-csv "1.0.0"]

to your project.clj and download it from Clojars with

lein deps

Use
---
There are two functions exposed to the user: 

### parse-csv
Takes a CSV as a char sequence or string, and returns a lazy sequence of 
vectors of strings; each vector corresponds to a row, and each string is 
one field from that row. 

### write-csv
Takes a sequence of sequences of strings, basically a table of strings, 
and renders that table into a string in CSV format. You can easily
call this function repeatedly row-by-row and concatenate the results yourself. 

Configuration options
---------------------
You can modify some of the behavior of the parser by re-binding some vars. 

### \*delimiter\* 
By default, this is a comma. You can change this to another character, such as
a tab, to read tab-delimited files. 

### \*end-of-line\*
By default, this is "\n". This value only affects the output from write-csv;
parse-csv will always accept both \n and \r\n line-endings. 

License
--------
Eclipse Public License 
