(ns langchain4clj.tools
  "Tool support with automatic schema detection (Spec, Schema, Malli)."
  (:require [langchain4clj.tools.protocols :as p]
            [langchain4clj.tools.spec :as spec-impl]
            [langchain4clj.specs :as specs]
            [clojure.spec.alpha :as s]
            [clojure.walk :as walk]
            [clojure.string :as str])
  (:import [dev.langchain4j.agent.tool ToolSpecification]
           [dev.langchain4j.data.message ToolExecutionResultMessage]
           [dev.langchain4j.model.chat.request.json
            JsonObjectSchema
            JsonStringSchema
            JsonIntegerSchema
            JsonNumberSchema
            JsonBooleanSchema
            JsonArraySchema]))

(defn- generate-tool-spec
  [tool-name schema-map]
  (let [spec-ns (str *ns*)
        tool-spec-ns (str spec-ns "." tool-name)
        param-specs (for [[k pred] schema-map]
                      (let [spec-name (keyword tool-spec-ns (name k))]
                        `(s/def ~spec-name ~pred)))
        param-spec-name (keyword tool-spec-ns "params")
        param-spec-keys (mapv (fn [[k _]]
                                (keyword tool-spec-ns (name k)))
                              schema-map)]
    {:param-specs param-specs
     :param-spec-name param-spec-name
     :param-spec `(s/def ~param-spec-name
                    (s/keys :req-un ~param-spec-keys))}))

(defmacro deftool
  "Creates a tool with defn-like syntax. Args: name, docstring, schema-map, args, body."
  [name docstring schema-map args & body]
  {:pre [(symbol? name)
         (string? docstring)
         (map? schema-map)
         (vector? args)]}

  (let [tool-name-str (clojure.core/name name)]
    (if (empty? schema-map)
      `(def ~name
         ~docstring
         (create-tool
          {:name ~tool-name-str
           :description ~docstring
           :params-schema :no-params
           :fn (fn ~args ~@body)}))

      (let [{:keys [param-specs param-spec-name param-spec]}
            (generate-tool-spec tool-name-str schema-map)]
        `(do
           ~@param-specs
           ~param-spec
           (def ~name
             ~docstring
             (create-tool
              {:name ~tool-name-str
               :description ~docstring
               :params-schema ~param-spec-name
               :fn (fn ~args ~@body)})))))))

(try
  (require '[langchain4clj.tools.schema :as schema-impl])
  (catch Exception _ nil))

(try
  (require '[langchain4clj.tools.malli :as malli-impl])
  (catch Exception _ nil))

(defn detect-schema-type
  "Returns :spec, :schema, :malli, or nil."
  [schema]
  (cond
    (spec-impl/spec? schema) :spec
    (and (find-ns 'langchain4clj.tools.malli)
         ((resolve 'langchain4clj.tools.malli/malli?) schema)) :malli
    (and (find-ns 'langchain4clj.tools.schema)
         ((resolve 'langchain4clj.tools.schema/schema?) schema)) :schema
    :else nil))

(defn create-provider
  "Creates SchemaProvider based on schema type (auto-detected or explicit)."
  ([schema] (create-provider schema nil))
  ([schema schema-type]
   (let [type (or schema-type (detect-schema-type schema))]
     (case type
       :spec (spec-impl/create-spec-provider schema)
       :schema (if (find-ns 'langchain4clj.tools.schema)
                 ((resolve 'langchain4clj.tools.schema/create-schema-provider) schema)
                 (throw (ex-info "Prismatic Schema not available" {:schema-type :schema})))
       :malli (if (find-ns 'langchain4clj.tools.malli)
                ((resolve 'langchain4clj.tools.malli/create-malli-provider) schema)
                (throw (ex-info "Malli not available" {:schema-type :malli})))
       (throw (ex-info "Cannot determine schema type" {:schema schema}))))))

(defn build-json-schema
  "Builds JsonObjectSchema from a Clojure map representation."
  [schema-map]
  (letfn [(build-element [elem-map]
            (let [type (:type elem-map)]
              (case type
                "string"
                (cond-> (JsonStringSchema/builder)
                  (:description elem-map) (.description (:description elem-map))
                  (:enum elem-map) (.enumValues (:enum elem-map))
                  true (.build))

                "integer"
                (cond-> (JsonIntegerSchema/builder)
                  (:description elem-map) (.description (:description elem-map))
                  true (.build))

                "number"
                (cond-> (JsonNumberSchema/builder)
                  (:description elem-map) (.description (:description elem-map))
                  true (.build))

                "boolean"
                (cond-> (JsonBooleanSchema/builder)
                  (:description elem-map) (.description (:description elem-map))
                  true (.build))

                "array"
                (let [items-schema (when-let [items (:items elem-map)]
                                     (build-element items))]
                  (cond-> (JsonArraySchema/builder)
                    items-schema (.items items-schema)
                    (:description elem-map) (.description (:description elem-map))
                    true (.build)))

                "object"
                (let [properties (:properties elem-map)
                      required (:required elem-map)
                      builder (JsonObjectSchema/builder)]
                  (doseq [[prop-name prop-schema] properties]
                    (.addProperty builder (name prop-name) (build-element prop-schema)))
                  (when (seq required)
                    (.required builder (mapv name required)))
                  (when-let [desc (:description elem-map)]
                    (.description builder desc))
                  (.build builder))

                (-> (JsonStringSchema/builder) (.build)))))]
    (build-element schema-map)))

(defn- is-kebab-case?
  "Returns true if string contains non-alphanumeric chars (hyphens, etc)."
  [s]
  (boolean (re-find #"[^a-zA-Z0-9_]" s)))

(defn- kebab->camel
  "Converts kebab-case to camelCase. Preserves other formats."
  [k]
  (let [s (if (keyword? k) (name k) (str k))]
    (if (is-kebab-case? s)
      (let [parts (str/split s #"-")]
        (str (first parts) (apply str (map str/capitalize (rest parts)))))
      s)))

(defn- camel->kebab
  "Converts camelCase to kebab-case keyword."
  [k]
  (let [s (if (keyword? k) (name k) (str k))]
    (if (re-find #"[A-Z]" s)
      (keyword (str/lower-case (str/replace s #"([a-z])([A-Z])" "$1-$2")))
      (if (keyword? k) k (keyword s)))))

(defn normalize-tool-params
  "Normalizes parameter keys for OpenAI compatibility.
   Kebab-case keywords get camelCase string equivalents added."
  [params]
  (walk/postwalk
   (fn [form]
     (if (map? form)
       (reduce-kv
        (fn [m k v]
          (cond
            (keyword? k)
            (let [k-str (name k)]
              (if (is-kebab-case? k-str)
                (assoc m k v (kebab->camel k) v)
                (assoc m k v k-str v)))

            (string? k)
            (if (is-kebab-case? k)
              (assoc m k v (kebab->camel k) v (keyword k) v)
              (assoc m k v))

            :else (assoc m k v)))
        {}
        form)
       form))
   params))

(defn create-tool-specification
  "Creates a LangChain4j ToolSpecification."
  [{:keys [name description parameters]}]
  (-> (ToolSpecification/builder)
      (.name name)
      (.description description)
      (.parameters (build-json-schema parameters))
      (.build)))

(defn create-tool
  "Creates a tool with schema validation.
   
   Required: :name, :description, :params-schema (use :no-params if none), :fn
   Optional: :result-schema, :schema-type"
  [{:keys [name description params-schema result-schema schema-type]
    tool-fn :fn}]
  {:pre [(string? name)
         (string? description)
         (some? params-schema)
         (ifn? tool-fn)]}

  (let [params-provider (when (and params-schema (not= params-schema :no-params))
                          (create-provider params-schema schema-type))
        result-provider (when result-schema
                          (create-provider result-schema schema-type))

        executor-fn (cond
                      (= params-schema :no-params)
                      (fn [params] (tool-fn params))

                      (and params-provider result-provider)
                      (fn [params]
                        (let [normalized-params (normalize-tool-params params)
                              coerced-params (p/coerce params-provider normalized-params)
                              _ (p/validate params-provider coerced-params)
                              result (tool-fn coerced-params)]
                          (p/validate result-provider result)))

                      params-provider
                      (fn [params]
                        (let [normalized-params (normalize-tool-params params)
                              coerced-params (p/coerce params-provider normalized-params)]
                          (p/validate params-provider coerced-params)
                          (tool-fn coerced-params)))

                      result-provider
                      (fn [params]
                        (let [normalized-params (normalize-tool-params params)
                              result (tool-fn normalized-params)]
                          (p/validate result-provider result)))

                      :else
                      (fn [params] (tool-fn (normalize-tool-params params))))

        json-schema (when params-provider (p/to-json-schema params-provider))
        spec (create-tool-specification
              {:name name
               :description description
               :parameters (or json-schema {:type "object"})})]

    {:name name
     :description description
     :specification spec
     :executor-fn executor-fn
     :params-schema params-schema
     :result-schema result-schema
     :schema-type (or schema-type
                      (when (and params-schema (not= params-schema :no-params))
                        (detect-schema-type params-schema)))}))

(defn ^:deprecated tool
  "DEPRECATED: Use `deftool` or `create-tool` instead."
  [name description fn]
  (println "WARNING: `tool` is deprecated. Use `deftool` or `create-tool`.")
  {:name name
   :description description
   :executor-fn fn
   :specification (create-tool-specification
                   {:name name
                    :description description
                    :parameters {:type "object"}})})

(defn ^:deprecated with-params-schema
  "DEPRECATED: Use `deftool` or `create-tool` instead."
  [tool schema]
  (println "WARNING: `with-params-schema` is deprecated.")
  (let [provider (create-provider schema nil)
        json-schema (p/to-json-schema provider)
        validated-fn (fn [params]
                       (let [normalized-params (normalize-tool-params params)
                             coerced (p/coerce provider normalized-params)]
                         (p/validate provider coerced)
                         ((:executor-fn tool) coerced)))]
    (assoc tool
           :executor-fn validated-fn
           :params-schema schema
           :schema-type (detect-schema-type schema)
           :specification (create-tool-specification
                           {:name (:name tool)
                            :description (:description tool)
                            :parameters (or json-schema {:type "object"})}))))

(defn with-result-schema
  "Adds result schema validation to a tool."
  [tool schema]
  (let [provider (create-provider schema nil)
        validated-fn (fn [params]
                       (let [result ((:executor-fn tool) params)]
                         (p/validate provider result)
                         result))]
    (assoc tool
           :executor-fn validated-fn
           :result-schema schema)))

(defn with-validation
  "Enables validation (alias for when schemas already set)."
  [tool]
  tool)

(defn with-description
  "Updates tool description."
  [tool new-description]
  (assoc tool
         :description new-description
         :specification (create-tool-specification
                         {:name (:name tool)
                          :description new-description
                          :parameters (or (when-let [schema (:params-schema tool)]
                                            (p/to-json-schema (create-provider schema nil)))
                                          {:type "object"})})))

(defn execute-tool
  "Executes a tool with given args."
  [tool args]
  ((:executor-fn tool) args))

(defn find-tool
  "Finds a tool by name in a collection."
  [tool-name tools]
  (first (filter #(= (:name %) tool-name) tools)))

(defn create-tool-result-message
  "Creates a ToolExecutionResultMessage for LangChain4j."
  [{:keys [tool-request result]}]
  (ToolExecutionResultMessage/from tool-request (pr-str result)))

(defn validate-batch
  "Validates a batch of data against a schema."
  [schema-or-provider data-seq]
  (let [provider (if (satisfies? p/SchemaProvider schema-or-provider)
                   schema-or-provider
                   (create-provider schema-or-provider))]
    (mapv #(p/validate provider %) data-seq)))

(defn coerce-batch
  "Coerces a batch of data."
  [schema-or-provider data-seq]
  (let [provider (if (satisfies? p/SchemaProvider schema-or-provider)
                   schema-or-provider
                   (create-provider schema-or-provider))]
    (mapv #(p/coerce provider %) data-seq)))

(def ^:private tool-registry (atom {}))

(defn register-tool!
  "Registers a tool globally."
  [tool]
  (swap! tool-registry assoc (:name tool) tool)
  tool)

(defn get-tool
  "Gets a tool from the registry."
  [tool-name]
  (get @tool-registry tool-name))

(defn list-tools
  "Lists registered tool names."
  []
  (keys @tool-registry))

(defn clear-tools!
  "Clears the tool registry."
  []
  (reset! tool-registry {}))

(defn with-logging
  "Adds logging to tool execution."
  [tool]
  (update tool :executor-fn
          (fn [f]
            (fn [params]
              (println (str "[" (:name tool) "] Input: " (pr-str params)))
              (let [result (f params)]
                (println (str "[" (:name tool) "] Output: " (pr-str result)))
                result)))))

(defn with-timing
  "Adds timing to tool execution."
  [tool]
  (update tool :executor-fn
          (fn [f]
            (fn [params]
              (let [start (System/currentTimeMillis)
                    result (f params)
                    elapsed (- (System/currentTimeMillis) start)]
                (println (str "[" (:name tool) "] Time: " elapsed "ms"))
                result)))))

(defn with-retry
  "Adds retry logic to a tool."
  ([tool] (with-retry tool 3))
  ([tool max-attempts]
   (update tool :executor-fn
           (fn [f]
             (fn [params]
               (loop [attempt 1]
                 (let [result (try
                                {:success true :value (f params)}
                                (catch Exception e
                                  {:success false :error e}))]
                   (if (:success result)
                     (:value result)
                     (if (< attempt max-attempts)
                       (do
                         (println (str "[" (:name tool) "] Attempt " attempt " failed, retrying..."))
                         (Thread/sleep (* 1000 attempt))
                         (recur (inc attempt)))
                       (throw (:error result)))))))))))

(s/fdef create-tool
  :args (s/cat :config ::specs/tool-definition)
  :ret map?)

(s/fdef execute-tool
  :args (s/cat :tool map? :args map?)
  :ret any?)

(s/fdef with-retry
  :args (s/alt :tool-only (s/cat :tool map?)
               :with-attempts (s/cat :tool map? :max-attempts pos-int?))
  :ret map?)

(s/fdef register-tool!
  :args (s/cat :tool map?)
  :ret nil?)

(s/fdef get-tool
  :args (s/cat :tool-name (s/or :keyword keyword? :string string?))
  :ret (s/nilable map?))

