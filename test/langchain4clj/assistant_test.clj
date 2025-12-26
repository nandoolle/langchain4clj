(ns langchain4clj.assistant-test
  "Comprehensive tests for the assistant system"
  (:require [clojure.test :refer [deftest is testing]]
            [langchain4clj.assistant :as assistant]
            [langchain4clj.tools :as tools]
            [langchain4clj.test-utils :as test-utils]
            [clojure.data.json :as json]
            [clojure.spec.alpha :as s])
  (:import [dev.langchain4j.data.message UserMessage SystemMessage AiMessage]
           [dev.langchain4j.model.chat.response ChatResponse]
           [dev.langchain4j.agent.tool ToolExecutionRequest]
           [java.util ArrayList]))

;; ============================================================================
;; Spec Definitions for Test Tools
;; ============================================================================

(s/def ::a number?)
(s/def ::b number?)
(s/def ::math-params (s/keys :req-un [::a ::b]))
(s/def ::no-params (s/keys))

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
;; Memory Management Tests
;; ============================================================================

(deftest test-create-memory
  (testing "Memory creation with default settings"
    (let [memory (assistant/create-memory {})]
      (is (satisfies? assistant/ChatMemory memory))
      (is (empty? (assistant/get-messages memory)))))

  (testing "Memory creation with max-messages"
    (let [memory (assistant/create-memory {:max-messages 5})]
      (is (satisfies? assistant/ChatMemory memory)))))

(deftest test-memory-add-message
  (testing "Adding messages to memory"
    (let [memory (assistant/create-memory {})
          msg (UserMessage. "Hello")]

      (assistant/add-message! memory msg)
      (is (= 1 (count (assistant/get-messages memory))))
      (is (= msg (first (assistant/get-messages memory))))))

  (testing "Adding multiple messages"
    (let [memory (assistant/create-memory {})
          msg1 (UserMessage. "First")
          msg2 (UserMessage. "Second")]

      (assistant/add-message! memory msg1)
      (assistant/add-message! memory msg2)

      (is (= 2 (count (assistant/get-messages memory))))
      (is (= msg1 (first (assistant/get-messages memory))))
      (is (= msg2 (second (assistant/get-messages memory)))))))

(deftest test-memory-max-messages
  (testing "Memory respects max-messages limit"
    (let [memory (assistant/create-memory {:max-messages 3})]

      (doseq [i (range 5)]
        (assistant/add-message! memory (UserMessage. (str "Message " i))))

      (is (= 3 (count (assistant/get-messages memory))))
      (is (= "Message 2" (.singleText ^UserMessage (first (assistant/get-messages memory)))))
      (is (= "Message 4" (.singleText ^UserMessage (last (assistant/get-messages memory))))))))

(deftest test-memory-clear
  (testing "Clearing memory"
    (let [memory (assistant/create-memory {})]
      (assistant/add-message! memory (UserMessage. "Test"))
      (is (= 1 (count (assistant/get-messages memory))))

      (assistant/clear! memory)
      (is (empty? (assistant/get-messages memory))))))

(deftest test-memory-get-messages
  (testing "Getting messages returns vector"
    (let [memory (assistant/create-memory {})]
      (is (vector? (assistant/get-messages memory)))

      (assistant/add-message! memory (UserMessage. "Test"))
      (is (vector? (assistant/get-messages memory))))))

;; ============================================================================
;; Template Processing Tests
;; ============================================================================

(deftest test-process-template-single-variable
  (testing "Template with single variable"
    (is (= "Hello World"
           (assistant/process-template "Hello {{name}}" {:name "World"}))))

  (testing "Template with keyword variable"
    (is (= "Hello Clojure"
           (assistant/process-template "Hello {{lang}}" {:lang "Clojure"})))))

(deftest test-process-template-multiple-variables
  (testing "Template with multiple variables"
    (is (= "Hello Alice, you are 30 years old"
           (assistant/process-template
            "Hello {{name}}, you are {{age}} years old"
            {:name "Alice" :age 30})))))

(deftest test-process-template-no-variables
  (testing "Template without variables"
    (is (= "Hello World"
           (assistant/process-template "Hello World" {})))))

(deftest test-process-template-missing-variable
  (testing "Template with missing variable (placeholder remains)"
    (is (= "Hello {{missing}}"
           (assistant/process-template "Hello {{missing}}" {})))))

(deftest test-process-template-repeated-variable
  (testing "Template with repeated variable"
    (is (= "test and test"
           (assistant/process-template "{{word}} and {{word}}" {:word "test"})))))

