(ns langchain4clj.listeners.specs-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.spec.alpha :as s]
            [langchain4clj.listeners.specs :as specs]))

;; =============================================================================
;; Message Type Specs
;; =============================================================================

(deftest message-type-spec-test
  (testing "valid message types"
    (is (s/valid? ::specs/message-type :user))
    (is (s/valid? ::specs/message-type :system))
    (is (s/valid? ::specs/message-type :ai))
    (is (s/valid? ::specs/message-type :tool-execution-result)))

  (testing "invalid message types"
    (is (not (s/valid? ::specs/message-type :unknown)))
    (is (not (s/valid? ::specs/message-type "USER")))
    (is (not (s/valid? ::specs/message-type nil)))))

;; =============================================================================
;; Content Type Specs
;; =============================================================================

(deftest content-type-spec-test
  (testing "valid content types"
    (is (s/valid? ::specs/content-type :text))
    (is (s/valid? ::specs/content-type :image))
    (is (s/valid? ::specs/content-type :audio))
    (is (s/valid? ::specs/content-type :document)))

  (testing "invalid content types"
    (is (not (s/valid? ::specs/content-type :video)))
    (is (not (s/valid? ::specs/content-type "text")))))

;; =============================================================================
;; Provider Specs
;; =============================================================================

(deftest provider-spec-test
  (testing "valid providers"
    (is (s/valid? ::specs/provider :openai))
    (is (s/valid? ::specs/provider :anthropic))
    (is (s/valid? ::specs/provider :google-ai-gemini))
    (is (s/valid? ::specs/provider :vertex-ai-gemini))
    (is (s/valid? ::specs/provider :ollama))
    (is (s/valid? ::specs/provider :mistral)))

  (testing "invalid providers"
    (is (not (s/valid? ::specs/provider :aws-bedrock)))
    (is (not (s/valid? ::specs/provider "openai")))))

;; =============================================================================
;; Finish Reason Specs
;; =============================================================================

(deftest finish-reason-spec-test
  (testing "valid finish reasons"
    (is (s/valid? ::specs/finish-reason :end-turn))
    (is (s/valid? ::specs/finish-reason :stop))
    (is (s/valid? ::specs/finish-reason :max-tokens))
    (is (s/valid? ::specs/finish-reason :tool-calls))
    (is (s/valid? ::specs/finish-reason :content-filter))
    (is (s/valid? ::specs/finish-reason :error))
    (is (s/valid? ::specs/finish-reason :length)))

  (testing "invalid finish reasons"
    (is (not (s/valid? ::specs/finish-reason :cancelled)))
    (is (not (s/valid? ::specs/finish-reason "STOP")))))

;; =============================================================================
;; Model Name Specs
;; =============================================================================

(deftest model-name-spec-test
  (testing "valid OpenAI models"
    (is (s/valid? ::specs/openai-model :gpt-4o))
    (is (s/valid? ::specs/openai-model :gpt-4o-mini))
    (is (s/valid? ::specs/openai-model :o1))
    (is (s/valid? ::specs/openai-model :o3-mini)))

  (testing "valid Anthropic models"
    (is (s/valid? ::specs/anthropic-model :claude-opus-4))
    (is (s/valid? ::specs/anthropic-model :claude-sonnet-4))
    (is (s/valid? ::specs/anthropic-model :claude-3-5-haiku)))

  (testing "valid Google models"
    (is (s/valid? ::specs/google-model :gemini-2.5-flash))
    (is (s/valid? ::specs/google-model :gemini-1.5-pro)))

  (testing "model-name accepts any provider or custom string"
    (is (s/valid? ::specs/model-name :gpt-4o))
    (is (s/valid? ::specs/model-name :claude-sonnet-4))
    (is (s/valid? ::specs/model-name "custom-fine-tuned-model"))))

;; =============================================================================
;; Token Usage Specs
;; =============================================================================

(deftest token-usage-spec-test
  (testing "valid token usage"
    (is (s/valid? ::specs/token-usage
                  {:input-tokens 100
                   :output-tokens 200
                   :total-tokens 300})))

  (testing "zero tokens are valid"
    (is (s/valid? ::specs/token-usage
                  {:input-tokens 0
                   :output-tokens 0
                   :total-tokens 0})))

  (testing "negative tokens are invalid"
    (is (not (s/valid? ::specs/token-usage
                       {:input-tokens -1
                        :output-tokens 0
                        :total-tokens 0}))))

  (testing "missing keys are invalid"
    (is (not (s/valid? ::specs/token-usage
                       {:input-tokens 100})))))

;; =============================================================================
;; Message Specs
;; =============================================================================

