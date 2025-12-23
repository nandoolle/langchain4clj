(ns langchain4clj.listeners.types-test
  (:require [clojure.test :refer [deftest is testing]]
            [langchain4clj.listeners.types :as types]))

;; =============================================================================
;; ->str Tests
;; =============================================================================

(deftest ->str-test
  (testing "converts keywords using mapping"
    (is (= "USER" (types/->str types/message-types :user)))
    (is (= "SYSTEM" (types/->str types/message-types :system)))
    (is (= "AI" (types/->str types/message-types :ai)))
    (is (= "TOOL_EXECUTION_RESULT" (types/->str types/message-types :tool-execution-result))))

  (testing "passes through strings unchanged"
    (is (= "USER" (types/->str types/message-types "USER")))
    (is (= "custom-string" (types/->str types/message-types "custom-string"))))

  (testing "returns keyword name if not in mapping"
    (is (= "unknown" (types/->str types/message-types :unknown)))
    (is (= "custom-type" (types/->str {} :custom-type))))

  (testing "works with all mappings"
    (is (= "ANTHROPIC" (types/->str types/providers :anthropic)))
    (is (= "END_TURN" (types/->str types/finish-reasons :end-turn)))
    (is (= "gpt-4o" (types/->str types/all-models :gpt-4o)))
    (is (= "claude-sonnet-4-20250514" (types/->str types/all-models :claude-sonnet-4)))))

;; =============================================================================
;; ->keyword Tests
;; =============================================================================

(deftest ->keyword-test
  (testing "converts strings to keywords using mapping"
    (is (= :user (types/->keyword types/message-types "USER")))
    (is (= :system (types/->keyword types/message-types "SYSTEM")))
    (is (= :ai (types/->keyword types/message-types "AI"))))

  (testing "passes through keywords unchanged"
    (is (= :user (types/->keyword types/message-types :user)))
    (is (= :unknown (types/->keyword types/message-types :unknown))))

  (testing "returns nil if not in mapping"
    (is (nil? (types/->keyword types/message-types "UNKNOWN")))
    (is (nil? (types/->keyword {} "anything"))))

  (testing "works with all mappings"
    (is (= :anthropic (types/->keyword types/providers "ANTHROPIC")))
    (is (= :end-turn (types/->keyword types/finish-reasons "END_TURN")))
    (is (= :gpt-4o (types/->keyword types/all-models "gpt-4o")))))

;; =============================================================================
;; ->str-or-throw Tests
;; =============================================================================

(deftest ->str-or-throw-test
  (testing "converts valid keywords"
    (is (= "USER" (types/->str-or-throw types/message-types :user))))

  (testing "passes through strings"
    (is (= "custom" (types/->str-or-throw types/message-types "custom"))))

  (testing "throws on unknown keyword"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Unknown key"
                          (types/->str-or-throw types/message-types :unknown)))))

;; =============================================================================
;; Model Mappings Tests
;; =============================================================================

(deftest openai-models-test
  (testing "OpenAI model mappings"
    (is (= "gpt-4o" (types/->str types/openai-models :gpt-4o)))
    (is (= "gpt-4o-mini" (types/->str types/openai-models :gpt-4o-mini)))
    (is (= "o1" (types/->str types/openai-models :o1)))
    (is (= "o3-mini" (types/->str types/openai-models :o3-mini)))))

(deftest anthropic-models-test
  (testing "Anthropic model mappings"
    (is (= "claude-opus-4-20250514" (types/->str types/anthropic-models :claude-opus-4)))
    (is (= "claude-sonnet-4-20250514" (types/->str types/anthropic-models :claude-sonnet-4)))
    (is (= "claude-3-5-sonnet-20241022" (types/->str types/anthropic-models :claude-3-5-sonnet)))
    (is (= "claude-3-5-haiku-20241022" (types/->str types/anthropic-models :claude-3-5-haiku)))))

(deftest google-models-test
  (testing "Google model mappings"
    (is (= "gemini-2.5-flash" (types/->str types/google-models :gemini-2.5-flash)))
    (is (= "gemini-2.5-pro" (types/->str types/google-models :gemini-2.5-pro)))
    (is (= "gemini-1.5-flash" (types/->str types/google-models :gemini-1.5-flash)))))

