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
checks made are those on the actual (minimal) CSV structure. For example,
some people think it should be an error when lines in the CSV have a
different number of fields -- you should check this yourself. However, it is
not possible, after parsing, to tell if the input ended before the closing
quote of a field; if you care, it can be signaled to you.

The API has changed in the 2.0 series; see below for details.

Recent Updates
--------------

* Updated library to 2.0.2, with a bug fix for malformed input by
  [attil-io](https://github.com/attil-io).
* Updated library to 2.0.1, which adds the :force-quote option to write-csv.
  Big thanks to [Barrie McGuire](https://github.com/pleasle) for the contribution.
* Updated library to 2.0.0; essentially identical to 2.0.0-alpha2.

* Updated library to 2.0.0-alpha2..
* Rewritten parser for additional speed increases.
* Benchmarks to help monitor and improve performance.

* Updated the library to 2.0.0-alpha1.
* Major update: Massive speed improvements, end-of-line string is
  configurable for parsing, improved handling of empty files, input to
  parse-csv is now a string or Reader, and a new API based on keyword
  args instead of rebinding vars.

###Previously...
* Updated library to 1.3.2.
* Added support for changing the character used to start and end quoted fields in
  reading and writing.
* Updated library to 1.3.1.
* Fixed the quoting behavior on write, to properly quote any field with a CR. Thanks to Matt Lehman for this fix.
* Updated library to 1.3.0.
* Now has support for Clojure 1.3.
* Some speed improvements to take advantage of Clojure 1.3. Nearly twice as fast
  in my tests.
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
If you are using Leiningen, you can simply add

    [clojure-csv/clojure-csv "2.0.1"]

to your project.clj and download it from Clojars with

    lein deps

Use
---
The `clojure-csv.core` namespace exposes two functions to the user:

### parse-csv
Takes a CSV as a char sequence or string, and returns a lazy sequence of
vectors of strings; each vector corresponds to a row, and each string is
one field from that row. Be careful to ensure that if you read lazily from
a file or some other resource that it remains open when the sequence is
consumed.

Takes the following keyword arguments to change parsing behavior:
#### :delimiter
A character that contains the cell separator for each column in a row.
##### Default value: \\,
#### :end-of-line
A string containing the end-of-line character for
reading CSV files. If this setting is nil then \\n and \\r\\n are both
accepted.  
##### Default value: nil
#### :quote-char
A character that is used to begin and end a quoted cell.
##### Default value: \"
#### :strict
If this variable is true, the parser will throw an exception
on parse errors that are recoverable but not to spec or otherwise
nonsensical.  
##### Default value: false

### write-csv
Takes a sequence of sequences of strings, basically a table of strings,
and renders that table into a string in CSV format. You can easily
call this function repeatedly row-by-row and concatenate the results yourself.

Takes the following keyword arguments to change the written file:
#### :delimiter
A character that contains the cell separator for each column in a row.  
##### Default value: \\,
#### :end-of-line
A string containing the end-of-line character for writing CSV files.  
##### Default value: \\n
#### :quote-char
A character that is used to begin and end a quoted cell.
##### Default value: \"
#### :force-quote
If this variable is true, the output will have ever field quoted, whether
this is needed or not. This can apparently be helpful for interoperating
with Excel.
##### Default value: false

Changes from API 1.0
--------------------

Clojure-CSV was originally written for Clojure 1.0, before many of the
modern features we now enjoy in Clojure, like keyword args, an IO
library and fast primitive math. The 2.0 series freshens up the API to
more modern Clojure API style, language capabilities, and coding
conventions. The JARs for the 1.0 series will remain available
indefinitely (probably a long, long time), so if you can't handle an
API change, you can continue to use it as you always have.

Here's a summary of the changes:

* Options are now set through keyword args to parse-csv and write-csv. The
dynamic vars are removed.
  - Rationale: Dynamic vars are a little annoying to rebind. This can
  tempt you to imprudently set them for too wide a swath of
  code. Reusing the same vars for both reading and writing meant that
  the vars had to have the same meaning in each context, or else two
  vars introduced to accommodate the differences. Keyword args are
  clear, fast, explicit, and local.
* Parsing logic is now based on Java readers instead of Clojure char seqs.
  - Rationale: Largely performance. Clojure's char seqs are not
  particularly fast and throw off a lot of garbage. It's not clear
  that working entirely with pure Clojure data structures was
  providing much value to anyone. When you're doing IO, Readers
  are close at hand in Java, and now the basis for Clojure's IO libs.
* An empty file now parses as a file with no rows.
  - Rationale: The CSV standard actually doesn't say anything about an
  input that is an empty file. Clojure-CSV 1.0 would return a single
  row with an empty string in it. The logic was that a CSV file row is
  everything between the start of a line and the end of the line,
  where an EOF is a line terminator. This would mean an empty file is
  a single row that has an empty field. An alternative, and equally
  valid view is that if a file has nothing in it, there is no row to
  be had. A file that is a single row with an empty field can still be
  expressed in this viewpoint as a file that contains only a line
  terminator. The same cannot be said of the 1.0 view of things: there
  was no way to represent a file with no rows. In any case, I went and
  looked at many other CSV parsing libraries for other languages,
  and they universally took the view that an empty CSV file has no
  rows, so now Clojure-CSV does as well.
* The end-of-line option can now be set during parsing. If end-of-line is
  set to something other than nil, parse-csv will treat \n and \r\n as
  any other character and only use the string given in end-of-line as the
  newline.

Bugs
----
Please let me know of any problems you are having.

Contributors
------------
 - [Slawek Gwizdowski](https://github.com/i0cus)
 - [Matt Lehman](http://github.com/mlehman)
 - [Barrie McGuire](http://github.com/pleasle)

License
--------
Eclipse Public License