(deftest test-process-template-numeric-values
  (testing "Template with numeric values"
    (is (= "The answer is 42"
           (assistant/process-template "The answer is {{num}}" {:num 42})))))

;; ============================================================================
;; Tool Execution Tests
;; ============================================================================

(deftest test-execute-tool-calls-found
  (testing "Execute tool calls when tools are found"
    (let [calculator (tools/create-tool
                      {:name "add"
                       :description "Adds numbers"
                       :params-schema ::math-params
                       :fn (fn [{:keys [a b]}] (+ a b))})

          ;; Mock ToolExecutionRequest
          request (create-tool-request "add" "{\"a\":2,\"b\":3}")

          results (assistant/execute-tool-calls [request] [calculator])]

      (is (= 1 (count results)))
      (is (instance? dev.langchain4j.data.message.ToolExecutionResultMessage
                     (first results))))))

(deftest test-execute-tool-calls-not-found
  (testing "Execute tool calls when tool not found"
    (let [request (create-tool-request "nonexistent" "{}")

          results (assistant/execute-tool-calls [request] [])]

      (is (= 1 (count results)))
      ;; Result should contain error message
      (is (instance? dev.langchain4j.data.message.ToolExecutionResultMessage
                     (first results))))))

(deftest test-execute-tool-calls-multiple-tools
  (testing "Execute multiple tool calls"
    (let [add-tool (tools/create-tool
                    {:name "add"
                     :description "Adds"
                     :params-schema ::math-params
                     :fn (fn [{:keys [a b]}] (+ a b))})

          mul-tool (tools/create-tool
                    {:name "multiply"
                     :description "Multiplies"
                     :params-schema ::math-params
                     :fn (fn [{:keys [a b]}] (* a b))})

          request1 (create-tool-request "add" "{\"a\":2,\"b\":3}")

          request2 (create-tool-request "multiply" "{\"a\":4,\"b\":5}")

          results (assistant/execute-tool-calls [request1 request2]
                                                [add-tool mul-tool])]

      (is (= 2 (count results))))))

;; ============================================================================
;; Chat with Tools Tests
;; ============================================================================

(deftest test-chat-with-tools-no-tool-needed
  (testing "Chat without tool execution"
    (let [mock-model (test-utils/create-java-mock-chat-model "Simple response")

          result (assistant/chat-with-tools
                  {:model mock-model
                   :messages [(UserMessage. "Hello")]
                   :tools []
                   :max-iterations 10})]

      (is (= "Simple response" (:result result)))
      (is (= 2 (count (:messages result)))))))

(deftest test-chat-with-tools-max-iterations
  (testing "Chat reaches max iterations"
    (let [iteration-count (atom 0)
          mock-model (test-utils/create-java-mock-chat-model-with-response
                      (fn [_messages]
                        (swap! iteration-count inc)
                        (let [;; Always request a tool to trigger loop
                              tool-req (create-tool-request "test" "{}")
                              requests (doto (ArrayList.) (.add tool-req))]
                          (create-chat-response (create-ai-message "Need tools" requests)))))

          test-tool (tools/create-tool
                     {:name "test"
                      :description "Test"
                      :params-schema ::no-params
                      :fn (fn [_] "result")})

          result (assistant/chat-with-tools
                  {:model mock-model
                   :messages [(UserMessage. "Test")]
                   :tools [test-tool]
                   :max-iterations 3})]

      (is (contains? result :error))
      (is (= "Max iterations reached" (:error result)))
      (is (>= @iteration-count 3)))))

;; ============================================================================
;; Create Assistant Tests
;; ============================================================================

(deftest test-create-assistant-basic
  (testing "Create basic assistant"
    (let [mock-model (test-utils/create-java-mock-chat-model "Response")

          assistant-fn (assistant/create-assistant {:model mock-model})]

      (is (fn? assistant-fn))
      (is (= "Response" (assistant-fn "Test"))))))

(deftest test-create-assistant-with-memory
  (testing "Assistant retains conversation in memory"
    (let [mock-model (test-utils/create-java-mock-chat-model
                      (fn [_] "Received message"))

          memory (assistant/create-memory {:max-messages 10})
          assistant-fn (assistant/create-assistant {:model mock-model
                                                    :memory memory})]

      ;; First call
      (assistant-fn "First message")
      (is (= 2 (count (assistant/get-messages memory))))

      ;; Second call
      (assistant-fn "Second message")
      (is (= 4 (count (assistant/get-messages memory)))))))

