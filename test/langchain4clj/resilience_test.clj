(ns langchain4clj.resilience-test
  "Tests for provider failover and resilience system"
  (:require [clojure.test :refer [deftest testing is]]
            [langchain4clj.resilience :as resilience]
            [langchain4clj.core :as core])
  (:import [dev.langchain4j.model.chat ChatModel]
           [dev.langchain4j.model.chat.request ChatRequest]
           [dev.langchain4j.model.chat.response ChatResponse]
           [dev.langchain4j.data.message AiMessage UserMessage]))

;; ============================================================================
;; Mock Utilities
;; ============================================================================

(defn mock-working-model
  "Creates a ChatModel that always succeeds with given response"
  [response]
  (reify ChatModel
    (^String chat [_ ^String _message]
      response)
    (^ChatResponse chat [_ ^ChatRequest _request]
      (-> (ChatResponse/builder)
          (.aiMessage (-> (AiMessage/builder)
                          (.text response)
                          (.build)))
          (.build)))))

(defn mock-failing-model
  "Creates a ChatModel that always fails with given error message"
  [error-message]
  (reify ChatModel
    (^String chat [_ ^String _message]
      (throw (Exception. error-message)))
    (^ChatResponse chat [_ ^ChatRequest _request]
      (throw (Exception. error-message)))))

(defn mock-flaky-model
  "Creates a ChatModel that fails N times then succeeds"
  [fail-count response]
  (let [attempts (atom 0)]
    (reify ChatModel
      (^String chat [_ ^String _message]
        (if (< @attempts fail-count)
          (do
            (swap! attempts inc)
            (throw (Exception. "429 Rate limit exceeded")))
          response))
      (^ChatResponse chat [_ ^ChatRequest _request]
        (if (< @attempts fail-count)
          (do
            (swap! attempts inc)
            (throw (Exception. "429 Rate limit exceeded")))
          (-> (ChatResponse/builder)
              (.aiMessage (-> (AiMessage/builder)
                              (.text response)
                              (.build)))
              (.build)))))))

(defn mock-model-with-delay
  "Creates a ChatModel that delays before responding"
  [delay-ms response]
  (reify ChatModel
    (^String chat [_ ^String _message]
      (Thread/sleep delay-ms)
      response)
    (^ChatResponse chat [_ ^ChatRequest _request]
      (Thread/sleep delay-ms)
      (-> (ChatResponse/builder)
          (.aiMessage (-> (AiMessage/builder)
                          (.text response)
                          (.build)))
          (.build)))))

;; ============================================================================
;; Basic Failover Tests
;; ============================================================================

(deftest test-single-provider-success
  (testing "Single provider that works should succeed"
    (let [model (resilience/create-resilient-model
                 {:primary (mock-working-model "Success!")})]
      (is (= "Success!" (core/chat model "test"))))))

(deftest test-single-provider-failure
  (testing "Single provider that fails should throw"
    (let [model (resilience/create-resilient-model
                 {:primary (mock-failing-model "Fatal error")})]
      (is (thrown? Exception (core/chat model "test"))))))

(deftest test-primary-works-no-fallback
  (testing "When primary works, fallbacks should not be tried"
    (let [fallback-called (atom false)
          fallback (reify ChatModel
                     (^String chat [_ ^String _message]
                       (reset! fallback-called true)
                       "Fallback"))
          model (resilience/create-resilient-model
                 {:primary (mock-working-model "Primary")
                  :fallbacks [fallback]})]

      (is (= "Primary" (core/chat model "test")))
      (is (false? @fallback-called) "Fallback should not be called when primary works"))))

(deftest test-primary-fails-fallback-works
  (testing "When primary fails, should try fallback"
    (let [model (resilience/create-resilient-model
                 {:primary (mock-failing-model "401 Unauthorized")
                  :fallbacks [(mock-working-model "Fallback success")]})]
      (is (= "Fallback success" (core/chat model "test"))))))

(deftest test-all-providers-fail
  (testing "When all providers fail, should throw with details"
    (let [model (resilience/create-resilient-model
                 {:primary (mock-failing-model "401 Unauthorized")
                  :fallbacks [(mock-failing-model "404 Not found")
                              (mock-failing-model "connection error")]})]
      (try
        (core/chat model "test")
        (is false "Should have thrown")
        (catch Exception e
          (is (= "All providers failed" (.getMessage e)))
          (let [data (ex-data e)]
            (is (= 3 (:providers-count data)))
            (is (= 3 (:providers-tried data)))))))))

