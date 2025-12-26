(ns langchain4clj.thinking-test
  "Tests for thinking/reasoning modes in chat models"
  (:require [clojure.test :refer [deftest is testing]]
            [langchain4clj.core :as core])
  (:import [dev.langchain4j.model.openai OpenAiChatModel]
           [dev.langchain4j.model.anthropic AnthropicChatModel]
           [dev.langchain4j.model.googleai GoogleAiGeminiChatModel]))

;; =============================================================================
;; OpenAI Thinking Tests
;; =============================================================================

(deftest test-openai-with-thinking-effort
  (testing "OpenAI model with reasoning effort creates request parameters"
    (let [config {:provider :openai
                  :api-key "test-key"
                  :model "o3-mini"
                  :thinking {:effort :high}}
          model (core/create-model config)]
      (is (instance? OpenAiChatModel model)))))

(deftest test-openai-with-thinking-return
  (testing "OpenAI model with return thinking option"
    (let [config {:provider :openai
                  :api-key "test-key"
                  :model "o3-mini"
                  :thinking {:effort :medium :return true}}
          model (core/create-model config)]
      (is (instance? OpenAiChatModel model)))))

(deftest test-openai-with-thinking-helper
  (testing "with-thinking helper for OpenAI"
    (let [config (-> {:provider :openai :api-key "test-key" :model "o3-mini"}
                     (core/with-thinking {:effort :low :return true}))
          model (core/create-model config)]
      (is (instance? OpenAiChatModel model))
      (is (= {:effort :low :return true} (:thinking config))))))

(deftest test-openai-without-thinking
  (testing "OpenAI model without thinking config still works"
    (let [config {:provider :openai
                  :api-key "test-key"
                  :model "gpt-4o-mini"}
          model (core/create-model config)]
      (is (instance? OpenAiChatModel model)))))

;; =============================================================================
;; Anthropic Thinking Tests
;; =============================================================================

(deftest test-anthropic-with-thinking-enabled
  (testing "Anthropic model with thinking enabled"
    (let [config {:provider :anthropic
                  :api-key "test-key"
                  :model "claude-sonnet-4-20250514"
                  :thinking {:enabled true}}
          model (core/create-model config)]
      (is (instance? AnthropicChatModel model)))))

(deftest test-anthropic-with-thinking-budget
  (testing "Anthropic model with thinking budget tokens"
    (let [config {:provider :anthropic
                  :api-key "test-key"
                  :model "claude-sonnet-4-20250514"
                  :thinking {:enabled true
                             :budget-tokens 4096}}
          model (core/create-model config)]
      (is (instance? AnthropicChatModel model)))))

(deftest test-anthropic-with-full-thinking-config
  (testing "Anthropic model with all thinking options"
    (let [config {:provider :anthropic
                  :api-key "test-key"
                  :model "claude-sonnet-4-20250514"
                  :thinking {:enabled true
                             :budget-tokens 8192
                             :return true
                             :send true}}
          model (core/create-model config)]
      (is (instance? AnthropicChatModel model)))))

(deftest test-anthropic-with-thinking-helper
  (testing "with-thinking helper for Anthropic"
    (let [config (-> {:provider :anthropic :api-key "test-key"}
                     (core/with-thinking {:enabled true
                                          :budget-tokens 4096
                                          :return true}))
          model (core/create-model config)]
      (is (instance? AnthropicChatModel model))
      (is (= {:enabled true :budget-tokens 4096 :return true}
             (:thinking config))))))

(deftest test-anthropic-without-thinking
  (testing "Anthropic model without thinking config still works"
    (let [config {:provider :anthropic
                  :api-key "test-key"
                  :model "claude-3-5-sonnet-20241022"}
          model (core/create-model config)]
      (is (instance? AnthropicChatModel model)))))

;; =============================================================================
;; Google Gemini Thinking Tests
;; =============================================================================

(deftest test-gemini-with-thinking-enabled
  (testing "Gemini model with thinking enabled"
    (let [config {:provider :google-ai-gemini
                  :api-key "test-key"
                  :model "gemini-2.5-flash"
                  :thinking {:enabled true}}
          model (core/create-model config)]
      (is (instance? GoogleAiGeminiChatModel model)))))

