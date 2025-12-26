(ns langchain4clj.structured-test
  "Comprehensive tests for structured output system"
  (:require [clojure.test :refer [deftest is testing]]
            [langchain4clj.structured :as structured])
  (:import [dev.langchain4j.model.chat ChatModel]
           [dev.langchain4j.model.openai OpenAiChatModel]
           [dev.langchain4j.data.message AiMessage]
           [dev.langchain4j.model.chat.response ChatResponse]
           [dev.langchain4j.model.chat.request ChatRequest]
           [dev.langchain4j.agent.tool ToolExecutionRequest]
           [java.util ArrayList List]))

;; ============================================================================
;; Test Helpers
;; ============================================================================

(defn create-tool-request
  "Helper to create ToolExecutionRequest for tests"
  [name arguments]
  (-> (ToolExecutionRequest/builder)
      (.name name)
      (.arguments arguments)
      (.build)))

(defn create-ai-message
  "Helper to create AiMessage for tests"
  ([text]
   (create-ai-message text nil))
  ([text tool-execution-requests]
   (let [builder (AiMessage/builder)
         builder (if text (.text builder text) builder)
         builder (if tool-execution-requests (.toolExecutionRequests builder tool-execution-requests) builder)]
     (.build builder))))

(defn create-chat-response
  "Helper to create ChatResponse for tests"
  ^dev.langchain4j.model.chat.response.ChatResponse
  [ai-message]
  (-> (ChatResponse/builder)
      (.aiMessage ai-message)
      (.build)))

;; ============================================================================
;; JSON <-> EDN Conversion Tests
;; ============================================================================