(deftest all-models-test
  (testing "all-models contains all provider models"
    (is (= "gpt-4o" (types/->str types/all-models :gpt-4o)))
    (is (= "claude-sonnet-4-20250514" (types/->str types/all-models :claude-sonnet-4)))
    (is (= "gemini-2.5-flash" (types/->str types/all-models :gemini-2.5-flash)))
    (is (= "llama3.1" (types/->str types/all-models :llama3.1)))
    (is (= "mistral-large-latest" (types/->str types/all-models :mistral-large)))))

;; =============================================================================
;; context->java Tests
;; =============================================================================

(deftest context->java-test
  (testing "converts all known keys in nested structure"
    (let [input {:message-type :user
                 :provider :anthropic
                 :contents [{:content-type :text :text "Hello"}]}
          expected {:message-type "USER"
                    :provider "ANTHROPIC"
                    :contents [{:content-type "TEXT" :text "Hello"}]}]
      (is (= expected (types/context->java input)))))

  (testing "converts model-name and finish-reason"
    (let [input {:model-name :claude-sonnet-4
                 :finish-reason :end-turn}
          result (types/context->java input)]
      (is (= "claude-sonnet-4-20250514" (:model-name result)))
      (is (= "END_TURN" (:finish-reason result)))))

  (testing "leaves unknown keys unchanged"
    (let [input {:message-type :user
                 :custom-field "unchanged"
                 :nested {:also "unchanged"}}
          result (types/context->java input)]
      (is (= "USER" (:message-type result)))
      (is (= "unchanged" (:custom-field result)))
      (is (= {:also "unchanged"} (:nested result)))))

  (testing "handles complex nested structure"
    (let [input {:messages [{:message-type :system :text "Be helpful"}
                            {:message-type :user
                             :contents [{:content-type :text :text "Hi"}]}]
                 :parameters {:model-name :gpt-4o :temperature 0.7}
                 :provider :openai}
          result (types/context->java input)]
      (is (= "SYSTEM" (get-in result [:messages 0 :message-type])))
      (is (= "USER" (get-in result [:messages 1 :message-type])))
      (is (= "TEXT" (get-in result [:messages 1 :contents 0 :content-type])))
      (is (= "gpt-4o" (get-in result [:parameters :model-name])))
      (is (= "OPENAI" (:provider result))))))

;; =============================================================================
;; java->context Tests
;; =============================================================================

(deftest java->context-test
  (testing "converts all known string values to keywords"
    (let [input {:message-type "USER"
                 :provider "ANTHROPIC"
                 :contents [{:content-type "TEXT" :text "Hello"}]}
          expected {:message-type :user
                    :provider :anthropic
                    :contents [{:content-type :text :text "Hello"}]}]
      (is (= expected (types/java->context input)))))

  (testing "converts model-name and finish-reason"
    (let [input {:model-name "gpt-4o"
                 :finish-reason "END_TURN"}
          result (types/java->context input)]
      (is (= :gpt-4o (:model-name result)))
      (is (= :end-turn (:finish-reason result)))))

  (testing "leaves unknown string values as-is"
    (let [input {:message-type "USER"
                 :finish-reason "CUSTOM_REASON"}
          result (types/java->context input)]
      (is (= :user (:message-type result)))
      (is (nil? (:finish-reason result))))))

;; =============================================================================
;; Round-trip Tests
;; =============================================================================

(deftest roundtrip-test
  (testing "context survives round-trip conversion for known values"
    (let [original {:message-type :user
                    :provider :anthropic
                    :contents [{:content-type :text :text "Hello"}]
                    :parameters {:model-name :gpt-4o}
                    :finish-reason :end-turn}
          result (-> original
                     types/context->java
                     types/java->context)]
      (is (= :user (:message-type result)))
      (is (= :anthropic (:provider result)))
      (is (= :text (get-in result [:contents 0 :content-type])))
      (is (= :gpt-4o (get-in result [:parameters :model-name])))
      (is (= :end-turn (:finish-reason result))))))
