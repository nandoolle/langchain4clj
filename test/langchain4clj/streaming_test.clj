(ns langchain4clj.streaming-test
  "Tests for streaming API"
  (:require [clojure.test :refer [deftest testing is]]
            [langchain4clj.streaming :as streaming])
  (:import [dev.langchain4j.model.chat StreamingChatModel]
           [dev.langchain4j.model.chat.response StreamingChatResponseHandler ChatResponse]
           [dev.langchain4j.data.message AiMessage]
           [dev.langchain4j.model.output TokenUsage]))

;; =============================================================================
;; Mock Utilities
;; =============================================================================

(defn create-mock-response
  "Creates a mock ChatResponse object for testing"
  [tokens]
  (let [full-text (apply str tokens)
        ai-message (AiMessage/from full-text)
        token-count (int (count tokens))
        token-usage (TokenUsage. token-count token-count token-count)]
    (-> (ChatResponse/builder)
        (.aiMessage ai-message)
        (.tokenUsage token-usage)
        (.build))))

(defn create-mock-streaming-model
  "Creates a mock StreamingChatModel that streams tokens with delay.
  
  Parameters:
  - tokens: Vector of token strings to stream
  - delay-ms: Delay between tokens in milliseconds (default 10ms)"
  ([tokens] (create-mock-streaming-model tokens 10))
  ([tokens delay-ms]
   (reify StreamingChatModel
     (^void chat [_ ^String _message ^StreamingChatResponseHandler handler]
       (future
         (try
           (doseq [token tokens]
             (Thread/sleep delay-ms)
             (.onPartialResponse handler token))
           (.onCompleteResponse handler (create-mock-response tokens))
           (catch Exception e
             (.onError handler e)))))
     (^void chat [this ^java.util.List messages ^StreamingChatResponseHandler handler]
       (.chat ^StreamingChatModel this (str (first messages)) handler)))))

(defn create-failing-streaming-model
  "Creates a mock model that fails with an exception"
  ([] (create-failing-streaming-model "Mock error"))
  ([error-message]
   (reify StreamingChatModel
     (^void chat [_ ^String _message ^StreamingChatResponseHandler handler]
       (future
         (Thread/sleep 10)
         (.onError handler (Exception. error-message))))
     (^void chat [this ^java.util.List messages ^StreamingChatResponseHandler handler]
       (.chat ^StreamingChatModel this (str (first messages)) handler)))))

(defn create-partial-failure-model
  "Creates a mock model that streams some tokens then fails"
  [tokens-before-error error-message]
  (reify StreamingChatModel
    (^void chat [_ ^String _message ^StreamingChatResponseHandler handler]
      (future
        (try
          (doseq [token tokens-before-error]
            (Thread/sleep 10)
            (.onPartialResponse handler token))
          (Thread/sleep 10)
          (.onError handler (Exception. error-message))
          (catch Exception e
            (.onError handler e)))))
    (^void chat [this ^java.util.List messages ^StreamingChatResponseHandler handler]
      (.chat ^StreamingChatModel this (str (first messages)) handler))))

;; =============================================================================
;; Unit Tests
;; =============================================================================

(deftest test-stream-chat-callbacks-order
  (testing "Callbacks are called in correct order"
    (let [tokens (atom [])
          completed? (atom false)
          mock-model (create-mock-streaming-model ["Hello" " " "World" "!"])]

      (streaming/stream-chat mock-model "test"
                             {:on-token (fn [token] (swap! tokens conj token))
                              :on-complete (fn [_] (reset! completed? true))})

      ;; Wait for async completion
      (Thread/sleep 100)

      (is (= ["Hello" " " "World" "!"] @tokens)
          "All tokens should be received in order")
      (is @completed?
          "on-complete should be called after all tokens"))))

(deftest test-stream-chat-accumulate
  (testing "Can accumulate tokens into full response"
    (let [accumulated (atom "")
          result (promise)
          mock-model (create-mock-streaming-model ["The" " " "quick" " " "fox"])]

      (streaming/stream-chat mock-model "test"
                             {:on-token (fn [token] (swap! accumulated str token))
                              :on-complete (fn [response]
                                             (deliver result {:text @accumulated
                                                              :response response}))})

      (Thread/sleep 100)
      (let [{:keys [text response]} @result]
        (is (= "The quick fox" text)
            "Accumulated text should match streamed tokens")
        (is (some? response)
            "Response object should be provided")))))

(deftest test-stream-chat-error-callback
  (testing "Error callback is called on exception"
    (let [error-msg (atom nil)
          error-received? (promise)
          mock-model (create-failing-streaming-model "Test error message")]

      (streaming/stream-chat mock-model "test"
                             {:on-token (fn [_] nil)
                              :on-error (fn [error]
                                          (reset! error-msg (.getMessage error))
                                          (deliver error-received? true))})

      (Thread/sleep 100)
      (is (= true @error-received?)
          "Error callback should be called")
      (is (= "Test error message" @error-msg)
          "Error message should be correct"))))

