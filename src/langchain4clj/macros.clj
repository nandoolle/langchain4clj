(ns langchain4clj.macros
  "Macros for Java Builder pattern wrappers."
  (:require [clojure.string :as str]))

(defmacro ^{:clj-kondo/ignore [:unresolved-symbol]} defbuilder
  "Creates a fn that builds Java objects from config maps using Builder pattern."
  [fn-name builder-expr field-map]
  (let [config-sym (gensym "config")
        builder-sym (gensym "builder")
        reduce-body (mapcat
                     (fn [[k spec]]
                       (let [[method transformer] (if (vector? spec)
                                                    spec
                                                    [spec nil])
                             method-sym (symbol (str "." (name method)))
                             value-sym (gensym "value")]
                         `[(when-let [~value-sym (get ~config-sym ~k)]
                             (let [~value-sym ~(if transformer
                                                 `(~transformer ~value-sym)
                                                 value-sym)]
                               (when (some? ~value-sym)
                                 (~method-sym ~builder-sym ~value-sym))))]))
                     field-map)]
    `(defn ~fn-name [~config-sym]
       (let [~builder-sym ~builder-expr]
         ~@reduce-body
         (.build ~builder-sym)))))

(defn kebab->camel
  "Converts :kebab-case to \"camelCase\"."
  [k]
  (let [parts (clojure.string/split (name k) #"-")]
    (str (first parts)
         (apply str (map clojure.string/capitalize (rest parts))))))

(defn build-field-map
  "Builds field map for defbuilder, converting kebab to camelCase."
  [fields]
  (into {}
        (map (fn [field]
               (if (vector? field)
                 (let [[k transformer] field]
                   [k [(keyword (kebab->camel k)) transformer]])
                 [field (keyword (kebab->camel field))])))
        fields))

(defn build-with
  "Builds Java object from builder and config map using reflection."
  [builder config-map]
  (doseq [[k v] config-map]
    (when (some? v)
      (let [method-name (kebab->camel k)
            method (try
                     (first (filter #(= method-name (.getName %))
                                    (.getMethods (class builder))))
                     (catch Exception _ nil))]
        (when method
          (.invoke method builder (into-array Object [v]))))))
  (.build builder))

(defn apply-if
  "Applies f to value only if condition is truthy."
  [value condition f & args]
  (if condition
    (apply f value args)
    value))

(defn apply-when-some
  "Applies f to value only when x is not nil."
  [value x f & args]
  (if (some? x)
    (apply f value args)
    value))

(defn deep-merge
  "Recursively merges maps. Later maps take precedence."
  [& maps]
  (letfn [(merge-entry [m e]
            (let [k (key e)
                  v (val e)]
              (if (and (map? v) (map? (get m k)))
                (assoc m k (deep-merge (get m k) v))
                (assoc m k v))))]
    (reduce
     (fn [m1 m2]
       (reduce merge-entry m1 m2))
     (or (first maps) {})
     (rest maps))))

(defn with-defaults
  "Merges config with defaults, preferring config values."
  [config defaults]
  (merge defaults config))

(defn deprecation-warning
  "Prints a deprecation warning for old-fn with new-usage suggestion."
  [old-fn new-usage remove-ver]
  (println (format "⚠️  DEPRECATION WARNING: %s is deprecated.\n   Use: %s\n   Will be removed in: %s"
                   old-fn
                   new-usage
                   remove-ver)))


