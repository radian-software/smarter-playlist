(ns smarter-playlist.util
  (:refer-clojure :exclude [read])
  (:require [clojure
             [edn :as edn]
             [set :as set]
             [string :as str]]
            [clojure.tools.cli :as cli]))

(def random
  "Shared instance of java.util.Random."
  (java.util.Random.))

(defn sq
  "Returns the square of a number."
  [x]
  (* x x))

(defn cube
  "Returns the cube of a number."
  [x]
  (* x x x))

(defn abs
  "Returns the absolute value of a number."
  [x]
  (Math/abs x))

(defn read
  "Returns the first Clojure object read from the string."
  [s]
  (edn/read
    (-> (java.io.StringReader. s)
      (clojure.lang.LineNumberingPushbackReader.))))

(defn- read-all*
  "Returns a lazy sequence of Clojure objects read from the stream."
  [stream]
  (lazy-seq
    (try
      (cons (edn/read stream)
            (read-all* stream))
      (catch clojure.lang.EdnReader$ReaderException _))))

(defn read-all
  "Returns a lazy sequence of Clojure objects read from the string."
  [s]
  (read-all*
    (-> (java.io.StringReader. s)
      (clojure.lang.LineNumberingPushbackReader.))))

(defmacro defconfig
  "Like `def`, but attaches some metadata to the created var which
  allows it to be identified as a configurable parameter.
  Automatically declares the var :dynamic."
  [& [symbol doc-string? init? :as args]]
  {:arglists ([symbol doc-string? init?])
   :style/indent 1}
  (let [init (if (>= (count args) 3)
               init?
               doc-string?)]
    `(do
       (def ~@args)
       (alter-meta!
         (var ~symbol)
         merge
         {:dynamic true
          :config true
          :default '~init})
       (var ~symbol))))

(defn- get-config-map
  "Returns a map from symbols representing the config vars in the
  current namespace (see `defconfig`) to maps containing
  their :docstring, :default value (as an unevaluated form),
  and :var."
  []
  (->> *ns*
    (ns-interns)
    (reduce-kv (fn [config-map sym v]
                 (cond-> config-map
                   (:config (meta v))
                   (assoc sym
                          {:docstring (:doc (meta v))
                           :default (:default (meta v))
                           :var v})))
               {})))

(defn- options->bindings
  "Converts the return value of `clojure.tools.cli/parse-opts` into a
  form suitable for `with-redefs-fn`."
  [config-map options-map]
  (reduce-kv (fn [bindings-map sym value]
               (assoc bindings-map
                      (get-in config-map [sym :var])
                      (eval value)))
             {}
             options-map))

(defn with-parsed-options
  "Parses the arguments and binds the configuration parameters defined
  in the current namespace accordingly, printing their values. Then
  calls func with no arguments. If the provided arguments are invalid,
  an error message is printed and func is not called."
  [args func]
  (let [config-map (get-config-map)
        {:keys [options errors]}
        (cli/parse-opts
          args
          (vec
            (for [[sym {:keys [docstring default]}] config-map]
              [nil
               (format "--%s %s"
                       (str sym)
                       (str/upper-case sym))
               docstring
               :id sym
               :parse-fn read
               :default default]))
          :no-defaults true)]
    (if-not (seq errors)
      (do
        (doseq [[sym {:keys [default]}] (sort-by key config-map)]
          (if (contains? options sym)
            (printf "%s = %s%n"
                    sym (pr-str (get options sym)))
            (printf "%s = %s [default]%n"
                    sym (pr-str default))))
        (with-redefs-fn
          (options->bindings config-map options)
          func))
      (doseq [error errors]
        (println error)))))