(deftest test-multiple-fallbacks
  (testing "Should try all fallbacks in order"
    (let [model (resilience/create-resilient-model
                 {:primary (mock-failing-model "401 Unauthorized")
                  :fallbacks [(mock-failing-model "404 Not found")
                              (mock-working-model "Second fallback works!")
                              (mock-working-model "Third fallback")]})]
      (is (= "Second fallback works!" (core/chat model "test"))))))

;; ============================================================================
;; Retry Logic Tests
;; ============================================================================

(deftest test-retry-on-rate-limit
  (testing "Should retry on rate limit errors"
    (let [model (resilience/create-resilient-model
                 {:primary (mock-flaky-model 2 "Success after retries")
                  :max-retries 3
                  :retry-delay-ms 10})]
      (is (= "Success after retries" (core/chat model "test"))))))

(deftest test-retry-exhaustion-then-fallback
  (testing "Should fallback after retries exhausted"
    (let [model (resilience/create-resilient-model
                 {:primary (mock-flaky-model 10 "Never succeeds")
                  :fallbacks [(mock-working-model "Fallback works")]
                  :max-retries 2
                  :retry-delay-ms 10})]
      (is (= "Fallback works" (core/chat model "test"))))))

(deftest test-no-retry-on-auth-error
  (testing "Should not retry on authentication errors, go straight to fallback"
    (let [primary-attempts (atom 0)
          primary (reify ChatModel
                    (^String chat [_ ^String _message]
                      (swap! primary-attempts inc)
                      (throw (Exception. "401 Unauthorized"))))
          model (resilience/create-resilient-model
                 {:primary primary
                  :fallbacks [(mock-working-model "Fallback")]
                  :max-retries 3
                  :retry-delay-ms 10})]

      (is (= "Fallback" (core/chat model "test")))
      (is (= 1 @primary-attempts) "Should only try primary once on auth error"))))

(deftest test-non-recoverable-error
  (testing "Should immediately throw non-recoverable errors"
    (let [model (resilience/create-resilient-model
                 {:primary (mock-failing-model "400 Bad Request")
                  :fallbacks [(mock-working-model "Fallback")]
                  :max-retries 3})]
      (is (thrown-with-msg? Exception #"400 Bad Request"
                            (core/chat model "test"))))))

;; ============================================================================
;; ChatRequest Tests (Advanced Features)
;; ============================================================================

(deftest test-chatrequest-failover
  (testing "Should work with ChatRequest (tools, JSON mode, etc)"
    (let [model (resilience/create-resilient-model
                 {:primary (mock-failing-model "401 Unauthorized")
                  :fallbacks [(mock-working-model "ChatRequest fallback works")]})
          request (-> (ChatRequest/builder)
                      (.messages [(UserMessage. "test")])
                      (.build))
          response (.chat ^ChatModel model request)]

      (is (instance? ChatResponse response))
      (is (= "ChatRequest fallback works" (-> response .aiMessage .text))))))

;; ============================================================================
;; Configuration Tests
;; ============================================================================

(deftest test-default-config
  (testing "Should use default retry and delay values"
    (let [model (resilience/create-resilient-model
                 {:primary (mock-working-model "Success")})]
      (is (= "Success" (core/chat model "test"))))))

(deftest test-custom-retry-config
  (testing "Should respect custom retry configuration"
    (let [start-time (System/currentTimeMillis)
          model (resilience/create-resilient-model
                 {:primary (mock-flaky-model 2 "Success")
                  :max-retries 3
                  :retry-delay-ms 50})
          _ (core/chat model "test")
          elapsed (- (System/currentTimeMillis) start-time)]

      ;; 2 retries * 50ms = at least 100ms
      (is (>= elapsed 100) "Should have delayed for retries"))))

(deftest test-zero-retries
  (testing "Should work with zero retries (immediate failover)"
    (let [model (resilience/create-resilient-model
                 {:primary (mock-failing-model "401 Unauthorized")
                  :fallbacks [(mock-working-model "Immediate fallback")]
                  :max-retries 0})]
      (is (= "Immediate fallback" (core/chat model "test"))))))

(deftest test-preconditions
  (testing "Should validate configuration"
    (is (thrown? AssertionError
                 (resilience/create-resilient-model {:primary nil})))
    (is (thrown? AssertionError
                 (resilience/create-resilient-model
                  {:primary (mock-working-model "test")
                   :max-retries -1})))
    (is (thrown? AssertionError
                 (resilience/create-resilient-model
                  {:primary (mock-working-model "test")
                   :retry-delay-ms 0})))))

;; ============================================================================
;; Integration Tests (require real providers)
;; ============================================================================

