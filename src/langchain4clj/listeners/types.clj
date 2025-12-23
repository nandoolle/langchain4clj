(ns langchain4clj.listeners.types
  "Type conversions between Clojure keywords and Java/API string formats.
   
   Provides bidirectional mappings for:
   - Message types (keyword <-> \"USER\", \"SYSTEM\", etc.)
   - Providers (keyword <-> \"OPENAI\", \"ANTHROPIC\", etc.)
   - Finish reasons (keyword <-> \"END_TURN\", \"STOP\", etc.)
   - Model names (keyword <-> actual API model strings)
   
   Usage:
   (->str message-types :user)        ; => \"USER\"
   (->keyword message-types \"USER\")  ; => :user"
  (:require [clojure.set :as set]
            [clojure.walk :as walk]))

;; =============================================================================
;; Message Types
;; =============================================================================

(def message-types
  {:user "USER"
   :system "SYSTEM"
   :ai "AI"
   :tool-execution-result "TOOL_EXECUTION_RESULT"})

;; =============================================================================
;; Content Types
;; =============================================================================

(def content-types
  {:text "TEXT"
   :image "IMAGE"
   :audio "AUDIO"
   :document "DOCUMENT"})

;; =============================================================================
;; Providers
;; =============================================================================

(def providers
  {:openai "OPENAI"
   :anthropic "ANTHROPIC"
   :google-ai-gemini "GOOGLE_AI_GEMINI"
   :vertex-ai-gemini "VERTEX_AI_GEMINI"
   :ollama "OLLAMA"
   :mistral "MISTRAL"})

;; =============================================================================
;; Finish Reasons
;; =============================================================================

(def finish-reasons
  {:end-turn "END_TURN"
   :stop "STOP"
   :max-tokens "MAX_TOKENS"
   :tool-calls "TOOL_CALLS"
   :content-filter "CONTENT_FILTER"
   :error "ERROR"
   :length "LENGTH"})

;; =============================================================================
;; Model Names
;; =============================================================================

(def openai-models
  {:gpt-4o "gpt-4o"
   :gpt-4o-mini "gpt-4o-mini"
   :gpt-4-turbo "gpt-4-turbo"
   :gpt-4 "gpt-4"
   :gpt-3.5-turbo "gpt-3.5-turbo"
   :o1 "o1"
   :o1-mini "o1-mini"
   :o1-preview "o1-preview"
   :o3 "o3"
   :o3-mini "o3-mini"})

(def anthropic-models
  {:claude-opus-4 "claude-opus-4-20250514"
   :claude-sonnet-4 "claude-sonnet-4-20250514"
   :claude-3-5-sonnet "claude-3-5-sonnet-20241022"
   :claude-3-5-haiku "claude-3-5-haiku-20241022"
   :claude-3-opus "claude-3-opus-20240229"
   :claude-3-sonnet "claude-3-sonnet-20240229"
   :claude-3-haiku "claude-3-haiku-20240307"})

(def google-models
  {:gemini-2.5-flash "gemini-2.5-flash"
   :gemini-2.5-pro "gemini-2.5-pro"
   :gemini-2.0-flash "gemini-2.0-flash"
   :gemini-2.0-flash-lite "gemini-2.0-flash-lite"
   :gemini-1.5-flash "gemini-1.5-flash"
   :gemini-1.5-pro "gemini-1.5-pro"})

(def ollama-models
  {:llama3.1 "llama3.1"
   :llama3.2 "llama3.2"
   :mistral "mistral"
   :codellama "codellama"
   :gemma "gemma"
   :phi "phi"})

(def mistral-models
  {:mistral-large "mistral-large-latest"
   :mistral-medium "mistral-medium-latest"
   :mistral-small "mistral-small-latest"
   :codestral "codestral-latest"
   :mixtral "open-mixtral-8x22b"})

(def all-models
  (merge openai-models
         anthropic-models
         google-models
         ollama-models
         mistral-models))

;; =============================================================================
;; Conversion Functions
;; =============================================================================

(defn ->str
  "Convert a keyword to its string representation using the given mapping.
   Returns the keyword's name if not found in mapping.
   Passes through strings unchanged.
   
   Examples:
   (->str message-types :user)     ; => \"USER\"
   (->str providers :anthropic)    ; => \"ANTHROPIC\"
   (->str all-models :gpt-4o)      ; => \"gpt-4o\"
   (->str {} :unknown)             ; => \"unknown\""
  [mapping k]
  (cond
    (string? k) k
    (keyword? k) (get mapping k (name k))
    :else (str k)))

(defn ->keyword
  "Convert a string to its keyword representation using the inverted mapping.
   Returns nil if not found.
   Passes through keywords unchanged.
   
   Examples:
   (->keyword message-types \"USER\")     ; => :user
   (->keyword providers \"ANTHROPIC\")    ; => :anthropic"
  [mapping s]
  (cond
    (keyword? s) s
    (string? s) (get (set/map-invert mapping) s)
    :else nil))

(defn ->str-or-throw
  "Like ->str but throws if keyword not found in mapping.
   Use when strict validation is required."
  [mapping k]
  (if (string? k)
    k
    (if-let [v (get mapping k)]
      v
      (throw (ex-info (str "Unknown key: " k " not in mapping")
                      {:key k :valid-keys (keys mapping)})))))

;; =============================================================================
;; Context Conversion
;; =============================================================================

(defn- convert-value
  "Convert a single value based on its key."
  [k v]
  (case k
    :message-type (->str message-types v)
    :content-type (->str content-types v)
    :provider (->str providers v)
    :finish-reason (->str finish-reasons v)
    :model-name (->str all-models v)
    v))

(defn context->java
  "Convert a context map from Clojure keywords to Java/API string format.
   Recursively walks the structure converting known keys.
   
   Example:
   (context->java {:message-type :user :provider :anthropic})
   ; => {:message-type \"USER\" :provider \"ANTHROPIC\"}"
  [ctx]
  (walk/postwalk
   (fn [x]
     (if (map-entry? x)
       (let [[k v] x]
         [k (convert-value k v)])
       x))
   ctx))

(defn- convert-value-to-keyword
  "Convert a single string value based on its key back to keyword."
  [k v]
  (case k
    :message-type (->keyword message-types v)
    :content-type (->keyword content-types v)
    :provider (->keyword providers v)
    :finish-reason (->keyword finish-reasons v)
    :model-name (->keyword all-models v)
    v))

(defn java->context
  "Convert a context map from Java/API string format to Clojure keywords.
   Recursively walks the structure converting known keys.
   
   Example:
   (java->context {:message-type \"USER\" :provider \"ANTHROPIC\"})
   ; => {:message-type :user :provider :anthropic}"
  [ctx]
  (walk/postwalk
   (fn [x]
     (if (map-entry? x)
       (let [[k v] x]
         [k (convert-value-to-keyword k v)])
       x))
   ctx))