(deftest test-gemini-with-thinking-budget
  (testing "Gemini model with thinking budget tokens"
    (let [config {:provider :google-ai-gemini
                  :api-key "test-key"
                  :model "gemini-2.5-flash"
                  :thinking {:enabled true
                             :budget-tokens 4096}}
          model (core/create-model config)]
      (is (instance? GoogleAiGeminiChatModel model)))))

(deftest test-gemini-with-thinking-effort
  (testing "Gemini model with effort level (converted to budget)"
    (let [config {:provider :google-ai-gemini
                  :api-key "test-key"
                  :model "gemini-2.5-pro"
                  :thinking {:enabled true
                             :effort :high}}
          model (core/create-model config)]
      (is (instance? GoogleAiGeminiChatModel model)))))

(deftest test-gemini-with-full-thinking-config
  (testing "Gemini model with all thinking options"
    (let [config {:provider :google-ai-gemini
                  :api-key "test-key"
                  :model "gemini-2.5-flash"
                  :thinking {:enabled true
                             :budget-tokens 8192
                             :return true
                             :send true}}
          model (core/create-model config)]
      (is (instance? GoogleAiGeminiChatModel model)))))

(deftest test-gemini-with-thinking-helper
  (testing "with-thinking helper for Gemini"
    (let [config (-> {:provider :google-ai-gemini :api-key "test-key" :model "gemini-2.5-flash"}
                     (core/with-thinking {:enabled true
                                          :effort :medium
                                          :return true}))
          model (core/create-model config)]
      (is (instance? GoogleAiGeminiChatModel model))
      (is (= {:enabled true :effort :medium :return true}
             (:thinking config))))))

(deftest test-gemini-without-thinking
  (testing "Gemini model without thinking config still works"
    (let [config {:provider :google-ai-gemini
                  :api-key "test-key"
                  :model "gemini-1.5-flash"}
          model (core/create-model config)]
      (is (instance? GoogleAiGeminiChatModel model)))))

;; =============================================================================
;; Helper Function Tests
;; =============================================================================

(deftest test-with-thinking-adds-config
  (testing "with-thinking adds :thinking key to config"
    (let [base-config {:provider :openai :api-key "test"}
          result (core/with-thinking base-config {:effort :high})]
      (is (= {:effort :high} (:thinking result)))
      (is (= :openai (:provider result)))
      (is (= "test" (:api-key result))))))

(deftest test-with-thinking-in-threading
  (testing "with-thinking works in threading macro"
    (let [config (-> {:provider :anthropic :api-key "test"}
                     (core/with-model "claude-sonnet-4-20250514")
                     (core/with-thinking {:enabled true :budget-tokens 2048})
                     (core/with-temperature 0.5))]
      (is (= "claude-sonnet-4-20250514" (:model config)))
      (is (= {:enabled true :budget-tokens 2048} (:thinking config)))
      (is (= 0.5 (:temperature config))))))

;; =============================================================================
;; Edge Cases
;; =============================================================================

(deftest test-thinking-with-nil-values
  (testing "Thinking config with nil values is handled"
    (let [config {:provider :anthropic
                  :api-key "test-key"
                  :thinking {:enabled true
                             :budget-tokens nil
                             :return nil}}
          model (core/create-model config)]
      (is (instance? AnthropicChatModel model)))))

(deftest test-thinking-with-empty-map
  (testing "Empty thinking config is handled"
    (let [config {:provider :openai
                  :api-key "test-key"
                  :thinking {}}
          model (core/create-model config)]
      (is (instance? OpenAiChatModel model)))))

(deftest test-thinking-combined-with-listeners
  (testing "Thinking config works alongside listeners"
    (let [config {:provider :anthropic
                  :api-key "test-key"
                  :thinking {:enabled true :budget-tokens 4096}
                  :listeners []}
          model (core/create-model config)]
      (is (instance? AnthropicChatModel model)))))