(deftest user-message-spec-test
  (testing "valid user message"
    (is (s/valid? ::specs/user-message
                  {:message-type :user
                   :contents [{:content-type :text :text "Hello"}]})))

  (testing "user message with multiple contents"
    (is (s/valid? ::specs/user-message
                  {:message-type :user
                   :contents [{:content-type :text :text "Describe this"}
                              {:content-type :image}]})))

  (testing "wrong message type is invalid"
    (is (not (s/valid? ::specs/user-message
                       {:message-type :system
                        :contents [{:content-type :text :text "Hello"}]})))))

(deftest system-message-spec-test
  (testing "valid system message"
    (is (s/valid? ::specs/system-message
                  {:message-type :system
                   :text "You are a helpful assistant."})))

  (testing "missing text is invalid"
    (is (not (s/valid? ::specs/system-message
                       {:message-type :system})))))

(deftest ai-message-spec-test
  (testing "valid AI message with text"
    (is (s/valid? ::specs/ai-message
                  {:message-type :ai
                   :text "Here is my response."})))

  (testing "valid AI message with tool calls"
    (is (s/valid? ::specs/ai-message
                  {:message-type :ai
                   :text "Let me check that."
                   :tool-execution-requests [{:tool-id "call_123"
                                              :tool-name :get-weather
                                              :arguments {:city "London"}}]})))

  (testing "AI message without text is valid (tool-only response)"
    (is (s/valid? ::specs/ai-message
                  {:message-type :ai}))))

(deftest tool-result-message-spec-test
  (testing "valid tool result"
    (is (s/valid? ::specs/tool-result-message
                  {:message-type :tool-execution-result
                   :tool-id "call_123"
                   :tool-name :get-weather
                   :text "Sunny, 22°C"})))

  (testing "tool name can be string"
    (is (s/valid? ::specs/tool-result-message
                  {:message-type :tool-execution-result
                   :tool-id "call_123"
                   :tool-name "get-weather"
                   :text "Sunny, 22°C"}))))

;; =============================================================================
;; Parameters Specs
;; =============================================================================

(deftest parameters-spec-test
  (testing "minimal valid parameters"
    (is (s/valid? ::specs/parameters
                  {:model-name :gpt-4o})))

  (testing "full parameters"
    (is (s/valid? ::specs/parameters
                  {:model-name :claude-sonnet-4
                   :temperature 0.7
                   :max-output-tokens 4096
                   :top-p 0.9
                   :top-k 40
                   :tool-specifications []})))

  (testing "temperature out of range is invalid"
    (is (not (s/valid? ::specs/parameters
                       {:model-name :gpt-4o
                        :temperature 3.0}))))

  (testing "top-p out of range is invalid"
    (is (not (s/valid? ::specs/parameters
                       {:model-name :gpt-4o
                        :top-p 1.5})))))

;; =============================================================================
;; Context Specs
;; =============================================================================

(deftest request-context-spec-test
  (testing "valid request context"
    (is (s/valid? ::specs/request-context
                  {:messages [{:message-type :user
                               :contents [{:content-type :text :text "Hello"}]}]
                   :parameters {:model-name :gpt-4o}
                   :provider :openai})))

  (testing "request context with optional fields"
    (is (s/valid? ::specs/request-context
                  {:messages [{:message-type :system :text "Be helpful"}
                              {:message-type :user
                               :contents [{:content-type :text :text "Hello"}]}]
                   :parameters {:model-name :claude-sonnet-4
                                :temperature 0.7}
                   :provider :anthropic
                   :attributes {:session-id "abc123"}
                   :raw-context nil})))

  (testing "missing required fields is invalid"
    (is (not (s/valid? ::specs/request-context
                       {:messages []})))))

(deftest response-context-spec-test
  (testing "valid response context"
    (is (s/valid? ::specs/response-context
                  {:ai-message {:message-type :ai :text "Hello!"}
                   :response-metadata {:model-name :gpt-4o
                                       :finish-reason :end-turn
                                       :token-usage {:input-tokens 10
                                                     :output-tokens 5
                                                     :total-tokens 15}}
                   :provider :openai}))))

(deftest error-context-spec-test
  (testing "valid error context"
    (is (s/valid? ::specs/error-context
                  {:error {:error-message "Rate limit exceeded"
                           :error-type "RateLimitException"}
                   :provider :openai})))

  (testing "error context with cause"
    (is (s/valid? ::specs/error-context
                  {:error {:error-message "Connection failed"
                           :error-type "IOException"
                           :error-cause "Host unreachable"}
                   :provider :anthropic
                   :attributes {:retry-count 3}}))))
