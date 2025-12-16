(ns langchain4clj.specs
  "Clojure spec definitions for public APIs."
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]))

(s/def ::provider
  #{:openai :anthropic :google-ai-gemini :vertex-ai-gemini :ollama})

;; Model configuration
(s/def ::api-key
  (s/and string? #(not (str/blank? %))))

(s/def ::model
  (s/and string? #(not (str/blank? %))))

(s/def ::temperature
  (s/and number? #(<= 0 % 2)))

(s/def ::timeout
  (s/and int? pos?))

(s/def ::max-tokens
  (s/and int? pos?))

(s/def ::top-p
  (s/and number? #(<= 0 % 1)))

(s/def ::top-k
  (s/and int? pos?))

(s/def ::log-requests?
  boolean?)

(s/def ::log-responses?
  boolean?)

(s/def ::max-retries
  (s/and int? #(>= % 0)))

(s/def ::message
  (s/or :string string?
        :user-message #(instance? dev.langchain4j.data.message.UserMessage %)
        :system-message #(instance? dev.langchain4j.data.message.SystemMessage %)))

(s/def ::system-message
  string?)

(s/def ::messages
  (s/coll-of ::message :kind vector? :min-count 1))

(s/def ::openai-config
  (s/keys :req-un [::provider ::api-key]
          :opt-un [::model ::temperature ::timeout ::max-tokens ::top-p
                   ::log-requests? ::log-responses? ::max-retries]))

(s/def ::anthropic-config
  (s/keys :req-un [::provider ::api-key]
          :opt-un [::model ::temperature ::timeout ::max-tokens ::top-p ::top-k]))

(s/def ::google-ai-config
  (s/keys :req-un [::provider ::api-key]
          :opt-un [::model ::temperature ::timeout ::max-output-tokens ::top-p ::top-k]))

(s/def ::vertex-ai-config
  (s/keys :req-un [::provider ::project ::location]
          :opt-un [::model ::temperature ::max-output-tokens ::top-p ::top-k]))

(s/def ::base-url
  (s/and string? #(re-matches #"https?://.*" %)))

(s/def ::ollama-config
  (s/keys :req-un [::provider]
          :opt-un [::base-url ::model ::temperature ::timeout ::top-k ::top-p
                   ::seed ::num-predict ::stop ::log-requests? ::log-responses?
                   ::max-retries]))

(s/def ::model-config
  (s/or :openai ::openai-config
        :anthropic ::anthropic-config
        :google-ai ::google-ai-config
        :vertex-ai ::vertex-ai-config
        :ollama ::ollama-config))

(s/def ::tool-name
  keyword?)

(s/def ::tool-description
  string?)

(s/def ::tool-fn
  fn?)

(s/def ::tool-definition
  (s/keys :req-un [::tool-name ::tool-description ::tool-fn]
          :opt-un [::parameters]))

(s/def ::parameters
  (s/or :spec qualified-keyword? ;; clojure.spec
        :schema map?)) ;; malli or custom schema

(s/def ::schema
  (s/or :simple-map (s/map-of keyword? keyword?)
        :nested-map (s/map-of keyword? (s/or :keyword keyword?
                                             :vector vector?
                                             :map map?))))

(s/def ::output-format
  #{:edn :json-str})

(s/def ::strategy
  #{:json-mode :tool-forcing :validation :auto})

(s/def ::validate?
  boolean?)

(s/def ::max-attempts
  (s/and int? pos? #(<= % 10)))

(s/def ::structured-output-opts
  (s/keys :req-un [::schema]
          :opt-un [::strategy ::output-format ::validate? ::max-attempts]))

(s/def ::failure-threshold
  (s/and int? pos?))

(s/def ::success-threshold
  (s/and int? pos?))

(s/def ::half-open-timeout
  (s/and int? pos?))

(s/def ::circuit-breaker-config
  (s/keys :opt-un [::failure-threshold ::success-threshold ::half-open-timeout]))

(s/def ::fallback-model
  #(instance? dev.langchain4j.model.chat.ChatModel %))

(s/def ::failover-config
  (s/keys :req-un [::fallback-model]
          :opt-un [::circuit-breaker-config]))

(s/def ::tools
  (s/coll-of ::tool-definition :kind vector?))

(s/def ::max-iterations
  (s/and int? pos? #(<= % 50)))

(s/def ::agent-config
  (s/keys :opt-un [::tools ::max-iterations ::system-message]))

(s/def ::on-token
  fn?)

(s/def ::on-complete
  fn?)

(s/def ::on-error
  fn?)

(s/def ::streaming-callbacks
  (s/keys :req-un [::on-token]
          :opt-un [::on-complete ::on-error]))

(s/def ::memory-id
  (s/or :string string?
        :keyword keyword?))

(s/def ::max-messages
  (s/and int? pos?))

(s/def ::memory-config
  (s/keys :opt-un [::memory-id ::max-messages]))

(s/def ::chat-response
  string?)

(s/def ::chat-model
  #(instance? dev.langchain4j.model.chat.ChatModel %))

(s/def ::streaming-chat-model
  #(instance? dev.langchain4j.model.chat.StreamingChatModel %))
