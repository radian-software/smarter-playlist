(ns playlist.util
  (:require [clojure.edn :as edn]))

(defn sq
  [x]
  (* x x))

(defn cube
  [x]
  (* x x x))

(defn weighted-rand-nth
  [coll weights]
  (let [choice (rand (apply + weights))]
    (->> weights
      (reductions +)
      (map (fn [item cumulative]
             (when (< choice cumulative)
               item))
           coll)
      (some identity))))

(defn- read-all*
  [stream]
  (lazy-seq
    (try
      (cons (edn/read stream)
            (read-all* stream))
      (catch clojure.lang.EdnReader$ReaderException _))))

(defn read-all
  [s]
  (read-all*
    (-> (java.io.StringReader. s)
      (clojure.lang.LineNumberingPushbackReader.))))