(deftest test-edn-to-json-str
  (testing "Convert EDN map to JSON string"
    (let [edn-data {:name "Alice" :age 30}
          json-str (structured/edn->json-str edn-data)]

      (is (string? json-str))
      (is (re-find #"\"name\"\s*:\s*\"Alice\"" json-str))
      (is (re-find #"\"age\"\s*:\s*30" json-str))))

  (testing "Convert EDN vector to JSON string"
    (let [edn-data ["one" "two" "three"]
          json-str (structured/edn->json-str edn-data)]

      (is (string? json-str))
      (is (re-find #"\[" json-str))
      (is (re-find #"\"one\"" json-str))))

  (testing "Convert nested EDN to JSON"
    (let [edn-data {:person {:name "Bob" :contacts ["email" "phone"]}}
          json-str (structured/edn->json-str edn-data)]

      (is (string? json-str))
      (is (re-find #"\"name\"" json-str))
      (is (re-find #"\"contacts\"" json-str)))))

(deftest test-json-str-to-edn
  (testing "Convert JSON string to EDN map"
    (let [json-str "{\"name\":\"Alice\",\"age\":30}"
          edn-data (structured/json-str->edn json-str)]

      (is (map? edn-data))
      (is (= "Alice" (:name edn-data)))
      (is (= 30 (:age edn-data)))))

  (testing "Convert JSON array to EDN vector"
    (let [json-str "[\"one\",\"two\",\"three\"]"
          edn-data (structured/json-str->edn json-str)]

      (is (vector? edn-data))
      (is (= 3 (count edn-data)))
      (is (= "one" (first edn-data)))))

  (testing "Convert nested JSON to EDN"
    (let [json-str "{\"person\":{\"name\":\"Bob\",\"contacts\":[\"email\"]}}"
          edn-data (structured/json-str->edn json-str)]

      (is (map? edn-data))
      (is (map? (:person edn-data)))
      (is (= "Bob" (get-in edn-data [:person :name])))
      (is (vector? (get-in edn-data [:person :contacts]))))))

(deftest test-edn-json-round-trip
  (testing "Round-trip conversion preserves data"
    (let [original {:name "Test"
                    :items ["a" "b" "c"]
                    :nested {:key "value"}}
          json-str (structured/edn->json-str original)
          restored (structured/json-str->edn json-str)]

      (is (= (:name original) (:name restored)))
      (is (= (:items original) (:items restored)))
      (is (= (get-in original [:nested :key])
             (get-in restored [:nested :key]))))))

;; ============================================================================
;; Helper Function Tests
;; ============================================================================

(deftest test-supports-json-mode
  (testing "OpenAI model supports JSON mode"
    ;; Create an actual OpenAiChatModel instance for testing
    (let [model (OpenAiChatModel/builder)
          _ (.apiKey model "test-key")
          _ (.modelName model "gpt-3.5-turbo")
          built-model (.build model)]
      (is (structured/supports-json-mode? built-model))))

  (testing "Generic model returns false by default"
    (let [model (reify ChatModel
                  (^String chat [_ ^String _] "test"))]
      (is (false? (structured/supports-json-mode? model))))))

(deftest test-supports-tools
  (testing "Most models support tools"
    (let [model (reify ChatModel
                  (^String chat [_ ^String _] "test"))]
      (is (structured/supports-tools? model)))))

(deftest test-schema-to-example
  (testing "Generate example from simple schema"
    (let [schema {:name :string :age :int :active :boolean}
          example (structured/schema->example schema)]

      (is (map? example))
      (is (contains? example :name))
      (is (contains? example :age))
      (is (contains? example :active))
      (is (string? (:name example)))
      (is (number? (:age example)))
      (is (boolean? (:active example)))))

  (testing "Generate example from schema with nested map"
    (let [schema {:user :string :settings :map}
          example (structured/schema->example schema)]

      (is (map? example))
      (is (map? (:settings example)))))

  (testing "Generate example from schema with vector"
    (let [schema {:items :vector}
          example (structured/schema->example schema)]

      (is (vector? (:items example)))))

  (testing "Empty schema returns empty map"
    (is (= {} (structured/schema->example {})))))

(deftest test-validate-schema
  (testing "Valid data passes validation"
    (let [schema {:name :string :age :int}
          data {:name "Alice" :age 30}]

      (is (structured/validate-schema schema data))))

  (testing "Missing required field fails validation"
    (let [schema {:name :string :age :int}
          data {:name "Alice"}]

      (is (false? (structured/validate-schema schema data)))))

  (testing "Extra fields are allowed"
    (let [schema {:name :string}
          data {:name "Alice" :extra "field"}]

      (is (structured/validate-schema schema data))))

  (testing "Empty schema always validates"
    (is (structured/validate-schema {} {:anything "goes"}))))

;; ============================================================================
;; Chat with Output Tool Tests
;; ============================================================================

(deftest test-chat-with-output-tool-success
  (testing "Successfully extract structured output from tool call"
    (let [;; Mock tool execution request
          tool-request (create-tool-request "return_structured_data" "{\"name\":\"Alice\",\"age\":30}")
          tool-requests (doto (ArrayList.) (.add tool-request))

          ;; Mock AI message with tool call
          ai-message (create-ai-message nil tool-requests)

          ;; Mock ChatResponse
          response (create-chat-response ai-message)

          ;; Mock model that returns the response - implement all methods
          mock-model (reify ChatModel
                       (^String chat [_ ^String _]
                         "Alice")
                       (^ChatResponse chat [_ ^List _messages]
                         response)
                       (^ChatResponse chat [_ ^ChatRequest _request]
                         response)
                       (^ChatResponse chat [this ^"[Ldev.langchain4j.data.message.ChatMessage;" messages]
                         (let [msg-list (ArrayList. (seq messages))]
                           (.chat this msg-list))))

          schema {:name :string :age :int}
          result (structured/chat-with-output-tool mock-model
                                                   "Get user data"
                                                   schema)]

      (is (map? result))
      (is (= "Alice" (:name result)))
      (is (= 30 (:age result)))))

  (testing "Return JSON string when requested"
    (let [tool-request (create-tool-request "return_structured_data" "{\"result\":\"test\"}")
          tool-requests (doto (ArrayList.) (.add tool-request))
          ai-message (create-ai-message nil tool-requests)
          response (create-chat-response ai-message)
          mock-model (reify ChatModel
                       (^String chat [_ ^String _]
                         "test")
                       (^ChatResponse chat [_ ^List _messages]
                         response)
                       (^ChatResponse chat [_ ^ChatRequest _request]
                         response)
                       (^ChatResponse chat [this ^"[Ldev.langchain4j.data.message.ChatMessage;" messages]
                         (let [msg-list (ArrayList. (seq messages))]
                           (.chat this msg-list))))

          result (structured/chat-with-output-tool mock-model
                                                   "Test"
                                                   {}
                                                   :output-format :json-str)]

      (is (string? result))
      (is (re-find #"result" result)))))

(deftest test-chat-with-output-tool-no-tool-call
  (testing "Throws when model doesn't use output tool"
    (let [;; Mock AI message WITHOUT tool calls
          ai-message (create-ai-message "Just a regular response" nil)
          response (create-chat-response ai-message)

          ;; Use a complete mock that implements all ChatModel methods
          mock-model (reify ChatModel
                       (^String chat [_ ^String _]
                         (.text ai-message))
                       (^ChatResponse chat [_ ^List _messages]
                         response)
                       (^ChatResponse chat [_ ^ChatRequest _request]
                         response)
                       (^ChatResponse chat [this ^"[Ldev.langchain4j.data.message.ChatMessage;" messages]
                         (let [msg-list (ArrayList. (seq messages))]
                           (.chat this msg-list))))]

      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"did not use the output tool"
           (structured/chat-with-output-tool mock-model
                                             "Test"
                                             {:name :string}))))))

;; ============================================================================
;; Chat with Validation Tests
;; ============================================================================

(deftest test-chat-with-validation-first-attempt
  (testing "Validation succeeds on first attempt"
    (let [call-count (atom 0)
          mock-model (reify ChatModel
                       (^String chat [_ ^String _prompt]
                         (swap! call-count inc)
                         "{\"name\":\"Alice\",\"age\":30}"))

          schema {:name :string :age :int}
          result (structured/chat-with-validation mock-model
                                                  "Get data"
                                                  schema
                                                  :output-format :json)]

      (is (= 1 @call-count))
      (is (map? result))
      (is (= "Alice" (:name result))))))

(deftest test-chat-with-validation-retry
  (testing "Validation retries on invalid output"
    (let [attempt-count (atom 0)
          mock-model (reify ChatModel
                       (^String chat [_ ^String _prompt]
                         (swap! attempt-count inc)
                         (if (< @attempt-count 3)
                           "invalid json {"
                           "{\"name\":\"Alice\"}")))

          schema {:name :string}
          result (structured/chat-with-validation mock-model
                                                  "Get data"
                                                  schema
                                                  :max-attempts 5
                                                  :output-format :json)]

      (is (= 3 @attempt-count))
      (is (map? result))
      (is (= "Alice" (:name result)))))

  (testing "Validation retries for EDN format"
    (let [attempt-count (atom 0)
          mock-model (reify ChatModel
                       (^String chat [_ ^String _prompt]
                         (swap! attempt-count inc)
                         (if (< @attempt-count 2)
                           "{invalid edn"
                           "{:name \"Bob\"}")))

          schema {:name :string}
          result (structured/chat-with-validation mock-model
                                                  "Get data"
                                                  schema
                                                  :max-attempts 5
                                                  :output-format :edn)]

      (is (= 2 @attempt-count))
      (is (map? result))
      (is (= "Bob" (:name result))))))

(deftest test-chat-with-validation-max-attempts
  (testing "Throws after max attempts"
    (let [mock-model (reify ChatModel
                       (^String chat [_ ^String _prompt]
                         "always invalid {"))

          schema {:name :string}]

      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"Could not get valid structured output"
           (structured/chat-with-validation mock-model
                                            "Get data"
                                            schema
                                            :max-attempts 2
                                            :output-format :json))))))

;; ============================================================================
;; defstructured Macro Tests
;; ============================================================================

(deftest test-defstructured-creates-functions
  (testing "defstructured creates EDN and JSON functions"
    ;; Define a test type
    (structured/defstructured Person
      {:name :string :age :int})

    ;; Check that functions were created
    (is (fn? get-person))
    (is (fn? get-person-json))))

(deftest test-defstructured-edn-function
  (testing "defstructured EDN function works"
    (structured/defstructured TestUser
      {:username :string})

    (let [tool-request (create-tool-request "return_structured_data" "{\"username\":\"alice\"}")
          tool-requests (doto (ArrayList.) (.add tool-request))
          ai-message (create-ai-message nil tool-requests)
          response (create-chat-response ai-message)
          mock-model (reify ChatModel
                       (^String chat [_ ^String _]
                         "alice")
                       (^ChatResponse chat [_ ^List _messages]
                         response)
                       (^ChatResponse chat [_ ^ChatRequest _request]
                         response)
                       (^ChatResponse chat [this ^"[Ldev.langchain4j.data.message.ChatMessage;" messages]
                         (let [msg-list (ArrayList. (seq messages))]
                           (.chat this msg-list))))

          result (get-testuser mock-model "Get user")]

      (is (map? result))
      (is (= "alice" (:username result))))))

(deftest test-defstructured-json-function
  (testing "defstructured JSON function returns string"
    (structured/defstructured TestProduct
      {:name :string :price :number})

    (let [tool-request (create-tool-request "return_structured_data" "{\"name\":\"Widget\",\"price\":9.99}")
          tool-requests (doto (ArrayList.) (.add tool-request))
          ai-message (create-ai-message nil tool-requests)
          response (create-chat-response ai-message)
          mock-model (reify ChatModel
                       (^String chat [_ ^String _]
                         "Widget")
                       (^ChatResponse chat [_ ^List _messages]
                         response)
                       (^ChatResponse chat [_ ^ChatRequest _request]
                         response)
                       (^ChatResponse chat [this ^"[Ldev.langchain4j.data.message.ChatMessage;" messages]
                         (let [msg-list (ArrayList. (seq messages))]
                           (.chat this msg-list))))

          result (get-testproduct-json mock-model "Get product")]

      (is (string? result))
      ;; NOTE: clj-kondo warning "Expected: string, received: nil" - false positive
      ;; The clj-kondo hook for defstructured returns nil as placeholder,
      ;; causing incorrect type inference. Safe to ignore - validated above.
      (is (re-find #"Widget" result)))))

;; ============================================================================
;; Integration Tests
;; ============================================================================

(deftest test-output-formats-consistency
  (testing "All output formats work consistently"
    (let [test-data {:result "test" :value 42}
          json-str (structured/edn->json-str test-data)
          edn-data (structured/json-str->edn json-str)]

      ;; JSON string format
      (is (string? json-str))

      ;; EDN format
      (is (map? edn-data))
      (is (= "test" (:result edn-data)))
      (is (= 42 (:value edn-data))))))

(deftest test-schema-validation-integration
  (testing "Schema validation works end-to-end"
    (let [schema {:name :string
                  :email :string
                  :age :int}

          ;; Valid data
          valid-data {:name "Alice"
                      :email "alice@example.com"
                      :age 30
                      :extra "field"}]

      (is (structured/validate-schema schema valid-data))

      ;; Invalid data (missing required field)
      (let [invalid-data {:name "Bob"}]
        (is (false? (structured/validate-schema schema invalid-data)))))))

(deftest test-complex-nested-schema
  (testing "Complex nested schemas work"
    (let [schema {:user {:name :string
                         :contacts [:vector :string]}
                  :metadata :map}
          example (structured/schema->example schema)]

      (is (map? example))
      (is (map? (:user example)))
      (is (map? (:metadata example))))))

(deftest test-error-handling
  (testing "Proper error messages on failures"
    (let [mock-model (reify ChatModel
                       (^String chat [_ ^String _prompt]
                         "not json at all"))
          schema {:name :string}]

      (is (thrown? clojure.lang.ExceptionInfo
                   (structured/chat-with-validation mock-model
                                                    "Test"
                                                    schema
                                                    :max-attempts 1
                                                    :output-format :json))))))

;; ============================================================================
;; Edge Cases
;; ============================================================================

(deftest test-empty-schema
  (testing "Empty schema works"
    (let [example (structured/schema->example {})]
      (is (= {} example)))

    (is (structured/validate-schema {} {}))
    (is (structured/validate-schema {} {:anything "goes"}))))

(deftest test-nil-handling
  (testing "Nil values in conversion"
    (let [data {:key nil}
          json-str (structured/edn->json-str data)]
      (is (string? json-str))
      (is (re-find #"null" json-str)))))

(deftest test-special-characters
  (testing "Special characters in strings"
    (let [data {:text "Hello \"World\" with 'quotes'"}
          json-str (structured/edn->json-str data)
          restored (structured/json-str->edn json-str)]

      (is (= (:text data) (:text restored))))))

(deftest test-numeric-types
  (testing "Different numeric types"
    (let [data {:int 42
                :float 3.14
                :bigdec 99.999M}
          json-str (structured/edn->json-str data)
          restored (structured/json-str->edn json-str)]

      (is (number? (:int restored)))
      (is (number? (:float restored))))))

(deftest test-boolean-values
  (testing "Boolean values preserve correctly"
    (let [data {:active true :deleted false}
          json-str (structured/edn->json-str data)
          restored (structured/json-str->edn json-str)]

      (is (true? (:active restored)))
      (is (false? (:deleted restored))))))

(deftest test-vector-and-list
  (testing "Vectors preserve their structure"
    (let [data {:items [1 2 3]}
          json-str (structured/edn->json-str data)
          restored (structured/json-str->edn json-str)]

      (is (vector? (:items restored)))
      (is (= 3 (count (:items restored)))))))
