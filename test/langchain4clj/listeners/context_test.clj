(ns langchain4clj.listeners.context-test
  (:require [clojure.test :refer [deftest is testing]]
            [langchain4clj.listeners.context :as ctx]
            [langchain4clj.listeners.specs :as specs]))

;; =============================================================================
;; Message Constructors Tests
;; =============================================================================

(deftest text-content-test
  (testing "creates text content"
    (is (= {:content-type :text :text "Hello"}
           (ctx/text-content "Hello")))))

(deftest image-content-test
  (testing "creates image content"
    (is (= {:content-type :image :data "base64data" :mime-type "image/png"}
           (ctx/image-content "base64data" "image/png")))))

(deftest user-message-test
  (testing "creates user message with text"
    (let [msg (ctx/user-message "Hello world")]
      (is (= :user (:message-type msg)))
      (is (= 1 (count (:contents msg))))
      (is (= :text (get-in msg [:contents 0 :content-type])))
      (is (= "Hello world" (get-in msg [:contents 0 :text]))))))

(deftest user-message-with-contents-test
  (testing "creates user message with multiple contents"
    (let [contents [(ctx/text-content "Describe this")
                    (ctx/image-content "data" "image/jpeg")]
          msg (ctx/user-message-with-contents contents)]
      (is (= :user (:message-type msg)))
      (is (= 2 (count (:contents msg))))
      (is (= :text (get-in msg [:contents 0 :content-type])))
      (is (= :image (get-in msg [:contents 1 :content-type]))))))

(deftest system-message-test
  (testing "creates system message"
    (let [msg (ctx/system-message "You are helpful")]
      (is (= :system (:message-type msg)))
      (is (= "You are helpful" (:text msg))))))

(deftest ai-message-test
  (testing "creates AI message with text"
    (let [msg (ctx/ai-message "Here is my response")]
      (is (= :ai (:message-type msg)))
      (is (= "Here is my response" (:text msg))))))

(deftest ai-message-with-tool-calls-test
  (testing "creates AI message with tool calls"
    (let [tool-req (ctx/tool-request "call_123" :get-weather {:city "London"})
          msg (ctx/ai-message-with-tool-calls "Let me check" [tool-req])]
      (is (= :ai (:message-type msg)))
      (is (= "Let me check" (:text msg)))
      (is (= 1 (count (:tool-execution-requests msg))))
      (is (= "call_123" (get-in msg [:tool-execution-requests 0 :tool-id]))))))

(deftest tool-request-test
  (testing "creates tool request"
    (let [req (ctx/tool-request "call_abc" :calculator {:expr "2+2"})]
      (is (= "call_abc" (:tool-id req)))
      (is (= :calculator (:tool-name req)))
      (is (= {:expr "2+2"} (:arguments req))))))

(deftest tool-result-test
  (testing "creates tool result message"
    (let [msg (ctx/tool-result "call_abc" :calculator "4")]
      (is (= :tool-execution-result (:message-type msg)))
      (is (= "call_abc" (:tool-id msg)))
      (is (= :calculator (:tool-name msg)))
      (is (= "4" (:text msg))))))

;; =============================================================================
;; Token Usage Tests
;; =============================================================================

(deftest token-usage-test
  (testing "creates token usage with auto-calculated total"
    (let [usage (ctx/token-usage 100 200)]
      (is (= 100 (:input-tokens usage)))
      (is (= 200 (:output-tokens usage)))
      (is (= 300 (:total-tokens usage)))))

  (testing "creates token usage with explicit total"
    (let [usage (ctx/token-usage 100 200 350)]
      (is (= 100 (:input-tokens usage)))
      (is (= 200 (:output-tokens usage)))
      (is (= 350 (:total-tokens usage))))))

;; =============================================================================
;; Parameters Tests
;; =============================================================================

(deftest parameters-test
  (testing "creates parameters with model and options"
    (let [params (ctx/parameters :claude-sonnet-4 {:temperature 0.7 :max-output-tokens 4096})]
      (is (= :claude-sonnet-4 (:model-name params)))
      (is (= 0.7 (:temperature params)))
      (is (= 4096 (:max-output-tokens params)))))

  (testing "creates minimal parameters"
    (let [params (ctx/parameters :gpt-4o {})]
      (is (= :gpt-4o (:model-name params)))
      (is (= 1 (count params))))))

;; =============================================================================
;; Response Metadata Tests
;; =============================================================================

