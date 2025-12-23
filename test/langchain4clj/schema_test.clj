(ns langchain4clj.schema-test
  (:require [clojure.test :refer [deftest is testing]]
            [langchain4clj.schema :as schema])
  (:import [dev.langchain4j.model.chat.request.json
            JsonAnyOfSchema
            JsonArraySchema
            JsonBooleanSchema
            JsonEnumSchema
            JsonIntegerSchema
            JsonNumberSchema
            JsonObjectSchema
            JsonStringSchema]))

;; =============================================================================
;; Primitive Types Tests
;; =============================================================================

(deftest test-string-schema
  (testing "String schema conversion"
    (let [result (schema/edn->json-schema {:type :string})]
      (is (instance? JsonStringSchema result))))

  (testing "String schema with description"
    (let [result (schema/edn->json-schema {:type :string
                                           :description "User's name"})]
      (is (instance? JsonStringSchema result))
      (is (= "User's name" (.description result)))))

  (testing "String type as string (not keyword)"
    (let [result (schema/edn->json-schema {:type "string"})]
      (is (instance? JsonStringSchema result)))))

(deftest test-number-schema
  (testing "Number schema conversion"
    (let [result (schema/edn->json-schema {:type :number})]
      (is (instance? JsonNumberSchema result))))

  (testing "Number schema with description"
    (let [result (schema/edn->json-schema {:type :number
                                           :description "Price in dollars"})]
      (is (instance? JsonNumberSchema result))
      (is (= "Price in dollars" (.description result))))))

(deftest test-integer-schema
  (testing "Integer schema conversion"
    (let [result (schema/edn->json-schema {:type :integer})]
      (is (instance? JsonIntegerSchema result))))

  (testing "Integer schema with description"
    (let [result (schema/edn->json-schema {:type :integer
                                           :description "User's age"})]
      (is (instance? JsonIntegerSchema result))
      (is (= "User's age" (.description result))))))

(deftest test-boolean-schema
  (testing "Boolean schema conversion"
    (let [result (schema/edn->json-schema {:type :boolean})]
      (is (instance? JsonBooleanSchema result))))

  (testing "Boolean schema with description"
    (let [result (schema/edn->json-schema {:type :boolean
                                           :description "Is active"})]
      (is (instance? JsonBooleanSchema result))
      (is (= "Is active" (.description result))))))

;; =============================================================================
;; Enum Tests
;; =============================================================================

(deftest test-enum-schema
  (testing "Enum schema conversion"
    (let [result (schema/edn->json-schema {:enum ["low" "medium" "high"]})]
      (is (instance? JsonEnumSchema result))
      (is (= ["low" "medium" "high"] (vec (.enumValues result))))))

  (testing "Enum schema with description"
    (let [result (schema/edn->json-schema {:enum ["a" "b"]
                                           :description "Priority level"})]
      (is (instance? JsonEnumSchema result))
      (is (= "Priority level" (.description result)))))

  (testing "Enum requires string values"
    (is (thrown? AssertionError
                 (schema/edn->json-schema {:enum [1 2 3]}))))

  (testing "Enum requires at least one value"
    (is (thrown? AssertionError
                 (schema/edn->json-schema {:enum []})))))

;; =============================================================================
;; Array Tests
;; =============================================================================

(deftest test-array-schema
  (testing "Array of strings"
    (let [result (schema/edn->json-schema {:type :array
                                           :items {:type :string}})]
      (is (instance? JsonArraySchema result))
      (is (instance? JsonStringSchema (.items result)))))

  (testing "Array of integers"
    (let [result (schema/edn->json-schema {:type :array
                                           :items {:type :integer}})]
      (is (instance? JsonArraySchema result))
      (is (instance? JsonIntegerSchema (.items result)))))

  (testing "Array with description"
    (let [result (schema/edn->json-schema {:type :array
                                           :items {:type :string}
                                           :description "List of tags"})]
      (is (instance? JsonArraySchema result))
      (is (= "List of tags" (.description result)))))

  (testing "Nested arrays"
    (let [result (schema/edn->json-schema {:type :array
                                           :items {:type :array
                                                   :items {:type :number}}})]
      (is (instance? JsonArraySchema result))
      (is (instance? JsonArraySchema (.items result)))))

  (testing "Array requires items"
    (is (thrown? AssertionError
                 (schema/edn->json-schema {:type :array})))))

;; =============================================================================
;; Object Tests
;; =============================================================================

