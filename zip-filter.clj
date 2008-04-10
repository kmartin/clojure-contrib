;   Copyright (c) Chris Houser, April 2008. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Common Public License 1.0 (http://opensource.org/licenses/cpl.php)
;   which can be found in the file CPL.TXT at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

; System for filtering trees and nodes generated by zip.clj in
; general, and xml trees in particular.

(clojure/in-ns 'zip-filter)
(clojure/refer 'clojure)

(defn sequential?
  "Returns true if x implements Sequential."
  [x] (instance? clojure.lang.Sequential x))

; This uses the negative form (no-auto) so that the result from any
; naive function, including user functions, defaults to "auto".
(defn auto
  [v x] (with-meta x ((if v dissoc assoc) ^x :zip-filter/no-auto? true)))

(defn auto?
  [x] (not (:zip-filter/no-auto? ^x)))

(defn right-locs
  "Returns a lazy sequence of locations to the right of loc, starting with loc."
  [loc] (when loc (lazy-cons (auto false loc) (right-locs (zip/right loc)))))

(defn left-locs
  "Returns a lazy sequence of locations to the left of loc, starting with loc."
  [loc] (when loc (lazy-cons (auto false loc) (left-locs (zip/left loc)))))

(defn leftmost?
  "Returns true if there are no more nodes to the left of location loc."
  [loc] (nil? (zip/left loc)))

(defn rightmost?
  "Returns true if there are no more nodes to the right of location loc."
  [loc] (nil? (zip/right loc)))

(defn children
  "Returns a lazy sequence of all immediate children of location loc,
  left-to-right."
  [loc]
    (when (zip/branch? loc)
      (map #(auto false %) (right-locs (zip/down loc)))))

(defn children-auto
  "Returns a lazy sequence of all immediate children of location loc,
  left-to-right, marked so that a following tag= predicate will auto-descend."
  #^{:private true}
  [loc]
    (when (zip/branch? loc)
      (map #(auto true %) (right-locs (zip/down loc)))))

(defn descendants
  "Returns a lazy sequence of all descendants of location loc, in
  depth-first order, left-to-right, starting with loc."
  [loc] (lazy-cons (auto false loc) (mapcat descendants (children loc))))

(defn ancestors
  "Returns a lazy sequence of all ancestors of location loc, starting
  with loc and proceeding to loc's parent node and on through to the
  root of the tree."
  [loc] (when loc (lazy-cons (auto false loc) (ancestors (zip/up loc)))))

(defn fixup-apply
  "Calls (pred loc), and then converts the result to the 'appropriate'
  sequence."
  #^{:private true}
  [pred loc]
      (let [rtn (pred loc)]
        (cond (and (map? ^rtn) (:zip-filter/is-node? ^rtn)) (list rtn)
              (= rtn true)                (list loc)
              (= rtn false)               nil
              (nil? rtn)                  nil
              (sequential? rtn)           rtn
              :else                       (list rtn))))

(defn mapcat-chain
  #^{:private true}
  [loc preds mkpred]
    (reduce (fn [prevseq expr]
                (mapcat #(fixup-apply (or (mkpred expr) expr) %) prevseq))
            (list (with-meta loc (assoc ^loc :zip-filter/is-node? true)))
            preds))


; === specialization for xml filtering  ===

(clojure/in-ns 'zip-filter-xml)
(clojure/refer 'clojure)
(refer 'zip-filter)

(def xml->)

(defn attr
  "Returns the xml attribute named attrname, of the xml node at location loc."
  ([attrname]     (fn [loc] (attr loc attrname)))
  ([loc attrname] (when (zip/branch? loc) (-> loc zip/node :attrs attrname))))

(defn attr=
  "Returns a query predicate that matches a node when it has an
  attribute named attrname whose value is attrval."
  [attrname attrval] (fn [loc] (= attrval (attr loc attrname))))

(defn tag=
  "Returns a query predicate that matches a node when its is a tag
  named tagname."
  [tagname]
    (fn [loc]
        (filter #(and (zip/branch? %) (= tagname ((zip/node %) :tag)))
                (if (auto? loc) (children-auto loc) (list (auto true loc))))))

(defn text
  "Returns the textual contents of the given location, similar to
  xpaths's value-of"
  [loc]
    (.replaceAll #^String (apply str (xml-> loc descendants zip/node string?))
       (str "[\\s" (char 160) "]+") " "))

(defn text=
  "Returns a query predicate that matches a node when its textual
  content equals s."
  [s] (fn [loc] (= (text loc) s)))

(defn seq-test
  "Returns a query predicate that matches a node when its xml content
  matches the query expresions given."
  #^{:private true}
  [preds] (fn [loc] (and (apply xml-> loc preds) (list loc))))

(defn xml->
  "The loc is passed to the first predicate.  If the predicate returns
  a collection, each value of the collection is passed to the next
  predicate.  If it returns a location, the location is passed to the
  next predicate.  If it returns true, the input location is passed to
  the next predicate.  If it returns false or nil, the next predicate
  is not called.

  This process is repeated, passing the processed results of each
  predicate to the next predicate.  xml-> returns the final sequence.
  The entire chain is evaluated lazily.

  There are also special predicates: keywords are converted to tag=,
  strings to text=, and vectors to sub-queries that return true if
  they match.

  See the footer of zip-query.clj for examples."
  [loc & preds]
    (mapcat-chain loc preds
                  #(cond (keyword? %) (tag= %)
                         (string?  %) (text= %)
                         (vector?  %) (seq-test %))))

(defn xml1->
  "Returns the first item from loc based on the query predicates
  given.  See xml->"
  [loc & preds] (first (apply xml-> loc preds)))


; === examples ===

(comment

(defn parse-str [s]
  (zip/xml-zip (xml/parse (new org.xml.sax.InputSource
                               (new java.io.StringReader s)))))

(def atom1 (parse-str "<?xml version='1.0' encoding='UTF-8'?>
<feed xmlns='http://www.w3.org/2005/Atom'>
  <id>tag:blogger.com,1999:blog-28403206</id>
  <updated>2008-02-14T08:00:58.567-08:00</updated>
  <title type='text'>n01senet</title>
  <link rel='alternate' type='text/html' href='http://n01senet.blogspot.com/'/>
  <entry>
    <id>1</id>
    <published>2008-02-13</published>
    <title type='text'>clojure is the best lisp yet</title>
    <author><name>Chouser</name></author>
  </entry>
  <entry>
    <id>2</id>
    <published>2008-02-07</published>
    <title type='text'>experimenting with vnc</title>
    <author><name>agriffis</name></author>
  </entry>
</feed>
"))

; simple single-function filter
(assert (= (xml-> atom1 #((zip/node %) :tag))
           '(:feed)))

; two-stage filter using helpful query prediates
(assert (= (xml-> atom1 (tag= :title) text)
           '("n01senet")))

; same filter as above, this time using keyword shortcut
(assert (= (xml-> atom1 :title text)
           '("n01senet")))

; multi-stage filter
(assert (= (xml-> atom1 :entry :author :name text)
           '("Chouser" "agriffis")))

; test xml1->
(assert (= (xml1-> atom1 :entry :author :name text)
           "Chouser"))

; multi-stage filter with subquery specified using a vector
(assert (= (xml-> atom1 :entry [:author :name (text= "agriffis")]
                        :id text)
           '("2")))

; same filter as above, this time using a string shortcut
(assert (= (xml-> atom1 :entry [:author :name "agriffis"] :id text)
           '("2")))

; attribute access
(assert (= (xml-> atom1 :title (attr :type))
           '("text")))

; attribute filtering
(assert (= (xml-> atom1 :link [(attr= :rel "alternate")] (attr :type))
           '("text/html")))

; ancestors
(assert (= (xml-> atom1 descendants :id "2" ancestors zip/node #(:tag %))
           '(:id :entry :feed)))

; ancestors with non-auto tag= (:entry), followed by auto tag= (:id)
(assert (= (xml-> atom1 descendants :name "Chouser" ancestors :entry :id text)
           '("1")))

; left-locs and detection of returning a single loc (zip/up)
(assert (= (xml-> atom1 descendants :name "Chouser" zip/up left-locs :id text)
           '("1")))

; right-locs
(assert (= (xml-> atom1 descendants :id right-locs :author text)
           '("Chouser" "agriffis")))

)