(deftest test-stream-chat-partial-failure
  (testing "Handles failure after receiving some tokens"
    (let [tokens (atom [])
          error-msg (atom nil)
          completed? (atom false)
          mock-model (create-partial-failure-model ["Hello" " " "World"] "Mid-stream error")]

      (streaming/stream-chat mock-model "test"
                             {:on-token (fn [token] (swap! tokens conj token))
                              :on-complete (fn [_] (reset! completed? true))
                              :on-error (fn [error] (reset! error-msg (.getMessage error)))})

      (Thread/sleep 100)

      (is (= ["Hello" " " "World"] @tokens)
          "Should receive tokens before error")
      (is (not @completed?)
          "on-complete should not be called on error")
      (is (= "Mid-stream error" @error-msg)
          "Should capture error message"))))

(deftest test-stream-chat-without-error-handler
  (testing "Works without optional on-error callback"
    (let [tokens (atom [])
          completed? (atom false)
          mock-model (create-mock-streaming-model ["Test"])]

      (streaming/stream-chat mock-model "test"
                             {:on-token (fn [token] (swap! tokens conj token))
                              :on-complete (fn [_] (reset! completed? true))})

      (Thread/sleep 50)

      (is (= ["Test"] @tokens))
      (is @completed?))))

(deftest test-stream-chat-without-complete-handler
  (testing "Works without optional on-complete callback"
    (let [tokens (atom [])
          mock-model (create-mock-streaming-model ["A" "B" "C"])]

      (streaming/stream-chat mock-model "test"
                             {:on-token (fn [token] (swap! tokens conj token))})

      (Thread/sleep 100)

      (is (= ["A" "B" "C"] @tokens)
          "Should receive all tokens even without on-complete"))))

(deftest test-stream-chat-requires-on-token
  (testing "Throws assertion error without on-token callback"
    (let [mock-model (create-mock-streaming-model ["Test"])]
      (is (thrown? AssertionError
                   (streaming/stream-chat mock-model "test"
                                          {:on-complete (fn [_] nil)}))
          "Should require on-token callback"))))

(deftest test-stream-chat-empty-tokens
  (testing "Handles empty token stream"
    (let [tokens (atom [])
          completed? (atom false)
          mock-model (create-mock-streaming-model [])]

      (streaming/stream-chat mock-model "test"
                             {:on-token (fn [token] (swap! tokens conj token))
                              :on-complete (fn [_] (reset! completed? true))})

      (Thread/sleep 50)

      (is (empty? @tokens)
          "Should handle empty token stream")
      (is @completed?
          "Should still call on-complete"))))

;; =============================================================================
;; Model Creation Tests
;; =============================================================================

(deftest test-create-streaming-model-openai
  (testing "Creates OpenAI streaming model with defaults"
    (let [model (streaming/create-streaming-model {:provider :openai
                                                   :api-key "test-key"})]
      (is (instance? dev.langchain4j.model.openai.OpenAiStreamingChatModel model)
          "Should create OpenAiStreamingChatModel instance"))))

(deftest test-create-streaming-model-openai-custom
  (testing "Creates OpenAI streaming model with custom config"
    (let [model (streaming/create-streaming-model {:provider :openai
                                                   :api-key "test-key"
                                                   :model "gpt-4"
                                                   :temperature 0.9
                                                   :max-tokens 100})]
      (is (instance? dev.langchain4j.model.openai.OpenAiStreamingChatModel model)))))

(deftest test-create-streaming-model-anthropic
  (testing "Creates Anthropic streaming model"
    (let [model (streaming/create-streaming-model {:provider :anthropic
                                                   :api-key "test-key"})]
      (is (instance? dev.langchain4j.model.anthropic.AnthropicStreamingChatModel model)
          "Should create AnthropicStreamingChatModel instance"))))

(deftest test-create-streaming-model-ollama
  (testing "Creates Ollama streaming model with defaults"
    (let [model (streaming/create-streaming-model {:provider :ollama})]
      (is (instance? dev.langchain4j.model.ollama.OllamaStreamingChatModel model)
          "Should create OllamaStreamingChatModel instance"))))

(deftest test-create-streaming-model-ollama-custom
  (testing "Creates Ollama streaming model with custom config"
    (let [model (streaming/create-streaming-model {:provider :ollama
                                                   :base-url "http://192.168.1.100:11434"
                                                   :model "mistral"
                                                   :temperature 0.9
                                                   :top-k 50
                                                   :top-p 0.95})]
      (is (instance? dev.langchain4j.model.ollama.OllamaStreamingChatModel model)))))