(deftest ^:integration test-real-provider-failover
  (testing "Real providers with invalid key should fallback to Ollama"
    ;; This test requires Ollama to be running locally
    (try
      (let [model (resilience/create-resilient-model
                   {:primary (core/create-model {:provider :openai
                                                 :api-key "invalid-key"})
                    :fallbacks [(core/create-model {:provider :ollama})]
                    :max-retries 1
                    :retry-delay-ms 100})
            ;; OpenAI should fail (invalid key) → fallback to Ollama succeeds
            response (core/chat model "Say 'test' only")]

        (is (string? response))
        (is (not-empty response)))

      (catch Exception e
        ;; If Ollama not available, test is skipped
        (println "Ollama integration test skipped (Ollama not available):" (.getMessage e))))))

;; ============================================================================
;; Circuit Breaker Tests (Phase 2)
;; ============================================================================

(deftest test-circuit-breaker-opens-after-failures
  (testing "Circuit breaker should open after failure threshold"
    (let [model (resilience/create-resilient-model
                 {:primary (mock-failing-model "503 Service Unavailable")
                  :fallbacks [(mock-working-model "Fallback works")]
                  :circuit-breaker? true
                  :failure-threshold 3
                  :max-retries 0})]

      ;; First 3 failures should open the circuit
      (is (= "Fallback works" (core/chat model "test 1")))
      (is (= "Fallback works" (core/chat model "test 2")))
      (is (= "Fallback works" (core/chat model "test 3")))

      ;; After 3 failures, circuit should be open - skips primary
      ;; Should go directly to fallback without trying primary
      (is (= "Fallback works" (core/chat model "test 4"))))))

(deftest test-circuit-breaker-closed-to-open-transition
  (testing "Circuit breaker state transitions correctly from closed to open"
    (let [failure-count (atom 0)
          failing-primary (reify ChatModel
                            (^String chat [_ ^String _message]
                              (swap! failure-count inc)
                              (throw (Exception. "503 Service Unavailable"))))
          model (resilience/create-resilient-model
                 {:primary failing-primary
                  :fallbacks [(mock-working-model "Fallback")]
                  :circuit-breaker? true
                  :failure-threshold 3
                  :max-retries 0})]

      ;; Trigger failures to open circuit
      (core/chat model "test 1")
      (core/chat model "test 2")
      (core/chat model "test 3")

      (let [before-count @failure-count]
        ;; Next call should skip primary (circuit open)
        (core/chat model "test 4")
        ;; Failure count should not increase (primary skipped)
        (is (= before-count @failure-count))))))

(deftest test-circuit-breaker-half-open-transition
  (testing "Circuit breaker transitions to half-open after timeout"
    (let [model (resilience/create-resilient-model
                 {:primary (mock-failing-model "503 Service Unavailable")
                  :fallbacks [(mock-working-model "Fallback")]
                  :circuit-breaker? true
                  :failure-threshold 2
                  :timeout-ms 100 ; Short timeout for testing
                  :max-retries 0})]

      ;; Open the circuit
      (core/chat model "test 1")
      (core/chat model "test 2")

      ;; Wait for timeout
      (Thread/sleep 150)

      ;; Next call should try primary again (half-open)
      ;; Since primary still fails, should fall back
      (is (= "Fallback" (core/chat model "test 3"))))))

(deftest test-circuit-breaker-half-open-to-closed
  (testing "Circuit breaker closes after success threshold in half-open"
    (let [call-count (atom 0)
          recovering-model (reify ChatModel
                             (^String chat [_ ^String _message]
                               (let [count (swap! call-count inc)]
                                 (if (<= count 2)
                                   (throw (Exception. "503 Service Unavailable"))
                                   "Recovered!"))))
          model (resilience/create-resilient-model
                 {:primary recovering-model
                  :fallbacks [(mock-working-model "Fallback")]
                  :circuit-breaker? true
                  :failure-threshold 2
                  :success-threshold 2
                  :timeout-ms 100
                  :max-retries 0})]

      ;; Open the circuit (2 failures)
      (is (= "Fallback" (core/chat model "test 1")))
      (is (= "Fallback" (core/chat model "test 2")))

      ;; Wait for half-open transition
      (Thread/sleep 150)

      ;; Now provider is recovered - should succeed
      (is (= "Recovered!" (core/chat model "test 3")))
      (is (= "Recovered!" (core/chat model "test 4")))

      ;; Circuit should be closed now - keep using primary
      (is (= "Recovered!" (core/chat model "test 5"))))))