(deftest test-create-assistant-with-tools
  (testing "Assistant with tools"
    (let [tool-executed (atom false)
          mock-model (test-utils/create-java-mock-chat-model "Tool response")

          test-tool (tools/create-tool
                     {:name "test-tool"
                      :description "Test"
                      :params-schema ::no-params
                      :fn (fn [_]
                            (reset! tool-executed true)
                            "result")})

          assistant-fn (assistant/create-assistant {:model mock-model
                                                    :tools [test-tool]})]

      (assistant-fn "Use tool")
      (is (fn? assistant-fn)))))

(deftest test-create-assistant-with-system-message
  (testing "Assistant with static string system message"
    (let [mock-model (test-utils/create-java-mock-chat-model "Response")
          memory (assistant/create-memory {:max-messages 10})
          assistant-fn (assistant/create-assistant
                        {:model mock-model
                         :memory memory
                         :system-message "You are helpful"})]

      (assistant-fn "Test")
      ;; Verify system message is in memory
      (let [messages (assistant/get-messages memory)]
        (is (= 3 (count messages))) ;; system + user + ai
        (is (instance? SystemMessage (first messages)))
        (is (= "You are helpful" (.text ^SystemMessage (first messages)))))))

  (testing "Assistant with function system message"
    (let [mock-model (test-utils/create-java-mock-chat-model "Response")
          memory (assistant/create-memory {:max-messages 10})
          system-fn (fn [{:keys [user-input]}]
                      (str "Help the user with: " user-input))
          assistant-fn (assistant/create-assistant
                        {:model mock-model
                         :memory memory
                         :system-message system-fn})]

      (assistant-fn "coding task")
      ;; Verify dynamic system message is in memory
      (let [messages (assistant/get-messages memory)]
        (is (= 3 (count messages)))
        (is (instance? SystemMessage (first messages)))
        (is (= "Help the user with: coding task" (.text ^SystemMessage (first messages)))))))

  (testing "System message function receives template-vars"
    (let [mock-model (test-utils/create-java-mock-chat-model "Response")
          memory (assistant/create-memory {:max-messages 10})
          received-ctx (atom nil)
          system-fn (fn [ctx]
                      (reset! received-ctx ctx)
                      "Dynamic system")
          assistant-fn (assistant/create-assistant
                        {:model mock-model
                         :memory memory
                         :system-message system-fn})]

      (assistant-fn "test input" {:template-vars {:lang "clojure"}})
      ;; Verify context was passed correctly
      (is (= "test input" (:user-input @received-ctx)))
      (is (= {:lang "clojure"} (:template-vars @received-ctx)))))

  (testing "System message function returning nil adds no message"
    (let [mock-model (test-utils/create-java-mock-chat-model "Response")
          memory (assistant/create-memory {:max-messages 10})
          system-fn (fn [_] nil) ;; Returns nil
          assistant-fn (assistant/create-assistant
                        {:model mock-model
                         :memory memory
                         :system-message system-fn})]

      (assistant-fn "test")
      ;; Verify no system message in memory (only user + ai)
      (let [messages (assistant/get-messages memory)]
        (is (= 2 (count messages)))
        (is (instance? UserMessage (first messages))))))

  (testing "System message only added once (not on every call)"
    (let [call-count (atom 0)
          mock-model (test-utils/create-java-mock-chat-model "Response")
          memory (assistant/create-memory {:max-messages 20})
          system-fn (fn [_ctx]
                      (swap! call-count inc)
                      (str "Call " @call-count))
          assistant-fn (assistant/create-assistant
                        {:model mock-model
                         :memory memory
                         :system-message system-fn})]

      (assistant-fn "first")
      (assistant-fn "second")
      (assistant-fn "third")

      ;; System function called only once
      (is (= 1 @call-count))
      ;; Memory has: 1 system + 3 user + 3 ai = 7
      (is (= 7 (count (assistant/get-messages memory))))
      ;; System message has value from first call
      (is (= "Call 1" (.text ^SystemMessage (first (assistant/get-messages memory)))))))

  (testing "System message re-added after clear-memory"
    (let [call-count (atom 0)
          mock-model (test-utils/create-java-mock-chat-model "Response")
          memory (assistant/create-memory {:max-messages 20})
          system-fn (fn [{:keys [user-input]}]
                      (swap! call-count inc)
                      (str "Helping with: " user-input))
          assistant-fn (assistant/create-assistant
                        {:model mock-model
                         :memory memory
                         :system-message system-fn})]

      (assistant-fn "task1")
      (is (= 1 @call-count))
      (is (= "Helping with: task1" (.text ^SystemMessage (first (assistant/get-messages memory)))))

      ;; Clear and call again
      (assistant-fn "task2" {:clear-memory? true})
      (is (= 2 @call-count))
      (is (= "Helping with: task2" (.text ^SystemMessage (first (assistant/get-messages memory))))))))

