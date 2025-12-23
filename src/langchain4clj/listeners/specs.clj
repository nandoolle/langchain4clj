(ns langchain4clj.listeners.specs
  "Specs and enums for listener context data structures.
   
   Provides validated keyword enums for:
   - Message types (:user, :system, :ai, :tool-execution-result)
   - Content types (:text, :image, :audio, :document)
   - Providers (:openai, :anthropic, :google-ai-gemini, etc.)
   - Finish reasons (:end-turn, :stop, :max-tokens, etc.)
   - Model names by provider"
  (:require [clojure.spec.alpha :as s]))

;; =============================================================================
;; Message Types
;; =============================================================================

(s/def ::message-type
  #{:user :system :ai :tool-execution-result})

;; =============================================================================
;; Content Types
;; =============================================================================

(s/def ::content-type
  #{:text :image :audio :document})

;; =============================================================================
;; Providers
;; =============================================================================

(s/def ::provider
  #{:openai :anthropic :google-ai-gemini :vertex-ai-gemini :ollama :mistral})

;; =============================================================================
;; Finish Reasons
;; =============================================================================

(s/def ::finish-reason
  #{:end-turn :stop :max-tokens :tool-calls :content-filter :error :length})

;; =============================================================================
;; Model Names by Provider
;; =============================================================================

(s/def ::openai-model
  #{:gpt-4o :gpt-4o-mini :gpt-4-turbo :gpt-4 :gpt-3.5-turbo
    :o1 :o1-mini :o1-preview :o3 :o3-mini})

(s/def ::anthropic-model
  #{:claude-opus-4 :claude-sonnet-4
    :claude-3-5-sonnet :claude-3-5-haiku
    :claude-3-opus :claude-3-sonnet :claude-3-haiku})

(s/def ::google-model
  #{:gemini-2.5-flash :gemini-2.5-pro
    :gemini-2.0-flash :gemini-2.0-flash-lite
    :gemini-1.5-flash :gemini-1.5-pro})

(s/def ::ollama-model
  #{:llama3.1 :llama3.2 :mistral :codellama :gemma :phi})

(s/def ::mistral-model
  #{:mistral-large :mistral-medium :mistral-small :codestral :mixtral})

(s/def ::model-name
  (s/or :openai ::openai-model
        :anthropic ::anthropic-model
        :google ::google-model
        :ollama ::ollama-model
        :mistral ::mistral-model
        :custom string?))

;; =============================================================================
;; Token Usage
;; =============================================================================

(s/def ::input-tokens (s/and int? #(>= % 0)))
(s/def ::output-tokens (s/and int? #(>= % 0)))
(s/def ::total-tokens (s/and int? #(>= % 0)))

(s/def ::token-usage
  (s/keys :req-un [::input-tokens ::output-tokens ::total-tokens]))

;; =============================================================================
;; Message Content
;; =============================================================================

(s/def ::text string?)

(s/def ::content-item
  (s/keys :req-un [::content-type]
          :opt-un [::text]))

(s/def ::contents
  (s/coll-of ::content-item :kind vector?))

;; =============================================================================
;; Messages
;; =============================================================================

(s/def ::tool-id string?)
(s/def ::tool-name (s/or :keyword keyword? :string string?))
(s/def ::arguments map?)

(s/def ::tool-execution-request
  (s/keys :req-un [::tool-id ::tool-name ::arguments]))

(s/def ::tool-execution-requests
  (s/coll-of ::tool-execution-request :kind vector?))

(s/def ::user-message
  (s/and (s/keys :req-un [::message-type ::contents])
         #(= :user (:message-type %))))

(s/def ::system-message
  (s/and (s/keys :req-un [::message-type ::text])
         #(= :system (:message-type %))))

(s/def ::ai-message
  (s/and (s/keys :req-un [::message-type]
                 :opt-un [::text ::tool-execution-requests])
         #(= :ai (:message-type %))))

(s/def ::tool-result-message
  (s/and (s/keys :req-un [::message-type ::tool-id ::tool-name ::text])
         #(= :tool-execution-result (:message-type %))))

(s/def ::message
  (s/or :user ::user-message
        :system ::system-message
        :ai ::ai-message
        :tool-result ::tool-result-message))

(s/def ::messages
  (s/coll-of ::message :kind vector?))

;; =============================================================================
;; Request Parameters
;; =============================================================================

(s/def ::temperature (s/and number? #(<= 0 % 2)))
(s/def ::max-output-tokens (s/and int? pos?))
(s/def ::top-p (s/and number? #(<= 0 % 1)))
(s/def ::top-k (s/and int? pos?))
(s/def ::tool-specifications vector?)

(s/def ::parameters
  (s/keys :req-un [::model-name]
          :opt-un [::temperature ::max-output-tokens ::top-p ::top-k
                   ::tool-specifications]))

;; =============================================================================
;; Response Metadata
;; =============================================================================

(s/def ::response-id string?)

(s/def ::response-metadata
  (s/keys :req-un [::model-name ::finish-reason ::token-usage]
          :opt-un [::response-id]))

;; =============================================================================
;; Context Structures
;; =============================================================================

(s/def ::attributes map?)
(s/def ::raw-context any?)

(s/def ::request-context
  (s/keys :req-un [::messages ::parameters ::provider]
          :opt-un [::attributes ::raw-context]))

(s/def ::response-context
  (s/keys :req-un [::ai-message ::response-metadata ::provider]
          :opt-un [::request-context ::attributes ::raw-context]))

(s/def ::error-message string?)
(s/def ::error-type string?)
(s/def ::error-cause (s/nilable string?))

(s/def ::error
  (s/keys :req-un [::error-message ::error-type]
          :opt-un [::error-cause]))

(s/def ::error-context
  (s/keys :req-un [::error ::provider]
          :opt-un [::request-context ::attributes]))