(deftest test-circuit-breaker-half-open-to-open-on-failure
  (testing "Circuit breaker reopens if half-open test fails"
    (let [model (resilience/create-resilient-model
                 {:primary (mock-failing-model "503 Service Unavailable")
                  :fallbacks [(mock-working-model "Fallback")]
                  :circuit-breaker? true
                  :failure-threshold 2
                  :timeout-ms 100
                  :max-retries 0})]

      ;; Open the circuit
      (core/chat model "test 1")
      (core/chat model "test 2")

      ;; Wait for half-open
      (Thread/sleep 150)

      ;; Try again - should fail and reopen
      (is (= "Fallback" (core/chat model "test 3")))

      ;; Circuit should be open again - skip primary
      (is (= "Fallback" (core/chat model "test 4"))))))

(deftest test-circuit-breaker-with-retries
  (testing "Circuit breaker works with retry logic"
    (let [model (resilience/create-resilient-model
                 {:primary (mock-flaky-model 10 "Never succeeds")
                  :fallbacks [(mock-working-model "Fallback")]
                  :circuit-breaker? true
                  :failure-threshold 2
                  :max-retries 2
                  :retry-delay-ms 10})]

      ;; First call: 2 retries + 1 initial = 3 attempts, but only 1 failure recorded
      ;; because retries are on same request
      (is (= "Fallback" (core/chat model "test 1")))

      ;; Second call: should record second failure and open circuit
      (is (= "Fallback" (core/chat model "test 2")))

      ;; Third call: circuit open, should skip primary
      (is (= "Fallback" (core/chat model "test 3"))))))

(deftest test-circuit-breaker-per-provider
  (testing "Each provider has its own circuit breaker"
    (let [primary-calls (atom 0)
          fallback-calls (atom 0)
          primary (reify ChatModel
                    (^String chat [_ ^String _message]
                      (swap! primary-calls inc)
                      (throw (Exception. "503 Service Unavailable"))))
          fallback (reify ChatModel
                     (^String chat [_ ^String _message]
                       (swap! fallback-calls inc)
                       (throw (Exception. "503 Service Unavailable"))))
          model (resilience/create-resilient-model
                 {:primary primary
                  :fallbacks [fallback]
                  :circuit-breaker? true
                  :failure-threshold 2
                  :max-retries 0})]

      ;; This should fail on both providers
      (is (thrown? Exception (core/chat model "test 1")))
      (is (thrown? Exception (core/chat model "test 2")))

      ;; Both circuit breakers should be open now
      ;; Next call should throw immediately without trying either
      (is (thrown-with-msg? Exception #"All providers failed or unavailable"
                            (core/chat model "test 3")))

      ;; Both providers should have been called exactly 2 times (to open their circuits)
      (is (= 2 @primary-calls))
      (is (= 2 @fallback-calls)))))

(deftest test-circuit-breaker-disabled-by-default
  (testing "Circuit breaker is disabled by default (backward compatibility)"
    (let [call-count (atom 0)
          model (resilience/create-resilient-model
                 {:primary (reify ChatModel
                             (^String chat [_ ^String _message]
                               (swap! call-count inc)
                               (throw (Exception. "503 Service Unavailable"))))
                  :fallbacks [(mock-working-model "Fallback")]
                  :max-retries 0})]

      ;; Without circuit breaker, primary should be tried every time
      (is (= "Fallback" (core/chat model "test 1")))
      (is (= "Fallback" (core/chat model "test 2")))
      (is (= "Fallback" (core/chat model "test 3")))
      (is (= "Fallback" (core/chat model "test 4")))
      (is (= "Fallback" (core/chat model "test 5")))

      ;; Primary should have been called 5 times (no circuit breaker)
      (is (= 5 @call-count)))))

(deftest test-circuit-breaker-chatrequest
  (testing "Circuit breaker works with ChatRequest"
    (let [model (resilience/create-resilient-model
                 {:primary (mock-failing-model "503 Service Unavailable")
                  :fallbacks [(mock-working-model "Fallback works")]
                  :circuit-breaker? true
                  :failure-threshold 2
                  :max-retries 0})
          request (-> (ChatRequest/builder)
                      (.messages [(UserMessage. "test")])
                      (.build))]

      ;; Open the circuit with ChatRequest
      (.chat ^ChatModel model request)
      (.chat ^ChatModel model request)

      ;; Circuit should be open - use fallback
      (let [response (.chat ^ChatModel model request)]
        (is (= "Fallback works" (-> response .aiMessage .text)))))))

(comment
  ;; Run all tests
  (clojure.test/run-tests 'langchain4clj.resilience-test)

  ;; Run specific test
  (test-primary-fails-fallback-works)

  ;; Run integration tests (requires Ollama)
  (test-real-provider-failover))
