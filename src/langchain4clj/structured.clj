(ns langchain4clj.structured
  "Structured output support for LangChain4j."
  (:require [langchain4clj.core :as core]
            [langchain4clj.tools :as tools]
            [langchain4clj.specs :as specs]
            [langchain4clj.constants :as const]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [jsonista.core :as j])
  (:import [dev.langchain4j.model.chat.request ChatRequest ResponseFormat]
           [dev.langchain4j.model.openai OpenAiChatModel]
           [dev.langchain4j.agent.tool ToolSpecification]
           [dev.langchain4j.data.message UserMessage]))

(def ^:private json-mapper
  (j/object-mapper {:decode-key-fn keyword :encode-key-fn name :pretty true}))

(declare schema->example validate-schema supports-json-mode? supports-tools?)

(defn edn->json-str [edn-data]
  (j/write-value-as-string edn-data json-mapper))

(defn json-str->edn [json-str]
  (j/read-value json-str json-mapper))

(defn chat-json-mode
  "Uses native JSON mode. Returns EDN by default."
  [model prompt & {:keys [schema output-format add-json-instruction?]
                   :or {output-format :edn add-json-instruction? true}}]
  (let [has-json? (re-find #"(?i)json" prompt)
        enhanced-prompt (cond
                          (false? add-json-instruction?) prompt
                          has-json? (if schema
                                      (str prompt "\n\nMatch this structure:\n"
                                           (edn->json-str (schema->example schema)))
                                      prompt)
                          :else (if schema
                                  (str prompt "\n\nReturn JSON matching this structure:\n"
                                       (edn->json-str (schema->example schema)))
                                  (str prompt "\n\nReturn your response as valid JSON.")))
        user-message (UserMessage. enhanced-prompt)
        request (-> (ChatRequest/builder)
                    (.messages [user-message])
                    (.responseFormat ResponseFormat/JSON)
                    (.build))
        response (.chat model request)
        json-response (-> response .aiMessage .text)]
    (case output-format
      :json json-response
      :json-str json-response
      :edn (json-str->edn json-response)
      (json-str->edn json-response))))

(defn chat-edn-mode
  "Returns EDN data using JSON mode."
  [model prompt & {:keys [schema]}]
  (chat-json-mode model prompt :schema schema :output-format :edn))

(defn custom-schema->json-schema
  "Converts {:name :string} to JSON Schema format."
  [schema]
  (cond
    (and (map? schema) (:type schema)) schema

    (map? schema)
    {:type "object"
     :properties (into {} (map (fn [[k v]]
                                 [(name k)
                                  (cond
                                    (#{:string :str} v) {:type "string"}
                                    (#{:int :integer} v) {:type "integer"}
                                    (#{:number :float :double} v) {:type "number"}
                                    (#{:boolean :bool} v) {:type "boolean"}
                                    (map? v) (custom-schema->json-schema v)
                                    (vector? v) {:type "array"
                                                 :items (if (seq v)
                                                          (custom-schema->json-schema (first v))
                                                          {:type "string"})}
                                    :else {:type "string"})])
                               schema))}

    (vector? schema)
    {:type "array"
     :items (if (seq schema) (custom-schema->json-schema (first schema)) {:type "string"})}

    (keyword? schema)
    (case schema
      (:string :str) {:type "string"}
      (:int :integer) {:type "integer"}
      (:number :float :double) {:type "number"}
      (:boolean :bool) {:type "boolean"}
      {:type "string"})

    :else {:type "object"}))

(defn chat-with-output-tool
  "Uses function calling for structured output."
  [model prompt output-schema & {:keys [output-format] :or {output-format :edn}}]
  (let [json-schema (custom-schema->json-schema output-schema)
        json-object-schema (tools/build-json-schema json-schema)
        tool-spec (-> (ToolSpecification/builder)
                      (.name "return_structured_data")
                      (.description "Returns the structured response")
                      (.parameters json-object-schema)
                      (.build))
        enhanced-prompt (str prompt "\n\nYou MUST call the 'return_structured_data' function with your response.")
        response (core/chat model enhanced-prompt {:tools [tool-spec]})]
    (when-not response
      (throw (ex-info "Chat returned nil response" {:model model :prompt enhanced-prompt})))
    (let [ai-message (.aiMessage response)]
      (when-not ai-message
        (throw (ex-info "Model did not return an AI message" {:response response})))
      (let [tool-calls (.toolExecutionRequests ai-message)]
        (if (and tool-calls (seq tool-calls))
          (let [json-args (-> tool-calls first .arguments)]
            (case output-format
              :json json-args
              :json-str json-args
              :edn (json-str->edn json-args)
              (json-str->edn json-args)))
          (throw (ex-info "Model did not use the output tool" {:response response})))))))

(defn chat-with-validation
  "Uses iterative prompting to ensure valid structured output."
  [model prompt schema & {:keys [max-attempts output-format]
                          :or {max-attempts const/default-max-attempts output-format :edn}}]
  (loop [attempt 1]
    (let [example (schema->example schema)
          example-str (if (= output-format :edn) (pr-str example) (edn->json-str example))
          format-name (if (= output-format :edn) "EDN" "JSON")
          enhanced-prompt (if (= attempt 1)
                            (str prompt "\n\nReturn ONLY valid " format-name " matching this structure:\n" example-str)
                            (str "That was not valid " format-name ". Please return ONLY valid " format-name " matching the structure."))
          response (core/chat model enhanced-prompt)
          parsed (try
                   (case output-format
                     :edn (read-string response)
                     :json (json-str->edn response)
                     :json-str response
                     (read-string response))
                   (catch Exception _e nil))]
      (cond
        (and parsed (validate-schema schema parsed)) parsed
        (< attempt max-attempts) (recur (inc attempt))
        :else (throw (ex-info "Could not get valid structured output" {:attempts attempt :last-response response}))))))

(defn structured-output
  "Intelligent structured output using best available method."
  [model prompt {:keys [schema strategy output-format validate?]
                 :or {output-format :edn validate? true}}]
  {:pre [(some? model) (string? prompt) (some? schema)]}
  (let [supports-json-mode? (supports-json-mode? model)
        supports-tools? (supports-tools? model)
        chosen-strategy (or strategy
                            (cond supports-json-mode? :json-mode
                                  supports-tools? :tools
                                  :else :validation))
        result (case chosen-strategy
                 :json-mode (chat-json-mode model prompt :schema schema :output-format output-format)
                 :tools (chat-with-output-tool model prompt schema :output-format output-format)
                 :validation (chat-with-validation model prompt schema :output-format output-format)
                 (throw (ex-info "Unknown strategy" {:strategy chosen-strategy})))]
    (if (and validate? (not= output-format :json-str))
      (if (validate-schema schema result)
        result
        (throw (ex-info "Output validation failed" {:output result :schema schema})))
      result)))

(defn supports-json-mode? [model]
  (or (instance? OpenAiChatModel model) false))

(defn supports-tools? [_model]
  true)

(defn schema->example
  "Generates example from schema."
  [schema]
  (cond
    (and (map? schema) (:type schema))
    (let [type (:type schema)]
      (case type
        "object" (into {} (map (fn [[k v]] [(keyword k) (schema->example v)]) (:properties schema)))
        "array" (if-let [items (:items schema)] [(schema->example items)] [])
        "string" (or (:enum schema) "example string")
        "integer" 42
        "number" 3.14
        "boolean" true
        "example"))

    (map? schema)
    (into {} (map (fn [[k v]]
                    [k (cond
                         (#{:string :str} v) "example"
                         (#{:int :integer} v) 42
                         (#{:number :float :double} v) 3.14
                         (#{:boolean :bool} v) true
                         (#{:map} v) {}
                         (#{:vector :list} v) []
                         (map? v) (schema->example v)
                         (vector? v) [(if (seq v) (schema->example (first v)) "example")]
                         :else "example")])
                  schema))

    (vector? schema)
    [(if (seq schema) (schema->example (first schema)) "example")]

    (keyword? schema)
    (case schema
      (:string :str) "example"
      (:int :integer) 42
      (:number :float :double) 3.14
      (:boolean :bool) true
      (:map) {}
      (:vector :list) []
      "example")

    :else {}))

(defn validate-schema [schema data]
  (if (map? schema)
    (every? (fn [[k _]] (contains? data k)) schema)
    true))

(defmacro defstructured
  "Define a structured output type."
  [name schema]
  (let [edn-fn-name (symbol (str "get-" (str/lower-case (str name))))
        json-fn-name (symbol (str "get-" (str/lower-case (str name)) "-json"))]
    `(do
       (defn ~edn-fn-name
         ([model# prompt#] (~edn-fn-name model# prompt# {}))
         ([model# prompt# options#]
          (structured-output model# prompt# (merge {:schema ~schema :output-format :edn} options#))))
       (defn ~json-fn-name
         ([model# prompt#] (~json-fn-name model# prompt# {}))
         ([model# prompt# options#]
          (structured-output model# prompt# (merge {:schema ~schema :output-format :json-str} options#)))))))

(defn edn-prompt [prompt schema]
  (str prompt "\n\nReturn the response as valid EDN matching this structure:\n" (pr-str (schema->example schema))))

(defn preserve-clojure-types [data]
  (walk/postwalk
   (fn [x]
     (cond
       (keyword? x) ^{:type :keyword} (name x)
       (symbol? x) ^{:type :symbol} (name x)
       (set? x) ^{:type :set} (vec x)
       :else x))
   data))

(defn restore-clojure-types [data]
  (walk/postwalk
   (fn [x]
     (if-let [type-meta (meta x)]
       (case (:type type-meta)
         :keyword (keyword x)
         :symbol (symbol x)
         :set (set x)
         x)
       x))
   data))

(comment
  (def model (core/create-model {:provider :openai :api-key (System/getenv "OPENAI_API_KEY")}))

  (structured-output model "Create a recipe"
                     {:schema {:name :string :ingredients [:vector :string] :steps [:vector :string]}})

  (chat-edn-mode model "Describe a person" :schema {:name :string :age :int}))

(s/fdef chat-json-mode
  :args (s/cat :model ::specs/chat-model :prompt string? :opts (s/keys* :opt-un [::specs/schema ::specs/output-format]))
  :ret (s/or :edn map? :json string?))

(s/fdef chat-edn-mode
  :args (s/cat :model ::specs/chat-model :prompt string? :opts (s/keys* :opt-un [::specs/schema]))
  :ret map?)

(s/fdef chat-with-output-tool
  :args (s/cat :model ::specs/chat-model :prompt string? :output-schema ::specs/schema :opts (s/keys* :opt-un [::specs/output-format]))
  :ret (s/or :edn map? :json string?))

(s/fdef chat-with-validation
  :args (s/cat :model ::specs/chat-model :prompt string? :schema ::specs/schema :opts (s/keys* :opt-un [::specs/max-attempts ::specs/output-format]))
  :ret (s/or :edn map? :json string?))

(s/fdef structured-output
  :args (s/cat :model ::specs/chat-model :prompt string? :options ::specs/structured-output-opts)
  :ret (s/or :edn map? :json string?))
