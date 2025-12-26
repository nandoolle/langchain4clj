(ns langchain4clj.memory-integration-test
  "Integration tests for memory with assistant."
  (:require [clojure.test :refer [deftest testing is]]
            [langchain4clj.memory :as mem]
            [langchain4clj.assistant :as asst])
  (:import [dev.langchain4j.data.message UserMessage SystemMessage AiMessage]
           [dev.langchain4j.model.chat ChatModel]
           [dev.langchain4j.model.output TokenUsage]))

;; Mock chat model for testing
(defn create-mock-model
  "Creates a mock ChatModel that returns canned responses with token usage."
  [responses]
  (let [call-count (atom 0)
        create-response (fn []
                          (let [response-text (nth responses @call-count "Mock response")
                                token-usage (TokenUsage. (int 50) (int 50))]
                            (swap! call-count inc)
                            (-> (dev.langchain4j.model.chat.response.ChatResponse/builder)
                                (.aiMessage (AiMessage. response-text))
                                (.tokenUsage token-usage)
                                (.build))))]
    (reify ChatModel
      (^String chat [_ ^String _message]
        (let [response-text (nth responses @call-count "Mock response")]
          (swap! call-count inc)
          response-text))

      (^dev.langchain4j.model.chat.response.ChatResponse chat [_ ^java.util.List _messages]
        (create-response))

      (^dev.langchain4j.model.chat.response.ChatResponse chat [_ ^dev.langchain4j.model.chat.request.ChatRequest _request]
        (create-response)))))

(deftest test-memory-with-assistant-basic
  (testing "Memory integrates with assistant"
    (let [memory (mem/create-memory {:max-messages 10})
          model (create-mock-model ["Hello!" "How are you?"])
          assistant (asst/create-assistant {:model model
                                            :memory memory})]

      ;; First interaction
      (assistant "Hi")
      (is (= 2 (:message-count (mem/stats memory))))  ; user + ai

      ;; Second interaction
      (assistant "How are you?")
      (is (= 4 (:message-count (mem/stats memory))))  ; 2 + user + ai

      ;; Verify messages persist
      (let [messages (mem/get-messages memory)]
        (is (= 4 (count messages)))
        (is (instance? UserMessage (first messages)))
        (is (instance? AiMessage (second messages)))))))

(deftest test-memory-with-token-tracking-integration
  (testing "Memory tracks tokens from ChatResponse"
    (let [memory (mem/create-memory)
          model (create-mock-model ["Response 1" "Response 2"])]

      ;; Simulate chat with token tracking
      (let [messages (java.util.ArrayList. [(UserMessage. "Hello")])
            response (.chat model messages)]
        (mem/add-message! memory (UserMessage. "Hello"))
        (mem/add-message! memory
                          (.aiMessage response)
                          {:token-usage (.tokenUsage response)}))

      (let [stats (mem/stats memory)]
        (is (= 2 (:message-count stats)))
        (is (= 100 (:token-count stats)))))))

(deftest test-auto-reset-with-assistant
  (testing "Auto-reset preserves context in assistant workflow"
    (let [ctx-msg (SystemMessage. "You are a helpful bot")
          memory (-> (mem/create-memory {:max-messages 5})
                     (mem/with-auto-reset {:max-messages 5
                                           :reset-threshold 0.8
                                           :context [ctx-msg]}))
          model (create-mock-model (repeat 10 "Response"))
          assistant (asst/create-assistant {:model model
                                            :memory memory})]

      ;; Add 4 interactions (8 messages total - will trigger reset at 80% of 5)
      (dotimes [_ 4]
        (assistant "Test"))

      ;; Should have reset and restored context
      (let [stats (mem/stats memory)]
        (is (< (:message-count stats) 8))
        ;; Context should be first message
        (is (= "You are a helpful bot"
               (.text (first (mem/get-messages memory)))))))))

(deftest test-stateless-with-assistant
  (testing "Stateless memory clears between sessions"
    (let [ctx-msg (SystemMessage. "Session context")
          memory (-> (mem/create-memory)
                     (mem/with-stateless-mode {:context [ctx-msg]}))
          model (create-mock-model (repeat 5 "Response"))
          assistant (asst/create-assistant {:model model
                                            :memory memory})]

      ;; First session
      (assistant "Question 1")
      (is (= 3 (:message-count (mem/stats memory))))  ; context + user + ai

      ;; Clear for new session
      (mem/clear! memory)

      ;; Second session - context auto-restored
      (assistant "Question 2")
      (is (= 3 (:message-count (mem/stats memory))))  ; context + user + ai
      (is (= "Session context" (.text (first (mem/get-messages memory))))))))

(deftest test-defmemory-with-assistant
  (testing "defmemory macro works with assistant"
    (mem/defmemory integration-test-memory
      :max-messages 20
      :auto-reset {:max-messages 20
                   :reset-threshold 0.9})

    (let [model (create-mock-model ["Response"])
          assistant (asst/create-assistant {:model model
                                            :memory integration-test-memory})]
      (assistant "Test")
      (is (= 2 (:message-count (mem/stats integration-test-memory)))))))

(deftest test-message-filtering-with-assistant
  (testing "Can filter messages from assistant interactions"
    (let [memory (mem/create-memory {:max-messages 10})
          model (create-mock-model ["AI 1" "AI 2"])
          assistant (asst/create-assistant {:model model
                                            :memory memory
                                            :system-message "System"})]

      (assistant "User 1")
      (assistant "User 2")

      ;; Filter only user messages
      (let [user-messages (mem/get-messages memory {:type UserMessage})]
        (is (= 2 (count user-messages))))

      ;; Filter only AI messages
      (let [ai-messages (mem/get-messages memory {:type AiMessage})]
        (is (= 2 (count ai-messages)))))))

(deftest test-memory-composition-with-assistant
  (testing "All strategies compose in real assistant usage"
    (let [ctx-msg (SystemMessage. "Context")
          memory (-> (mem/create-memory {:max-messages 10})
                     (mem/with-auto-reset {:max-messages 10
                                           :reset-threshold 0.85
                                           :context [ctx-msg]}))
          model (create-mock-model (repeat 10 "Response"))
          assistant (asst/create-assistant {:model model
                                            :memory memory})]

      ;; Use assistant multiple times
      (dotimes [_ 5]
        (assistant "Test"))

      ;; Memory should work correctly with all strategies
      (is (satisfies? mem/ChatMemory memory))
      (is (pos? (:message-count (mem/stats memory)))))))
