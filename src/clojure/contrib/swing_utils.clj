;;  Copyright (c) Stephen C. Gilardi. All rights reserved.  The use and
;;  distribution terms for this software are covered by the Eclipse Public
;;  License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can
;;  be found in the file epl-v10.html at the root of this distribution.  By
;;  using this software in any fashion, you are agreeing to be bound by the
;;  terms of this license.  You must not remove this notice, or any other,
;;  from this software.
;;
;;  clojure.contrib.swing-utils
;;
;;  Useful functions for interfacing Clojure to Swing
;;
;;  scgilardi (gmail)
;;  Created 31 May 2009

(ns clojure.contrib.swing-utils
  (:import (java.awt.event ActionListener KeyAdapter)
           (javax.swing SwingUtilities)))

(defn add-action-listener
  "Adds an ActionLister to component. When the action fires, f will be
  invoked with the event as its first argument followed by args.
  Returns the listener."
  [component f & args]
  (let [listener (proxy [ActionListener] []
                   (actionPerformed [event] (apply f event args)))]
    (.addActionListener component listener)
    listener))

(defn add-key-typed-listener
  "Adds a KeyListener to component that only responds to KeyTyped events.
  When a key is typed, f is invoked with the KeyEvent as its first argument
  followed by args. Returns the listener."
  [component f & args]
  (let [listener (proxy [KeyAdapter] []
                   (keyTyped [event] (apply f event args)))]
    (.addKeyListener component listener)
    listener))

;; ----------------------------------------------------------------------
;; Meikel Brandmeyer

(defn do-swing*
  "Runs thunk in the Swing event thread according to schedule:
    - :later => schedule the execution and return immediately
    - :now   => wait until the execution completes."
  [schedule thunk]
  (cond
   (= schedule :later) (SwingUtilities/invokeLater thunk)
   (= schedule :now) (if (SwingUtilities/isEventDispatchThread)
                       (thunk)
                       (SwingUtilities/invokeAndWait thunk)))
  nil)

(defmacro do-swing
  "Executes body in the Swing event thread asynchronously. Returns
  immediately after scheduling the execution."
  [& body]
  `(do-swing* :later (fn [] ~@body)))

(defmacro do-swing-and-wait
  "Executes body in the Swing event thread synchronously. Returns
  after the execution is complete."
  [& body]
  `(do-swing* :now (fn [] ~@body)))

;; ----------------------------------------------------------------------
