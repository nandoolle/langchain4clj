(ns langchain4clj.schema
  "Convert JSON Schema (EDN format) to LangChain4j JsonSchema objects.
   
   This namespace provides functions to convert standard JSON Schema
   represented as Clojure EDN data into LangChain4j's JsonSchema objects,
   which are used for tool parameter definitions.
   
   ## Why use this?
   
   LangChain4j requires JsonSchema objects for tool parameters, but building
   them with Java builders is verbose. This namespace lets you define schemas
   using standard JSON Schema syntax in EDN:
   
   ```clojure
   ;; Instead of this Java-style code:
   (-> (JsonObjectSchema/builder)
       (.addProperty \"name\" (-> (JsonStringSchema/builder) (.build)))
       (.addProperty \"age\" (-> (JsonIntegerSchema/builder) (.build)))
       (.required [\"name\"])
       (.build))
   
   ;; Write this:
   (edn->json-schema
     {:type :object
      :properties {:name {:type :string}
                   :age {:type :integer}}
      :required [:name]})
   ```
   
   ## Supported Types
   
   - `:string` - String values
   - `:number` - Floating point numbers
   - `:integer` - Integer numbers
   - `:boolean` - Boolean values
   - `:array` - Arrays (requires `:items`)
   - `:object` - Objects (with `:properties` and optional `:required`)
   - `:enum` - Enumerated string values
   - Mixed types - Vector of types like `[:string :null]`
   
   ## Use Cases
   
   - Defining tool parameters for function calling
   - MCP (Model Context Protocol) tool definitions
   - Converting external JSON Schema to LangChain4j format"
  (:require
   [clojure.data.json :as json])
  (:import
   [dev.langchain4j.model.chat.request.json
    JsonAnyOfSchema
    JsonArraySchema
    JsonBooleanSchema
    JsonEnumSchema
    JsonIntegerSchema
    JsonNumberSchema
    JsonObjectSchema
    JsonStringSchema]))

;; =============================================================================
;; Multimethod for Schema Conversion
;; =============================================================================

(defmulti edn->json-schema
  "Convert JSON Schema EDN to LangChain4j JsonSchema.
   
   Dispatches on the schema type:
   - Keyword type (`:string`, `:number`, etc.)
   - Vector of types for mixed/nullable types
   - `:enum` key for enumerated values
   
   Examples:
   ```clojure
   ;; Simple types
   (edn->json-schema {:type :string})
   (edn->json-schema {:type :integer :description \"User's age\"})
   
   ;; Arrays
   (edn->json-schema {:type :array :items {:type :string}})
   
   ;; Objects
   (edn->json-schema
     {:type :object
      :description \"User information\"
      :properties {:name {:type :string}
                   :email {:type :string}}
      :required [:name :email]})
   
   ;; Enums
   (edn->json-schema {:enum [\"low\" \"medium\" \"high\"]})
   
   ;; Nullable/mixed types
   (edn->json-schema {:type [:string :null]})
   ```"
  (fn [{:keys [type enum]}]
    (cond
      ;; Vector of types = mixed/anyOf
      (vector? type) :mixed-types
      ;; Keyword or string type
      (and type (keyword? type)) type
      (and type (string? type)) (keyword type)
      ;; Enum without type
      enum :enum
      ;; Fallback
      :else (throw (ex-info "Cannot determine schema type"
                            {:schema-keys (keys {:type type :enum enum})
                             :type type
                             :enum enum})))))

;; =============================================================================
;; Helper Functions
;; =============================================================================

(defn- normalize-type
  "Normalize type to keyword. Handles string or keyword input."
  [t]
  (if (keyword? t) t (keyword t)))

(defn any-type-schema
  "Creates a schema that accepts any JSON type (except null).
   Useful for dynamic/unknown value types."
  []
  (-> (JsonAnyOfSchema/builder)
      (.anyOf [(.build (JsonStringSchema/builder))
               (.build (JsonNumberSchema/builder))
               (.build (JsonBooleanSchema/builder))
               (.build (JsonObjectSchema/builder))
               (-> (JsonArraySchema/builder)
                   (.items (.build (JsonStringSchema/builder)))
                   .build)])
      .build))

