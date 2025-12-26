(ns langchain4clj.listeners.context
  "Explicit constructors for listener context data structures.
   
   This namespace provides constructor functions for building context maps
   used by ChatModelListeners. No defaults are provided - users must specify
   all required fields explicitly.
   
   Two approaches are supported:
   1. Direct map creation with keyword enums (validated by specs)
   2. Constructor functions for type-safe building
   
   Example - Direct map (power user):
   ```clojure
   {:messages [(user-message \"Hello\")]
    :parameters {:model-name :claude-sonnet-4
                 :temperature 0.7
                 :max-output-tokens 4096}
    :provider :anthropic}
   ```
   
   Example - Using constructors:
   ```clojure
   (request-context
     {:messages [(user-message \"Hello\")]
      :parameters (parameters :claude-sonnet-4 
                              {:temperature 0.7 
                               :max-output-tokens 4096})
      :provider :anthropic})
   ```"
  (:require [langchain4clj.listeners.types :as types]
            [clojure.spec.alpha :as s]))

;; =============================================================================
;; Message Constructors
;; =============================================================================

(defn text-content
  "Create a text content item.
   
   Example:
   (text-content \"Hello, world!\")"
  [text]
  {:content-type :text
   :text text})

(defn image-content
  "Create an image content item.
   
   Example:
   (image-content \"base64-encoded-data\" \"image/png\")"
  [data mime-type]
  {:content-type :image
   :data data
   :mime-type mime-type})

(defn user-message
  "Create a user message with text content.
   
   Example:
   (user-message \"What is the weather today?\")"
  [text]
  {:message-type :user
   :contents [(text-content text)]})

(defn user-message-with-contents
  "Create a user message with multiple content items.
   
   Example:
   (user-message-with-contents 
     [(text-content \"Describe this image\")
      (image-content data \"image/png\")])"
  [contents]
  {:message-type :user
   :contents contents})

(defn system-message
  "Create a system message.
   
   Example:
   (system-message \"You are a helpful assistant.\")"
  [text]
  {:message-type :system
   :text text})

(defn ai-message
  "Create an AI message with text response.
   
   Example:
   (ai-message \"The weather is sunny today.\")"
  [text]
  {:message-type :ai
   :text text})

(defn ai-message-with-tool-calls
  "Create an AI message with tool execution requests.
   
   Example:
   (ai-message-with-tool-calls 
     \"Let me check the weather.\"
     [(tool-request \"call_123\" :get-weather {:city \"London\"})])"
  [text tool-requests]
  {:message-type :ai
   :text text
   :tool-execution-requests tool-requests})

(defn tool-request
  "Create a tool execution request.
   
   Example:
   (tool-request \"call_abc123\" :calculator {:expression \"2+2\"})"
  [id tool-name arguments]
  {:tool-id id
   :tool-name tool-name
   :arguments arguments})

(defn tool-result
  "Create a tool execution result message.
   
   Example:
   (tool-result \"call_abc123\" :calculator \"4\")"
  [id tool-name result-text]
  {:message-type :tool-execution-result
   :tool-id id
   :tool-name tool-name
   :text result-text})

;; =============================================================================
;; Token Usage Constructor
;; =============================================================================

(defn token-usage
  "Create a token usage map.
   
   Example:
   (token-usage 150 250)  ; input, output
   (token-usage 150 250 400)  ; explicit total"
  ([input-tokens output-tokens]
   (token-usage input-tokens output-tokens (+ input-tokens output-tokens)))
  ([input-tokens output-tokens total-tokens]
   {:input-tokens input-tokens
    :output-tokens output-tokens
    :total-tokens total-tokens}))

;; =============================================================================
;; Parameters Constructor
;; =============================================================================

(defn parameters
  "Create request parameters.
   
   Example:
   (parameters :gpt-4o {:temperature 0.7 :max-output-tokens 4096})
   (parameters :claude-sonnet-4 {:temperature 1.0})"
  [model-name opts]
  (merge {:model-name model-name} opts))

;; =============================================================================
;; Response Metadata Constructor
;; =============================================================================

(defn response-metadata
  "Create response metadata.
   
   Example:
   (response-metadata 
     {:model-name :claude-sonnet-4
      :finish-reason :end-turn
      :token-usage (token-usage 150 250)})"
  [{:keys [model-name finish-reason token-usage response-id]}]
  (cond-> {:model-name model-name
           :finish-reason finish-reason
           :token-usage token-usage}
    response-id (assoc :response-id response-id)))

;; =============================================================================
;; Context Constructors
;; =============================================================================

(defn request-context
  "Create a request context for on-request listeners.
   
   Required keys:
   - :messages - vector of messages
   - :parameters - request parameters (use `parameters` fn)
   - :provider - keyword enum (:openai, :anthropic, etc.)
   
   Optional keys:
   - :attributes - custom attributes map
   - :raw-context - original Java ChatModelRequestContext
   
   Example:
   (request-context
     {:messages [(system-message \"You are helpful\")
                 (user-message \"Hello\")]
      :parameters (parameters :claude-sonnet-4 {:temperature 0.7})
      :provider :anthropic})"
  [{:keys [messages parameters provider attributes raw-context]}]
  (cond-> {:messages messages
           :parameters parameters
           :provider provider}
    attributes (assoc :attributes attributes)
    raw-context (assoc :raw-context raw-context)))

(defn response-context
  "Create a response context for on-response listeners.
   
   Required keys:
   - :ai-message - the AI's response message
   - :response-metadata - metadata with tokens, finish reason (use `response-metadata` fn)
   - :provider - keyword enum
   
   Optional keys:
   - :request-context - the original request context
   - :attributes - custom attributes map
   - :raw-context - original Java ChatModelResponseContext
   
   Example:
   (response-context
     {:ai-message (ai-message \"Hello! How can I help?\")
      :response-metadata (response-metadata 
                           {:model-name :claude-sonnet-4
                            :finish-reason :end-turn
                            :token-usage (token-usage 10 25)})
      :provider :anthropic})"
  [{:keys [ai-message response-metadata provider request-context attributes raw-context]}]
  (cond-> {:ai-message ai-message
           :response-metadata response-metadata
           :provider provider}
    request-context (assoc :request-context request-context)
    attributes (assoc :attributes attributes)
    raw-context (assoc :raw-context raw-context)))

(defn error-context
  "Create an error context for on-error listeners.
   
   Required keys:
   - :error - error details map with :error-message and :error-type
   - :provider - keyword enum
   
   Optional keys:
   - :request-context - the original request context
   - :attributes - custom attributes map
   
   Example:
   (error-context
     {:error {:error-message \"Rate limit exceeded\"
              :error-type \"RateLimitException\"
              :error-cause \"Too many requests\"}
      :provider :openai
      :request-context original-request})"
  [{:keys [error provider request-context attributes]}]
  (cond-> {:error error
           :provider provider}
    request-context (assoc :request-context request-context)
    attributes (assoc :attributes attributes)))

(defn error-info
  "Create an error details map.
   
   Example:
   (error-info \"Connection timeout\" \"TimeoutException\")"
  ([message type]
   {:error-message message
    :error-type type})
  ([message type cause]
   {:error-message message
    :error-type type
    :error-cause cause}))

;; =============================================================================
;; Validation
;; =============================================================================

(defn validate!
  "Validate a context against its spec. Throws ex-info on failure.

   Example:
   (validate! :langchain4clj.listeners.specs/request-context my-request)"
  [spec ctx]
  (if (s/valid? spec ctx)
    ctx
    (throw (ex-info "Invalid context"
                    {:spec spec
                     :problems (s/explain-data spec ctx)}))))

(defn valid?
  "Check if a context is valid against its spec.

   Example:
   (valid? :langchain4clj.listeners.specs/request-context my-request)"
  [spec ctx]
  (s/valid? spec ctx))

;; =============================================================================
;; Conversion to Java Format
;; =============================================================================

(defn ->java
  "Convert a context from Clojure keywords to Java/API string format.
   
   Example:
   (->java (request-context {...}))
   ; Converts :provider :anthropic to :provider \"ANTHROPIC\""
  [ctx]
  (types/context->java ctx))

(defn <-java
  "Convert a context from Java/API string format to Clojure keywords.
   
   Example:
   (<-java {:provider \"ANTHROPIC\" :message-type \"USER\"})
   ; => {:provider :anthropic :message-type :user}"
  [ctx]
  (types/java->context ctx))