(deftest test-create-assistant-template-vars
  (testing "Assistant with template variables"
    (let [mock-model (test-utils/create-java-mock-chat-model "Response")
          assistant-fn (assistant/create-assistant {:model mock-model})]

      (assistant-fn "Hello {{name}}" {:template-vars {:name "World"}})
      ;; Just verify template processing works
      (is (fn? assistant-fn)))))

(deftest test-create-assistant-clear-memory
  (testing "Assistant with clear-memory option"
    (let [mock-model (test-utils/create-java-mock-chat-model "Response")

          memory (assistant/create-memory {})
          assistant-fn (assistant/create-assistant {:model mock-model
                                                    :memory memory})]

      ;; First call
      (assistant-fn "First")
      (is (= 2 (count (assistant/get-messages memory))))

      ;; Second call with clear
      (assistant-fn "Second" {:clear-memory? true})
      (is (= 2 (count (assistant/get-messages memory)))))))

(deftest test-create-assistant-custom-max-iterations
  (testing "Assistant with custom max-iterations"
    (let [mock-model (test-utils/create-java-mock-chat-model "Response")

          assistant-fn (assistant/create-assistant {:model mock-model
                                                    :max-iterations 5})]

      (is (fn? assistant-fn)))))

(deftest test-create-assistant-requires-model
  (testing "Assistant creation requires model"
    (is (thrown? AssertionError
                 (assistant/create-assistant {})))))

;; ============================================================================
;; Structured Output Support Tests
;; ============================================================================

(deftest test-with-structured-output
  (testing "Wrapping assistant with parser"
    (let [base-assistant (fn [_input] "name: Alice, age: 30")
          parser (fn [response]
                   {:name (re-find #"name: (\w+)" response)
                    :age (Integer/parseInt
                          (second (re-find #"age: (\d+)" response)))})

          structured-assistant (assistant/with-structured-output
                                 base-assistant
                                 parser)]

      (is (fn? structured-assistant))
      (let [result (structured-assistant "Get user")]
        (is (map? result))))))

(deftest test-with-structured-output-json-parser
  (testing "Structured output with JSON parser"
    (let [base-assistant (fn [_input] "{\"result\": 42}")
          parser (fn [response] (json/read-str response :key-fn keyword))

          structured-assistant (assistant/with-structured-output
                                 base-assistant
                                 parser)
          result (structured-assistant "Get data")]

      (is (= 42 (:result result))))))

;; ============================================================================
;; Integration Tests
;; ============================================================================

(deftest test-full-assistant-workflow
  (testing "Complete assistant workflow with memory and tools"
    (let [call-count (atom 0)
          mock-model (test-utils/create-java-mock-chat-model
                      (fn [_]
                        (swap! call-count inc)
                        (str "Response " @call-count)))

          memory (assistant/create-memory {:max-messages 10})
          test-tool (tools/create-tool
                     {:name "helper"
                      :description "Helps"
                      :params-schema ::no-params
                      :fn (fn [_] "helped")})

          assistant-fn (assistant/create-assistant
                        {:model mock-model
                         :memory memory
                         :tools [test-tool]
                         :system-message "Be helpful"
                         :max-iterations 5})]

      ;; First conversation turn
      (let [r1 (assistant-fn "Hello")]
        (is (= "Response 1" r1)))

      ;; Second turn - memory should have context
      (let [r2 (assistant-fn "Continue")]
        (is (= "Response 2" r2)))

      ;; Check memory has: system message + 2 user messages + 2 AI responses = 5
      (is (= 5 (count (assistant/get-messages memory))))

      ;; Use template
      (let [r3 (assistant-fn "Greet {{person}}"
                             {:template-vars {:person "Alice"}})]
        (is (string? r3)))

      ;; Clear and start fresh - system message will be re-added on next call
      (let [_r4 (assistant-fn "New conversation" {:clear-memory? true})]
        ;; After clear: system message + 1 user + 1 AI = 3
        (is (= 3 (count (assistant/get-messages memory))))))))

(deftest test-assistant-arity-variations
  (testing "Assistant function works with different arities"
    (let [mock-model (test-utils/create-java-mock-chat-model "Response")

          assistant-fn (assistant/create-assistant {:model mock-model})]

      ;; 1-arity
      (is (= "Response" (assistant-fn "Test")))

      ;; 2-arity with empty options
      (is (= "Response" (assistant-fn "Test" {})))

      ;; 2-arity with options
      (is (= "Response" (assistant-fn "Test" {:clear-memory? false}))))))
