;;  Copyright (c) Stephen C. Gilardi. All rights reserved.  The use and
;;  distribution terms for this software are covered by the Eclipse Public
;;  License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can
;;  be found in the file epl-v10.html at the root of this distribution.  By
;;  using this software in any fashion, you are agreeing to be bound by the
;;  terms of this license.  You must not remove this notice, or any other,
;;  from this software.
;;
;;  condition.clj
;;
;;  Flexible raising and handling of conditions. A condition is a map
;;  containing:
;;
;;    - keys and values specified as arguments to raise, and
;;    - a stack trace at key :stack-trace.
;;
;;  Note: requires AOT compilation.
;;
;;  Based on an idea from Chouser:
;;  http://groups.google.com/group/clojure/browse_frm/thread/da1285c538f22bb5
;;
;;  scgilardi (gmail)
;;  Created 09 June 2009

(ns #^{:author "Stephen C. Gilardi",
       :doc "Flexible raising and handling of conditions. A condition is a map
containing:

  - keys and values specified as arguments to raise, and
  - a stack trace at key :stack-trace.

Note: requires AOT compilation.

Based on an idea from Chouser:
http://groups.google.com/group/clojure/browse_frm/thread/da1285c538f22bb5"}
  clojure.contrib.condition
  (:require clojure.contrib.condition.Condition)
  (:import clojure.contrib.condition.Condition)
  (:use [clojure.contrib.def :only (defvar)]))

(defvar *condition*
  "While a handler is running, bound to the condition being handled")

(defvar *selector*
  "While a handler is running, bound to the selector returned by
  dispatch-fn for *condition*")

(defvar *condition-object*
  "While a handler is running, bound to the Condition object being
  handled")

(defmacro raise
  "Raises a condition with the supplied mappings. With no arguments,
  re-raises the current condition. (keyval => key val)"
  [& keyvals]
  `(let [m# (hash-map ~@keyvals)]
     (throw (if (seq m#)
              (Condition. m#)
              *condition-object*))))

(defmacro handler-case
  "Executes body in a context in which raised conditions can be handled.

  dispatch-fn accepts a raised condition (a map) and returns a selector
  value used to choose a handler.

  Handlers are forms within body:

    (handle key
      ...)

  If a condition is raised, handler-case executes the body of the first
  handler whose key satisfies (isa? selector key). If no handlers match,
  the condition is re-raised.

  While a handler is running, *condition* is bound to the condition being
  handled and *selector* is bound to to the value returned by dispatch-fn
  that matched the handler's key."
  [dispatch-fn & body]
  (loop [[form & forms :as body] body
         m {:code [] :handlers []}]
    (if (seq body)
      (recur
       forms
       (if (and (list? form) (= (first form) 'handle))
         (let [[_ key & body] form
               handler `[(isa? *selector* ~key) (do ~@body)]]
           (update-in m [:handlers] concat handler))
         (update-in m [:code] conj form)))
      `(try
        ~@(:code m)
        (catch Condition c#
          (binding [*condition-object* c#
                    *condition* ^c#
                    *selector* (~dispatch-fn ^c#)]
            (cond
             ~@(:handlers m)
             :else (raise))))))))

(defn print-stack-trace
  "Prints the stack trace for a condition"
  [condition]
  (printf "condition\n")
  (doseq [frame (:stack-trace condition)]
    (printf "        at %s.%s(%s:%s)\n"
            (.getClassName frame)
            (.getMethodName frame)
            (.getFileName frame)
            (.getLineNumber frame))))
