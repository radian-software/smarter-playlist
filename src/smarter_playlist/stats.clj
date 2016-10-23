(ns smarter-playlist.stats
  (:require [smarter-playlist.util :as util]))

;;;; Summary statistics

(defn mean
  "Returns the mean of a collection of numbers."
  [coll]
  (/ (apply + coll)
     (count coll)))

(defn weighted-mean
  "Returns the mean of a collection of numbers, using the
  provided weights."
  [coll weights]
  (/ (apply + (map * coll weights))
     (apply + weights)))

(defn median
  "Returns the median of a collection of numbers."
  [coll]
  (let [coll (sort coll)]
    (if (odd? (count coll))
      (nth coll (* 1/2 (count coll)))
      (* 1/2
         (+ (nth coll (dec (* 1/2 (count coll))))
            (nth coll (* 1/2 (count coll))))))))

(defn mean-median
  "Returns an averaging function which takes a collection and returns
  the weighted average of the collection's mean and median, using the
  provided weights."
  [& {:keys [mean-weight median-weight]
      :or {mean-weight 1
           median-weight 1}}]
  (fn [coll]
    (weighted-mean
      [(mean coll)
       (median coll)]
      [mean-weight
       median-weight])))

;;;; Distributions

;;; A distribution is a function of no arguments that returns a real
;;; number (probably different) each time it is called.

(defn normal
  "Returns a Normal distribution with the given parameters."
  [& {:keys [mean stdev]
      :or {mean 0
           stdev 1}}]
  (fn []
    (-> util/random
      .nextGaussian
      (* stdev)
      (+ mean))))

(defn bounded
  "Given a distribution, returns a new distribution for which the
  values are guaranteed to be at least :min (if provided) and at most
  :max (if provided). This is done by drawing values repeatedly from
  the base distribution (if :mode is :repeat) or by drawing a single
  value and clamping it to the provided range (if :mode is :clamp, the
  default)."
  [base-distr & {:keys [min max mode]
                 :or {mode :clamp}}]
  (fn []
    (case mode
      :repeat
      (first
        (cond->> (repeatedly base-distr)
          min (filter #(>= % min))
          max (filter #(<= % max))))
      :clamp
      (cond->> (base-distr)
        min (clojure.core/max min)
        max (clojure.core/min max)))))

(defn rounded
  "Given a distribution of doubles, returns a new distribution of
  longs obtained via :round, :floor, or :ceiling operations."
  [base-distr & [strategy]]
  (let [strategy (or strategy :round)]
    (fn []
      (let [x (base-distr)]
        (case strategy
          :round (long
                   (cond-> x
                     (float? x)
                     Math/round))
          :floor (long
                   (cond-> x
                     (float? x)
                     Math/floor))
          :ceiling (long
                     (cond-> x
                       (float? x)
                       Math/ceil))
          (throw (IllegalArgumentException.
                   (str "illegal strategy " strategy))))))))

;;;; Weighting functions

(defn log-power
  "Heavily positively biased weighting function."
  [& {:keys [base power]
      :or {base Math/E
           power 6}}]
  (let [divisor (Math/log base)]
    (fn [x]
      (Math/pow
        (/ (Math/log x)
           divisor)
        power))))

;;;; Random selection

(defn weighted-rand-nth
  "Like `clojure.core/rand-nth` but weights the items as you specify."
  [coll weights]
  (let [choice (rand (apply + weights))]
    (->> weights
      (reductions +)
      (map (fn [item cumulative]
             (when (< choice cumulative)
               item))
           coll)
      (some identity))))