(deftest test-object-schema
  (testing "Simple object"
    (let [result (schema/edn->json-schema
                  {:type :object
                   :properties {:name {:type :string}}})]
      (is (instance? JsonObjectSchema result))))

  (testing "Object with multiple properties"
    (let [result (schema/edn->json-schema
                  {:type :object
                   :properties {:name {:type :string}
                                :age {:type :integer}
                                :active {:type :boolean}}})]
      (is (instance? JsonObjectSchema result))))

  (testing "Object with required fields"
    (let [result (schema/edn->json-schema
                  {:type :object
                   :properties {:name {:type :string}
                                :email {:type :string}}
                   :required [:name :email]})]
      (is (instance? JsonObjectSchema result))
      (is (= #{"name" "email"} (set (.required result))))))

  (testing "Object with description"
    (let [result (schema/edn->json-schema
                  {:type :object
                   :description "User information"
                   :properties {:name {:type :string}}})]
      (is (instance? JsonObjectSchema result))
      (is (= "User information" (.description result)))))

  (testing "Nested objects"
    (let [result (schema/edn->json-schema
                  {:type :object
                   :properties {:user {:type :object
                                       :properties {:name {:type :string}}}}})]
      (is (instance? JsonObjectSchema result)))))

;; =============================================================================
;; Mixed Types Tests
;; =============================================================================

(deftest test-mixed-types-schema
  (testing "Nullable string"
    (let [result (schema/edn->json-schema {:type [:string :null]})]
      (is (instance? JsonAnyOfSchema result))))

  (testing "String or number"
    (let [result (schema/edn->json-schema {:type [:string :number]})]
      (is (instance? JsonAnyOfSchema result))))

  (testing "Mixed types with description"
    (let [result (schema/edn->json-schema {:type [:string :integer]
                                           :description "ID can be string or int"})]
      (is (instance? JsonAnyOfSchema result))
      (is (= "ID can be string or int" (.description result)))))

  (testing "Array in mixed types"
    (let [result (schema/edn->json-schema {:type [:array :null]})]
      (is (instance? JsonAnyOfSchema result))))

  (testing "Object in mixed types"
    (let [result (schema/edn->json-schema {:type [:object :null]})]
      (is (instance? JsonAnyOfSchema result)))))

;; =============================================================================
;; any-type-schema Tests
;; =============================================================================

(deftest test-any-type-schema
  (testing "any-type-schema creates JsonAnyOfSchema"
    (let [result (schema/any-type-schema)]
      (is (instance? JsonAnyOfSchema result)))))

;; =============================================================================
;; json-string->json-schema Tests
;; =============================================================================

(deftest test-json-string-conversion
  (testing "Simple JSON string"
    (let [result (schema/json-string->json-schema
                  "{\"type\": \"string\"}")]
      (is (instance? JsonStringSchema result))))

  (testing "Complex JSON string"
    (let [result (schema/json-string->json-schema
                  "{\"type\": \"object\", 
                    \"properties\": {
                      \"name\": {\"type\": \"string\"},
                      \"age\": {\"type\": \"integer\"}
                    },
                    \"required\": [\"name\"]}")]
      (is (instance? JsonObjectSchema result))
      (is (= #{"name"} (set (.required result)))))))

;; =============================================================================
;; schema-for-tool Tests
;; =============================================================================

(deftest test-schema-for-tool
  (testing "All properties required by default"
    (let [result (schema/schema-for-tool
                  {:location {:type :string}
                   :units {:type :string}})]
      (is (instance? JsonObjectSchema result))
      (is (= 2 (count (.required result))))))

  (testing "With optional properties"
    (let [result (schema/schema-for-tool
                  {:location {:type :string}
                   :units {:type :string}}
                  {:optional [:units]})]
      (is (instance? JsonObjectSchema result))
      (is (= #{"location"} (set (.required result))))))

  (testing "With description"
    (let [result (schema/schema-for-tool
                  {:query {:type :string}}
                  {:description "Search parameters"})]
      (is (instance? JsonObjectSchema result))
      (is (= "Search parameters" (.description result))))))

;; =============================================================================
;; Error Handling Tests
;; =============================================================================

(deftest test-error-handling
  (testing "Unknown schema type throws"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
                          #"Cannot determine schema type"
                          (schema/edn->json-schema {:unknown "field"})))))

;; =============================================================================
;; Real-World Use Cases
;; =============================================================================

(deftest test-weather-tool-schema
  (testing "Weather API tool schema"
    (let [result (schema/edn->json-schema
                  {:type :object
                   :properties {:location {:type :string
                                           :description "City name, e.g., 'San Francisco'"}
                                :units {:type :string
                                        :description "Temperature units: 'celsius' or 'fahrenheit'"}}
                   :required [:location]})]
      (is (instance? JsonObjectSchema result))
      (is (= #{"location"} (set (.required result)))))))

(deftest test-search-tool-schema
  (testing "Search tool schema with complex parameters"
    (let [result (schema/edn->json-schema
                  {:type :object
                   :properties {:query {:type :string
                                        :description "Search query"}
                                :filters {:type :object
                                          :properties {:date_from {:type :string}
                                                       :date_to {:type :string}
                                                       :categories {:type :array
                                                                    :items {:type :string}}}}
                                :limit {:type :integer
                                        :description "Max results to return"}}
                   :required [:query]})]
      (is (instance? JsonObjectSchema result)))))

(deftest test-mcp-tool-schema
  (testing "MCP-style tool definition"
    (let [;; This is how MCP tools define their input schemas
          mcp-schema {:type :object
                      :properties {:path {:type :string
                                          :description "File path to read"}
                                   :encoding {:type :string
                                              :description "File encoding (default: utf-8)"}}
                      :required [:path]}
          result (schema/edn->json-schema mcp-schema)]
      (is (instance? JsonObjectSchema result))
      (is (= #{"path"} (set (.required result)))))))
