(ns langchain4clj.presets
  "Model presets for common configurations.
   
   Provides pre-configured model settings that can be used directly
   or customized with overrides. Supports namespaced keywords for
   provider/model identification.
   
   Usage:
   ```clojure
   (require '[langchain4clj.presets :as presets])
   (require '[langchain4clj.core :as lc])
   
   ;; Get preset and create model
   (lc/create-model (presets/get-preset :openai/gpt-4o))
   
   ;; Override defaults
   (lc/create-model (presets/get-preset :anthropic/claude-sonnet-4
                                        {:temperature 0.5}))
   
   ;; List available presets
   (presets/available-presets)
   ```"
  (:import [dev.langchain4j.model.anthropic AnthropicChatModelName]))

;; ==============================================================================
;; Base Configurations
;; ==============================================================================

(def ^:private model-base
  "Base configuration for standard models"
  {:temperature 1.0
   :max-tokens 4096
   :max-retries 3
   :timeout 60000})

(def ^:private reasoning-model-base
  "Base configuration for reasoning/thinking models"
  {:temperature 1.0
   :max-tokens 8192
   :max-retries 3
   :timeout 120000})

(def ^:private thinking-base
  "Common thinking configuration for reasoning models"
  {:enabled true
   :return true
   :send true
   :effort :medium})

;; ==============================================================================
;; Model Presets Registry
;; ==============================================================================

(def presets
  "Registry of model presets keyed by namespaced keyword.
   The namespace indicates the provider (:openai, :anthropic, :google)."
  {;; ==========================================================================
   ;; OpenAI Models
   ;; ==========================================================================
   :openai/gpt-4o
   (merge model-base
          {:provider :openai
           :model "gpt-4o"})

   :openai/gpt-4o-mini
   (merge model-base
          {:provider :openai
           :model "gpt-4o-mini"})

   :openai/gpt-4-1
   (merge model-base
          {:provider :openai
           :model "gpt-4.1"})

   :openai/gpt-4-1-mini
   (merge model-base
          {:provider :openai
           :model "gpt-4.1-mini"})

   :openai/gpt-4-1-nano
   (merge model-base
          {:provider :openai
           :model "gpt-4.1-nano"})

   :openai/o1
   (merge reasoning-model-base
          {:provider :openai
           :model "o1"
           :thinking {:effort :medium}})

   :openai/o1-mini
   (merge reasoning-model-base
          {:provider :openai
           :model "o1-mini"
           :thinking {:effort :medium}})

   :openai/o3
   (merge reasoning-model-base
          {:provider :openai
           :model "o3"
           :thinking {:effort :medium}})

   :openai/o3-mini
   (merge reasoning-model-base
          {:provider :openai
           :model "o3-mini"
           :thinking {:effort :medium}})

   :openai/o4-mini
   (merge model-base
          {:provider :openai
           :model "o4-mini"})

   :openai/o4-mini-reasoning
   (merge reasoning-model-base
          {:provider :openai
           :model "o4-mini"
           :thinking {:effort :medium}})

   ;; GPT-5 family (reasoning models with thinking)
   :openai/gpt-5
   (merge model-base
          {:provider :openai
           :model "gpt-5-2025-08-07"
           :thinking {:effort :medium}})

   :openai/gpt-5-mini
   (merge model-base
          {:provider :openai
           :model "gpt-5-mini-2025-08-07"
           :thinking {:effort :medium}})

   :openai/gpt-5-nano
   (merge model-base
          {:provider :openai
           :model "gpt-5-nano-2025-08-07"
           :thinking {:effort :medium}})

   ;; O3 Pro
   :openai/o3-pro
   (merge reasoning-model-base
          {:provider :openai
           :model "o3-pro-2025-06-10"
           :thinking {:effort :medium}})

   ;; ==========================================================================
   ;; Anthropic Models
   ;; ==========================================================================
   :anthropic/claude-3-5-haiku
   (merge model-base
          {:provider :anthropic
           :model AnthropicChatModelName/CLAUDE_3_5_HAIKU_20241022
           :max-tokens 2048})

   :anthropic/claude-haiku-4-5
   (merge model-base
          {:provider :anthropic
           :model AnthropicChatModelName/CLAUDE_HAIKU_4_5_20251001
           :max-tokens 2048})

   :anthropic/claude-sonnet-4
   (merge model-base
          {:provider :anthropic
           :model AnthropicChatModelName/CLAUDE_SONNET_4_20250514})

   :anthropic/claude-sonnet-4-reasoning
   (merge reasoning-model-base
          {:provider :anthropic
           :model AnthropicChatModelName/CLAUDE_SONNET_4_20250514
           :thinking (merge thinking-base {:budget-tokens 4096})})

   :anthropic/claude-sonnet-4-5
   (merge model-base
          {:provider :anthropic
           :model AnthropicChatModelName/CLAUDE_SONNET_4_5_20250929})

   :anthropic/claude-sonnet-4-5-reasoning
   (merge reasoning-model-base
          {:provider :anthropic
           :model AnthropicChatModelName/CLAUDE_SONNET_4_5_20250929
           :thinking (merge thinking-base {:budget-tokens 4096})})

   :anthropic/claude-opus-4
   (merge model-base
          {:provider :anthropic
           :model AnthropicChatModelName/CLAUDE_OPUS_4_20250514})

   :anthropic/claude-opus-4-reasoning
   (merge reasoning-model-base
          {:provider :anthropic
           :model AnthropicChatModelName/CLAUDE_OPUS_4_20250514
           :thinking (merge thinking-base {:budget-tokens 4096})})

   :anthropic/claude-opus-4-1
   (merge model-base
          {:provider :anthropic
           :model AnthropicChatModelName/CLAUDE_OPUS_4_1_20250805})

   :anthropic/claude-opus-4-1-reasoning
   (merge reasoning-model-base
          {:provider :anthropic
           :model AnthropicChatModelName/CLAUDE_OPUS_4_1_20250805
           :thinking (merge thinking-base {:budget-tokens 4096})})

   ;; ==========================================================================
   ;; Google Gemini Models
   ;; ==========================================================================
   :google/gemini-2-5-flash
   (merge model-base
          {:provider :google-ai-gemini
           :model "gemini-2.5-flash"})

   :google/gemini-2-5-flash-lite
   (merge model-base
          {:provider :google-ai-gemini
           :model "gemini-2.5-flash-lite"})

   :google/gemini-2-5-pro
   (merge model-base
          {:provider :google-ai-gemini
           :model "gemini-2.5-pro"})

   :google/gemini-2-5-flash-reasoning
   (merge reasoning-model-base
          {:provider :google-ai-gemini
           :model "gemini-2.5-flash"
           :thinking thinking-base})

   :google/gemini-2-5-pro-reasoning
   (merge reasoning-model-base
          {:provider :google-ai-gemini
           :model "gemini-2.5-pro"
           :thinking thinking-base})

   :google/gemini-1-5-flash
   (merge model-base
          {:provider :google-ai-gemini
           :model "gemini-1.5-flash"})

   :google/gemini-1-5-pro
   (merge model-base
          {:provider :google-ai-gemini
           :model "gemini-1.5-pro"})})

