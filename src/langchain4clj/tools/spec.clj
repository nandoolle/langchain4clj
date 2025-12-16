(ns langchain4clj.tools.spec
  "Clojure Spec implementation of SchemaProvider protocol"
  (:require [clojure.spec.alpha :as s]
            [langchain4clj.tools.protocols :as p]))

(defn spec-form
  "Gets the form of a spec, handling different spec types"
  [spec]
  (cond
    (keyword? spec) (s/form spec)
    (s/spec? spec) (s/form spec)
    :else spec))

(defn analyze-keys-spec
  "Analyzes a keys spec to extract required and optional keys"
  [form]
  (when (and (seq? form) (= (first form) `s/keys))
    (let [spec-map (apply hash-map (rest form))]
      {:req (set (:req-un spec-map))
       :opt (set (:opt-un spec-map))
       :req-keys (set (:req spec-map))
       :opt-keys (set (:opt spec-map))})))

(defn spec->json-type
  "Converts a spec predicate to JSON Schema type"
  [pred]
  (cond
    (#{`string? 'string?} pred) "string"
    (#{`int? 'int? `integer? 'integer?} pred) "integer"
    (#{`number? 'number? `double? 'double? `float? 'float?} pred) "number"
    (#{`boolean? 'boolean? `bool? 'bool?} pred) "boolean"
    (#{`map? 'map?} pred) "object"
    (#{`coll? 'coll? `vector? 'vector? `seq? 'seq?} pred) "array"
    (#{`any? 'any?} pred) "any"
    :else "string"))

(defn spec->json-schema
  "Converts a Clojure spec to JSON Schema"
  [spec]
  (let [form (spec-form spec)]
    (cond
      ;; Simple predicates
      (symbol? form)
      {:type (spec->json-type form)}

      ;; s/and specs
      (and (seq? form) (= (first form) `s/and))
      (spec->json-schema (second form)) ; Take first for simplicity

      ;; s/or specs
      (and (seq? form) (= (first form) `s/or))
      {:oneOf (vec (map spec->json-schema (take-nth 2 (rest (rest form)))))}

      ;; s/keys specs
      (and (seq? form) (= (first form) `s/keys))
      (let [keys-info (analyze-keys-spec form)
            all-keys (concat (:req keys-info) (:opt keys-info)
                             (:req-keys keys-info) (:opt-keys keys-info))
            properties (into {}
                             (map (fn [k]
                                    [(name k) (spec->json-schema k)])
                                  all-keys))
            required (vec (map name (concat (:req keys-info)
                                            (:req-keys keys-info))))]
        {:type "object"
         :properties properties
         :required required})

      ;; s/coll-of specs
      (and (seq? form) (= (first form) `s/coll-of))
      {:type "array"
       :items (spec->json-schema (second form))}

      ;; s/every specs
      (and (seq? form) (= (first form) `s/every))
      {:type "array"
       :items (spec->json-schema (second form))}

      ;; Default
      :else {:type "any"})))

(defrecord SpecProvider [spec]
  p/SchemaProvider

  (validate [_ data]
    (if (s/valid? spec data)
      data
      (throw (ex-info "Validation failed"
                      {:type :validation-error
                       :spec spec
                       :data data
                       :explanation (s/explain-data spec data)}))))

  (coerce [this data]
    ;; Spec doesn't have built-in coercion, but we can use conform
    (let [conformed (s/conform spec data)]
      (if (= conformed ::s/invalid)
        (p/validate this data) ; Will throw with details
        conformed)))

  (to-json-schema [_]
    (spec->json-schema spec))

  (explain-error [_ data]
    (s/explain-str spec data)))

(defn create-spec-provider
  "Creates a SchemaProvider for a Clojure spec"
  [spec]
  (->SpecProvider spec))

(defn spec?
  "Checks if the given value is a spec"
  [x]
  (or (keyword? x)
      (s/spec? x)
      (and (seq? x)
           (#{`s/keys `s/and `s/or `s/coll-of `s/every} (first x)))))