;; TODO: Uncomment when langchain4j 1.9.0+ is released with Gemini streaming support
;; (deftest test-create-streaming-model-google-ai-gemini
;;   (testing "Creates Google AI Gemini streaming model"
;;     (let [model (streaming/create-streaming-model {:provider :google-ai-gemini
;;                                                    :api-key "test-key"})]
;;       (is (instance? dev.langchain4j.model.googleai.GoogleAiGeminiStreamingChatModel model)
;;           "Should create GoogleAiGeminiStreamingChatModel instance"))))

;; (deftest test-create-streaming-model-vertex-ai-gemini
;;   (testing "Creates Vertex AI Gemini streaming model"
;;     (let [model (streaming/create-streaming-model {:provider :vertex-ai-gemini
;;                                                    :project "test-project"
;;                                                    :location "us-central1"})]
;;       (is (instance? dev.langchain4j.model.vertexai.VertexAiGeminiStreamingChatModel model)
;;           "Should create VertexAiGeminiStreamingChatModel instance"))))

;; =============================================================================
;; Integration Tests (require real API keys)
;; =============================================================================

(deftest ^:integration test-openai-streaming-integration
  (testing "OpenAI streaming works end-to-end"
    (when-let [api-key (System/getenv "OPENAI_API_KEY")]
      (let [model (streaming/create-streaming-model {:provider :openai
                                                     :api-key api-key
                                                     :model "gpt-4o-mini"})
            tokens (atom [])
            completed? (atom false)
            response (atom nil)]

        (streaming/stream-chat model "Say 'test' once"
                               {:on-token (fn [token] (swap! tokens conj token))
                                :on-complete (fn [resp]
                                               (reset! response resp)
                                               (reset! completed? true))
                                :on-error (fn [error]
                                            (println "Error:" (.getMessage error)))})

        ;; Wait for completion (max 10 seconds)
        (dotimes [_ 100]
          (when-not @completed?
            (Thread/sleep 100)))

        (is (pos? (count @tokens))
            "Should receive at least one token")
        (is @completed?
            "Should complete streaming")
        (is (some? @response)
            "Should receive response object")))))

(deftest ^:integration test-anthropic-streaming-integration
  (testing "Anthropic streaming works end-to-end"
    (when-let [api-key (System/getenv "ANTHROPIC_API_KEY")]
      (let [model (streaming/create-streaming-model {:provider :anthropic
                                                     :api-key api-key
                                                     :model "claude-3-5-sonnet-20241022"})
            all-text (atom "")
            completed? (atom false)]

        (streaming/stream-chat model "Say hello in 3 words"
                               {:on-token (fn [token] (swap! all-text str token))
                                :on-complete (fn [_] (reset! completed? true))
                                :on-error (fn [error]
                                            (println "Error:" (.getMessage error)))})

        ;; Wait for completion (max 10 seconds)
        (dotimes [_ 100]
          (when-not @completed?
            (Thread/sleep 100)))

        (is (not-empty @all-text)
            "Should receive some text")
        (is @completed?
            "Should complete streaming")))))

(deftest ^:integration test-ollama-streaming-integration
  (testing "Ollama streaming works end-to-end"
    ;; NOTE: This test requires Ollama to be installed and running locally
    ;; Install from: https://ollama.ai
    ;; Run: ollama pull llama3.1
    (try
      (let [model (streaming/create-streaming-model {:provider :ollama
                                                     :model "llama3.1"
                                                     :base-url "http://localhost:11434"})
            all-text (atom "")
            completed? (atom false)
            error? (atom nil)]

        (streaming/stream-chat model "Say hello in 3 words"
                               {:on-token (fn [token] (swap! all-text str token))
                                :on-complete (fn [_] (reset! completed? true))
                                :on-error (fn [error]
                                            (reset! error? error)
                                            (println "Ollama error (is Ollama running?):" (.getMessage error)))})

        ;; Wait for completion (max 10 seconds)
        (dotimes [_ 100]
          (when-not @completed?
            (Thread/sleep 100)))

        (when-not @error?
          (is (not-empty @all-text)
              "Should receive some text")
          (is @completed?
              "Should complete streaming")))
      (catch Exception e
        (println "Ollama integration test skipped (Ollama not available):" (.getMessage e))))))

(comment
  ;; Run all tests
  (clojure.test/run-tests 'langchain4clj.streaming-test)

  ;; Run specific test
  (test-stream-chat-callbacks-order)
  (test-stream-chat-error-callback)
  (test-create-streaming-model-openai)

  ;; Run integration tests (requires API keys)
  (test-openai-streaming-integration)
  (test-anthropic-streaming-integration))