;; ==============================================================================
;; Public API
;; ==============================================================================

(defn get-preset
  "Get a model preset by key, optionally merging overrides.
   
   Args:
   - preset-key: Namespaced keyword like :openai/gpt-4o
   - overrides: Optional map of configuration overrides
   
   Returns the preset config merged with overrides, or throws if not found.
   
   Examples:
   ```clojure
   (get-preset :openai/gpt-4o)
   ;; => {:provider :openai :model \"gpt-4o\" :temperature 1.0 ...}
   
   (get-preset :anthropic/claude-sonnet-4 {:temperature 0.5})
   ;; => {:provider :anthropic ... :temperature 0.5}
   ```"
  ([preset-key]
   (get-preset preset-key {}))
  ([preset-key overrides]
   (if-let [preset (get presets preset-key)]
     (merge preset overrides)
     (throw (ex-info (str "Unknown preset: " preset-key)
                     {:preset-key preset-key
                      :available-presets (keys presets)})))))

(defn available-presets
  "Returns a list of all available preset keys."
  []
  (keys presets))

(defn presets-by-provider
  "Returns presets grouped by provider.
   
   Example:
   ```clojure
   (presets-by-provider)
   ;; => {:openai [:openai/gpt-4o :openai/gpt-4o-mini ...]
   ;;     :anthropic [:anthropic/claude-sonnet-4 ...]
   ;;     :google [:google/gemini-2-5-flash ...]}
   ```"
  []
  (group-by #(-> % namespace keyword) (keys presets)))

(defn get-provider
  "Extracts the provider from a preset key.
   
   Example:
   ```clojure
   (get-provider :openai/gpt-4o) ;; => :openai
   (get-provider :anthropic/claude-sonnet-4) ;; => :anthropic
   ```"
  [preset-key]
  (some-> preset-key namespace keyword))