(deftest response-metadata-test
  (testing "creates response metadata"
    (let [meta (ctx/response-metadata
                {:model-name :claude-sonnet-4
                 :finish-reason :end-turn
                 :token-usage (ctx/token-usage 50 100)})]
      (is (= :claude-sonnet-4 (:model-name meta)))
      (is (= :end-turn (:finish-reason meta)))
      (is (= 50 (get-in meta [:token-usage :input-tokens])))))

  (testing "includes optional response-id"
    (let [meta (ctx/response-metadata
                {:model-name :gpt-4o
                 :finish-reason :stop
                 :token-usage (ctx/token-usage 10 20)
                 :response-id "resp_abc123"})]
      (is (= "resp_abc123" (:response-id meta))))))

;; =============================================================================
;; Context Constructors Tests
;; =============================================================================

(deftest request-context-test
  (testing "creates request context with required fields"
    (let [ctx-map (ctx/request-context
                   {:messages [(ctx/user-message "Hello")]
                    :parameters (ctx/parameters :gpt-4o {:temperature 0.5})
                    :provider :openai})]
      (is (= 1 (count (:messages ctx-map))))
      (is (= :gpt-4o (get-in ctx-map [:parameters :model-name])))
      (is (= :openai (:provider ctx-map)))))

  (testing "includes optional attributes"
    (let [ctx-map (ctx/request-context
                   {:messages [(ctx/user-message "Hello")]
                    :parameters (ctx/parameters :gpt-4o {})
                    :provider :openai
                    :attributes {:session-id "sess_123"}})]
      (is (= {:session-id "sess_123"} (:attributes ctx-map)))))

  (testing "includes optional raw-context"
    (let [raw-obj (Object.)
          ctx-map (ctx/request-context
                   {:messages [(ctx/user-message "Hello")]
                    :parameters (ctx/parameters :gpt-4o {})
                    :provider :openai
                    :raw-context raw-obj})]
      (is (= raw-obj (:raw-context ctx-map))))))

(deftest response-context-test
  (testing "creates response context with required fields"
    (let [ctx-map (ctx/response-context
                   {:ai-message (ctx/ai-message "Hello back!")
                    :response-metadata (ctx/response-metadata
                                        {:model-name :gpt-4o
                                         :finish-reason :end-turn
                                         :token-usage (ctx/token-usage 10 20)})
                    :provider :openai})]
      (is (= :ai (get-in ctx-map [:ai-message :message-type])))
      (is (= "Hello back!" (get-in ctx-map [:ai-message :text])))
      (is (= :end-turn (get-in ctx-map [:response-metadata :finish-reason])))
      (is (= :openai (:provider ctx-map)))))

  (testing "includes optional request-context"
    (let [req-ctx (ctx/request-context
                   {:messages [(ctx/user-message "Hello")]
                    :parameters (ctx/parameters :gpt-4o {})
                    :provider :openai})
          resp-ctx (ctx/response-context
                    {:ai-message (ctx/ai-message "Hi!")
                     :response-metadata (ctx/response-metadata
                                         {:model-name :gpt-4o
                                          :finish-reason :stop
                                          :token-usage (ctx/token-usage 5 10)})
                     :provider :openai
                     :request-context req-ctx})]
      (is (= req-ctx (:request-context resp-ctx))))))

(deftest error-context-test
  (testing "creates error context with required fields"
    (let [ctx-map (ctx/error-context
                   {:error (ctx/error-info "Rate limit" "RateLimitException")
                    :provider :openai})]
      (is (= "Rate limit" (get-in ctx-map [:error :error-message])))
      (is (= "RateLimitException" (get-in ctx-map [:error :error-type])))
      (is (= :openai (:provider ctx-map)))))

  (testing "includes error cause"
    (let [ctx-map (ctx/error-context
                   {:error (ctx/error-info "Timeout" "TimeoutException" "Host unreachable")
                    :provider :anthropic})]
      (is (= "Host unreachable" (get-in ctx-map [:error :error-cause]))))))

(deftest error-info-test
  (testing "creates error info without cause"
    (let [err (ctx/error-info "Something failed" "GenericException")]
      (is (= "Something failed" (:error-message err)))
      (is (= "GenericException" (:error-type err)))
      (is (not (contains? err :error-cause)))))

  (testing "creates error info with cause"
    (let [err (ctx/error-info "Connection failed" "IOException" "Network error")]
      (is (= "Connection failed" (:error-message err)))
      (is (= "IOException" (:error-type err)))
      (is (= "Network error" (:error-cause err))))))

;; =============================================================================
;; Validation Tests
;; =============================================================================

(deftest validate!-test
  (testing "returns context when valid"
    (let [ctx-map {:messages [{:message-type :user
                               :contents [{:content-type :text :text "Hi"}]}]
                   :parameters {:model-name :gpt-4o}
                   :provider :openai}]
      (is (= ctx-map (ctx/validate! ::specs/request-context ctx-map)))))

  (testing "throws on invalid context"
    (is (thrown? clojure.lang.ExceptionInfo
                 (ctx/validate! ::specs/request-context {:invalid "data"})))))

