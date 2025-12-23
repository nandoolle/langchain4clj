(ns langchain4clj.presets-test
  (:require [clojure.test :refer [deftest is testing]]
            [langchain4clj.presets :as presets]))

;; ============================================================================
;; Preset Registry Tests
;; ============================================================================

(deftest test-presets-registry-exists
  (testing "presets registry contains expected models"
    (is (map? presets/presets))
    (is (pos? (count presets/presets)))))

(deftest test-available-presets
  (testing "available-presets returns all preset keys"
    (let [available (presets/available-presets)]
      (is (seq available))
      (is (every? keyword? available))
      (is (every? namespace available) "All preset keys should be namespaced"))))

;; ============================================================================
;; OpenAI Presets Tests
;; ============================================================================

(deftest test-openai-presets-exist
  (testing "OpenAI presets are defined"
    (is (contains? presets/presets :openai/gpt-4o))
    (is (contains? presets/presets :openai/gpt-4o-mini))
    (is (contains? presets/presets :openai/o1))
    (is (contains? presets/presets :openai/o3-mini))))

(deftest test-openai-preset-structure
  (testing "OpenAI presets have correct structure"
    (let [preset (presets/get-preset :openai/gpt-4o)]
      (is (= :openai (:provider preset)))
      (is (= "gpt-4o" (:model preset)))
      (is (number? (:temperature preset)))
      (is (number? (:max-tokens preset)))
      (is (number? (:timeout preset))))))

(deftest test-openai-reasoning-presets
  (testing "OpenAI reasoning presets include thinking config"
    (let [o1-preset (presets/get-preset :openai/o1)
          o3-mini-preset (presets/get-preset :openai/o3-mini)]
      (is (contains? o1-preset :thinking))
      (is (contains? o3-mini-preset :thinking))
      (is (keyword? (get-in o1-preset [:thinking :effort]))))))

;; ============================================================================
;; Anthropic Presets Tests
;; ============================================================================

(deftest test-anthropic-presets-exist
  (testing "Anthropic presets are defined"
    (is (contains? presets/presets :anthropic/claude-sonnet-4))
    (is (contains? presets/presets :anthropic/claude-opus-4))
    (is (contains? presets/presets :anthropic/claude-3-5-haiku))))

(deftest test-anthropic-preset-structure
  (testing "Anthropic presets have correct structure"
    (let [preset (presets/get-preset :anthropic/claude-sonnet-4)]
      (is (= :anthropic (:provider preset)))
      (is (some? (:model preset)))
      (is (number? (:temperature preset))))))

(deftest test-anthropic-reasoning-presets
  (testing "Anthropic reasoning presets include thinking config"
    (let [preset (presets/get-preset :anthropic/claude-sonnet-4-reasoning)]
      (is (contains? preset :thinking))
      (is (true? (get-in preset [:thinking :enabled])))
      (is (number? (get-in preset [:thinking :budget-tokens]))))))

;; ============================================================================
;; Google Presets Tests
;; ============================================================================

(deftest test-google-presets-exist
  (testing "Google presets are defined"
    (is (contains? presets/presets :google/gemini-2-5-flash))
    (is (contains? presets/presets :google/gemini-2-5-pro))
    (is (contains? presets/presets :google/gemini-1-5-flash))))

(deftest test-google-preset-structure
  (testing "Google presets have correct structure"
    (let [preset (presets/get-preset :google/gemini-2-5-flash)]
      (is (= :google-ai-gemini (:provider preset)))
      (is (string? (:model preset)))
      (is (number? (:temperature preset))))))

(deftest test-google-reasoning-presets
  (testing "Google reasoning presets include thinking config"
    (let [preset (presets/get-preset :google/gemini-2-5-flash-reasoning)]
      (is (contains? preset :thinking))
      (is (true? (get-in preset [:thinking :enabled]))))))

;; ============================================================================
;; get-preset Function Tests
;; ============================================================================

(deftest test-get-preset-returns-config
  (testing "get-preset returns full configuration"
    (let [preset (presets/get-preset :openai/gpt-4o)]
      (is (map? preset))
      (is (contains? preset :provider))
      (is (contains? preset :model))
      (is (contains? preset :temperature)))))

(deftest test-get-preset-with-overrides
  (testing "get-preset merges overrides"
    (let [preset (presets/get-preset :openai/gpt-4o {:temperature 0.5
                                                     :api-key "test-key"})]
      (is (= 0.5 (:temperature preset)))
      (is (= "test-key" (:api-key preset)))
      (is (= "gpt-4o" (:model preset)) "Original values should be preserved"))))

(deftest test-get-preset-unknown-throws
  (testing "get-preset throws for unknown preset"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
                          #"Unknown preset"
                          (presets/get-preset :unknown/model)))))

;; ============================================================================
;; presets-by-provider Tests
;; ============================================================================

(deftest test-presets-by-provider
  (testing "presets-by-provider groups correctly"
    (let [by-provider (presets/presets-by-provider)]
      (is (map? by-provider))
      (is (contains? by-provider :openai))
      (is (contains? by-provider :anthropic))
      (is (contains? by-provider :google))
      (is (every? #(= "openai" (namespace %)) (:openai by-provider)))
      (is (every? #(= "anthropic" (namespace %)) (:anthropic by-provider)))
      (is (every? #(= "google" (namespace %)) (:google by-provider))))))

;; ============================================================================
;; get-provider Tests
;; ============================================================================

(deftest test-get-provider
  (testing "get-provider extracts provider from preset key"
    (is (= :openai (presets/get-provider :openai/gpt-4o)))
    (is (= :anthropic (presets/get-provider :anthropic/claude-sonnet-4)))
    (is (= :google (presets/get-provider :google/gemini-2-5-flash)))))

(deftest test-get-provider-nil
  (testing "get-provider returns nil for non-namespaced keywords"
    (is (nil? (presets/get-provider :no-namespace)))))

;; ============================================================================
;; Integration with core Tests
;; ============================================================================

(deftest test-preset-usable-with-create-model
  (testing "presets can be used with langchain4clj.core/create-model"
    (let [preset (presets/get-preset :openai/gpt-4o {:api-key "test-key"})]
      ;; Just verify the preset has the right structure for create-model
      (is (contains? preset :provider))
      (is (contains? preset :model))
      (is (contains? preset :api-key)))))