;; =============================================================================
;; Type Implementations
;; =============================================================================

(defmethod edn->json-schema :string
  [{:keys [description]}]
  (cond-> (JsonStringSchema/builder)
    description (.description description)
    :always (.build)))

(defmethod edn->json-schema :number
  [{:keys [description]}]
  (cond-> (JsonNumberSchema/builder)
    description (.description description)
    :always (.build)))

(defmethod edn->json-schema :integer
  [{:keys [description]}]
  (cond-> (JsonIntegerSchema/builder)
    description (.description description)
    :always (.build)))

(defmethod edn->json-schema :boolean
  [{:keys [description]}]
  (cond-> (JsonBooleanSchema/builder)
    description (.description description)
    :always (.build)))

(defmethod edn->json-schema :enum
  [{:keys [enum description]}]
  (assert (seq enum) "Enum must have at least one value")
  (assert (every? string? enum) "Enum values must be strings")
  (cond-> (JsonEnumSchema/builder)
    description (.description description)
    :always (.enumValues (vec enum))
    :always (.build)))

(defmethod edn->json-schema :array
  [{:keys [items description]}]
  (assert items "Array schema requires :items")
  (cond-> (JsonArraySchema/builder)
    description (.description description)
    :always (.items (edn->json-schema items))
    :always (.build)))

(defmethod edn->json-schema :object
  [{:keys [properties description required]}]
  (let [builder (cond-> (JsonObjectSchema/builder)
                  description (.description description)
                  (seq required) (.required (mapv name required)))]
    ;; Add each property
    (doseq [[prop-name prop-schema] properties]
      (.addProperty builder (name prop-name) (edn->json-schema prop-schema)))
    (.build builder)))

(defmethod edn->json-schema :mixed-types
  [{:keys [type description]}]
  (let [schemas (keep (fn [t]
                        (let [t (normalize-type t)]
                          (case t
                            :null nil ; Skip null (LangChain4j doesn't have JsonNullSchema builder)
                            :object (.build (JsonObjectSchema/builder))
                            :array (-> (JsonArraySchema/builder)
                                       (.items (any-type-schema))
                                       .build)
                            ;; Recursively convert other types
                            (edn->json-schema {:type t}))))
                      type)]
    (cond-> (JsonAnyOfSchema/builder)
      description (.description description)
      :always (.anyOf (vec schemas))
      :always (.build))))

;; =============================================================================
;; Convenience Functions
;; =============================================================================

(defn json-string->json-schema
  "Parse a JSON string and convert to LangChain4j JsonSchema.
   
   Convenience function for working with JSON Schema from external sources
   like files, APIs, or MCP tool definitions.
   
   Example:
   ```clojure
   (json-string->json-schema
     \"{\\\"type\\\": \\\"object\\\", 
       \\\"properties\\\": {\\\"name\\\": {\\\"type\\\": \\\"string\\\"}}}\")
   ```"
  [json-string]
  (-> json-string
      (json/read-str :key-fn keyword)
      edn->json-schema))

(defn schema-for-tool
  "Create a JSON Schema for tool parameters from a simple map specification.
   
   This is a convenience function that wraps properties in an object schema.
   All properties are required by default unless `:optional` is specified.
   
   Example:
   ```clojure
   ;; Simple: all properties required
   (schema-for-tool
     {:location {:type :string :description \"City name\"}
      :units {:type :string :description \"Temperature units\"}})
   
   ;; With optional properties
   (schema-for-tool
     {:location {:type :string :description \"City name\"}
      :units {:type :string :description \"Temperature units\"}}
     {:optional [:units]})
   ```"
  ([properties]
   (schema-for-tool properties {}))
  ([properties {:keys [optional description]}]
   (let [required-props (if optional
                          (remove (set optional) (keys properties))
                          (keys properties))]
     (edn->json-schema
      (cond-> {:type :object
               :properties properties
               :required (vec required-props)}
        description (assoc :description description))))))