(deftest valid?-test
  (testing "returns true for valid context"
    (let [ctx-map {:messages [{:message-type :user
                               :contents [{:content-type :text :text "Hi"}]}]
                   :parameters {:model-name :gpt-4o}
                   :provider :openai}]
      (is (true? (ctx/valid? ::specs/request-context ctx-map)))))

  (testing "returns false for invalid context"
    (is (false? (ctx/valid? ::specs/request-context {:invalid "data"})))))

;; =============================================================================
;; Java Conversion Tests
;; =============================================================================

(deftest ->java-test
  (testing "converts context to Java format"
    (let [ctx-map (ctx/request-context
                   {:messages [(ctx/user-message "Hello")]
                    :parameters (ctx/parameters :claude-sonnet-4 {:temperature 0.7})
                    :provider :anthropic})
          java-ctx (ctx/->java ctx-map)]
      (is (= "USER" (get-in java-ctx [:messages 0 :message-type])))
      (is (= "TEXT" (get-in java-ctx [:messages 0 :contents 0 :content-type])))
      (is (= "claude-sonnet-4-20250514" (get-in java-ctx [:parameters :model-name])))
      (is (= "ANTHROPIC" (:provider java-ctx))))))

(deftest <-java-test
  (testing "converts Java format to context"
    (let [java-ctx {:messages [{:message-type "USER"
                                :contents [{:content-type "TEXT" :text "Hello"}]}]
                    :parameters {:model-name "gpt-4o" :temperature 0.7}
                    :provider "OPENAI"}
          ctx-map (ctx/<-java java-ctx)]
      (is (= :user (get-in ctx-map [:messages 0 :message-type])))
      (is (= :text (get-in ctx-map [:messages 0 :contents 0 :content-type])))
      (is (= :gpt-4o (get-in ctx-map [:parameters :model-name])))
      (is (= :openai (:provider ctx-map))))))

;; =============================================================================
;; Integration Tests - Full Workflow
;; =============================================================================

(deftest full-workflow-test
  (testing "complete request/response workflow"
    (let [;; Create request
          request (ctx/request-context
                   {:messages [(ctx/system-message "You are helpful")
                               (ctx/user-message "What is 2+2?")]
                    :parameters (ctx/parameters :claude-sonnet-4
                                                {:temperature 0.7
                                                 :max-output-tokens 1024})
                    :provider :anthropic})

          ;; Validate request
          _ (ctx/validate! ::specs/request-context request)

          ;; Create response
          response (ctx/response-context
                    {:ai-message (ctx/ai-message "2+2 equals 4")
                     :response-metadata (ctx/response-metadata
                                         {:model-name :claude-sonnet-4
                                          :finish-reason :end-turn
                                          :token-usage (ctx/token-usage 25 10)})
                     :provider :anthropic
                     :request-context request})

          ;; Convert to Java
          java-response (ctx/->java response)]

      ;; Verify structure
      (is (= 2 (count (:messages request))))
      (is (= :system (get-in request [:messages 0 :message-type])))
      (is (= :user (get-in request [:messages 1 :message-type])))
      (is (= "2+2 equals 4" (get-in response [:ai-message :text])))
      (is (= 35 (get-in response [:response-metadata :token-usage :total-tokens])))

      ;; Verify Java conversion
      (is (= "ANTHROPIC" (:provider java-response)))
      (is (= "AI" (get-in java-response [:ai-message :message-type]))))))

(deftest tool-calling-workflow-test
  (testing "request with tool call response"
    (let [_request (ctx/request-context
                    {:messages [(ctx/user-message "What's the weather in London?")]
                     :parameters (ctx/parameters :gpt-4o {:temperature 0.0})
                     :provider :openai})

          ;; AI responds with tool call
          ai-response (ctx/ai-message-with-tool-calls
                       "Let me check the weather for you."
                       [(ctx/tool-request "call_weather_1" :get-weather {:city "London"})])

          response-1 (ctx/response-context
                      {:ai-message ai-response
                       :response-metadata (ctx/response-metadata
                                           {:model-name :gpt-4o
                                            :finish-reason :tool-calls
                                            :token-usage (ctx/token-usage 30 20)})
                       :provider :openai})

          ;; Tool result
          tool-result-msg (ctx/tool-result "call_weather_1" :get-weather "Sunny, 18°C")]

      (is (= :tool-calls (get-in response-1 [:response-metadata :finish-reason])))
      (is (= 1 (count (get-in response-1 [:ai-message :tool-execution-requests]))))
      (is (= :tool-execution-result (:message-type tool-result-msg)))
      (is (= "Sunny, 18°C" (:text tool-result-msg))))))
