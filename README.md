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
* More permissive than RFC 4180, although there are some optional strictness
  checks. (Send me any bugs you find, or any correctness checks you think
  should be performed.)

This library aims to be as permissive as possible with respect to deviation
from the standard, as long as the intention is clear. The only correctness
checks made are those that cannot be made after parsing is done. For example,
some people think it should be an error when lines in the CSV have a
different number of fields -- you should check this yourself. However, it is
not possible, after parsing, to tell if the input ended before the closing
quote of a field; if you care, it will be signaled to you.

Recent Updates
--------------

* Updated library to 1.3.0.
* Now has support for Clojure 1.3.
* Some speed improvements to take advantage of Clojure 1.3. Nearly twice as fast
  in my tests.

###Previously...
* Updated library to 1.2.4.  
* Added the char-seq multimethod, which provides a variety of implementations
  for easily creating the char seqs that parse-csv uses on input from various
  similar objects. Big thanks to [Slawek Gwizdowski](https://github.com/i0cus)
  for this contribution.
* Includes a bug fix for a problem where a non-comma delimiter was causing
  incorrect quoting on write.
* Included a bug fix to make the presence of a double-quote in an unquoted field
  parse better in non-strict mode. Specifically, if a CSV field is not quoted 
  but has \" characters, they are read as \" with no further processing. Does 
  not start quoting.
* Reorganized namespaces to fit better with my perception of Clojure standards.
  Specifically, the main namespace is now clojure-csv.core.
* Significantly faster on parsing. There should be additional speed
  improvements possible when Clojure 1.2 is released.
* Support for more error checking with \*strict\* var.
* Numerous bug fixes.

Obtaining
---------
If you are using Cake, you can simply add 

[clojure-csv/clojure-csv "1.3.0"]

to your project.clj and download it from Clojars with 

cake deps

Use
---
The **clojure-csv.core** namespace exposes two functions to the user: 

### parse-csv
Takes a CSV as a char sequence or string, and returns a lazy sequence of 
vectors of strings; each vector corresponds to a row, and each string is 
one field from that row. Be careful to ensure that if you read lazily from
a file or some other resource that it remains open when the sequence is
consumed.

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

### \*strict\*
By default, this is false. This value only affects parsing during parse-csv.
It will raise an exception when either a double-quote is present in an
unquoted field, or when the end of input is reached during a quoted field.

Bugs
----
Please let me know of any problems you are having.

Contributors
------------
 - [Slawek Gwizdowski](https://github.com/i0cus)

License
--------
Eclipse Public License 
