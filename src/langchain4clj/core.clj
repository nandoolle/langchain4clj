(ns langchain4clj.core
  "Core wrapper functions for LangChain4j"
  (:require [langchain4clj.macros :as macros]
            [langchain4clj.specs :as specs]
            [langchain4clj.constants :as const]
            [clojure.spec.alpha :as s]
            [clojure.tools.logging :as log])
  (:import [dev.langchain4j.model.openai OpenAiChatModel]
           [dev.langchain4j.model.chat ChatModel]
           [dev.langchain4j.model.anthropic AnthropicChatModel]
           [dev.langchain4j.model.googleai GoogleAiGeminiChatModel]
           [dev.langchain4j.model.mistralai MistralAiChatModel]
           [dev.langchain4j.model.vertexai VertexAiGeminiChatModel]
           [dev.langchain4j.model.ollama OllamaChatModel]
           [dev.langchain4j.model.chat.request ChatRequest ResponseFormat]
           [dev.langchain4j.data.message UserMessage SystemMessage]
           [java.time Duration]))

(defn- duration-from-millis
  "Convert milliseconds to Java Duration"
  [millis]
  (Duration/ofMillis millis))

(defn- int-from-long
  "Converts Long to Integer for Java interop"
  [n]
  (when n (int n)))

;; ============================================================================
;; Idiomatic Model Builders (New in v0.2.0)
;; ============================================================================

(macros/defbuilder build-openai-model
  (OpenAiChatModel/builder)
  {:api-key :apiKey
   :model :modelName
   :temperature :temperature
   :timeout [:timeout duration-from-millis]
   :log-requests? :logRequests
   :log-responses? :logResponses
   :max-retries :maxRetries
   :max-tokens :maxTokens})

(macros/defbuilder build-anthropic-model
  (AnthropicChatModel/builder)
  {:api-key :apiKey
   :model :modelName
   :temperature :temperature
   :timeout [:timeout duration-from-millis]
   :log-requests? :logRequests
   :log-responses? :logResponses
   :max-retries :maxRetries
   :max-tokens :maxTokens})

(macros/defbuilder build-google-ai-gemini-model
  (GoogleAiGeminiChatModel/builder)
  {:api-key :apiKey
   :model :modelName
   :temperature :temperature
   :timeout [:timeout duration-from-millis]
   :log-requests? :logRequestsAndResponses
   :log-responses? :logRequestsAndResponses
   :max-retries :maxRetries
   :max-tokens :maxOutputTokens})

(macros/defbuilder build-vertex-ai-gemini-model
  (VertexAiGeminiChatModel/builder)
  {:project :project
   :location :location
   :model :modelName
   :temperature :temperature
   :timeout [:timeout duration-from-millis]
   :max-retries :maxRetries
   :max-tokens :maxOutputTokens})

(macros/defbuilder build-ollama-model
  (OllamaChatModel/builder)
  {:base-url :baseUrl
   :model :modelName
   :temperature :temperature
   :timeout [:timeout duration-from-millis]
   :top-k [:topK int-from-long]
   :top-p :topP
   :seed [:seed int-from-long]
   :num-predict [:numPredict int-from-long]
   :stop :stop
   :response-format :responseFormat
   :log-requests :logRequests
   :log-responses :logResponses
   :max-retries [:maxRetries int-from-long]})

(macros/defbuilder build-mistral-model
  (MistralAiChatModel/builder)
  {:api-key :apiKey
   :model :modelName
   :temperature :temperature
   :timeout [:timeout duration-from-millis]
   :log-requests? :logRequests
   :log-responses? :logResponses
   :max-retries :maxRetries
   :max-tokens :maxTokens})

(macros/defbuilder build-chat-request-idiomatic
  (ChatRequest/builder)
  {:messages :messages
   :model-name :modelName
   :temperature :temperature
   :max-tokens :maxOutputTokens
   :top-p :topP
   :top-k :topK
   :frequency-penalty :frequencyPenalty
   :presence-penalty :presencePenalty
   :stop-sequences :stopSequences
   :tool-specifications :toolSpecifications
   :response-format :responseFormat})

(defmulti build-model
  "Build a chat model based on provider configuration"
  (fn [config] (:provider config)))

(defmethod build-model :openai
  [{:keys [model temperature timeout log-requests? log-responses?]
    :or {model "gpt-4o-mini"
         temperature const/default-temperature
         timeout const/default-timeout-ms
         log-requests? false
         log-responses? false}
    :as config}]
  (build-openai-model (merge {:model model
                              :temperature temperature
                              :timeout timeout
                              :log-requests? log-requests?
                              :log-responses? log-responses?}
                             (select-keys config [:api-key :max-retries :max-tokens]))))

(defmethod build-model :anthropic
  [{:keys [model temperature timeout log-requests? log-responses?]
    :or {model "claude-3-5-sonnet-20241022"
         temperature const/default-temperature
         timeout const/default-timeout-ms
         log-requests? false
         log-responses? false}
    :as config}]
  (build-anthropic-model (merge {:model model
                                 :temperature temperature
                                 :timeout timeout
                                 :log-requests? log-requests?
                                 :log-responses? log-responses?}
                                (select-keys config [:api-key :max-retries :max-tokens]))))

(defmethod build-model :google-ai-gemini
  [{:keys [model temperature timeout log-requests? log-responses?]
    :or {model "gemini-1.5-flash"
         temperature const/default-temperature
         timeout const/default-timeout-ms
         log-requests? false
         log-responses? false}
    :as config}]
  (build-google-ai-gemini-model (merge {:model model
                                        :temperature temperature
                                        :timeout timeout
                                        :log-requests? log-requests?
                                        :log-responses? log-responses?}
                                       (select-keys config [:api-key :max-retries :max-tokens]))))

(defmethod build-model :vertex-ai-gemini
  [{:keys [project location model temperature timeout]
    :or {model "gemini-1.5-flash"
         location "us-central1"
         temperature const/default-temperature
         timeout const/default-timeout-ms}
    :as config}]
  (build-vertex-ai-gemini-model (merge {:project project
                                        :location location
                                        :model model
                                        :temperature temperature
                                        :timeout timeout}
                                       (select-keys config [:max-retries :max-tokens]))))

(defmethod build-model :ollama
  [{:keys [base-url model temperature timeout log-requests? log-responses?]
    :or {base-url "http://localhost:11434"
         model "llama3.1"
         temperature const/default-temperature
         timeout const/default-timeout-ms
         log-requests? false
         log-responses? false}
    :as config}]
  (build-ollama-model (merge {:base-url base-url
                              :model model
                              :temperature temperature
                              :timeout timeout
                              :log-requests log-requests?
                              :log-responses log-responses?}
                             (select-keys config [:top-k :top-p :seed :num-predict :stop :response-format :max-retries]))))

(defmethod build-model :mistral
  [{:keys [model temperature timeout log-requests? log-responses?]
    :or {model "mistral-medium-2508"
         temperature const/default-temperature
         timeout const/default-timeout-ms
         log-requests? false
         log-responses? false}
    :as config}]
  (build-mistral-model (merge {:model model
                               :temperature temperature
                               :timeout timeout
                               :log-requests? log-requests?
                               :log-responses? log-responses?}
                              (select-keys config [:api-key :max-retries :max-tokens]))))

(defn create-model
  "Create a chat model from a configuration map.

  Config map structure:
  {:provider :openai | :anthropic
   :api-key \"your-api-key\"
   :model \"model-name\" (optional)
   :temperature 0.7 (optional, default 0.7)
   :timeout 60000 (optional, in milliseconds, default 60000)
   :log-requests? false (optional)
   :log-responses? false (optional)}

  Example:
  (create-model {:provider :openai
                 :api-key \"sk-...\"
                 :model \"gpt-4o-mini\"})"
  [config]
  (build-model config))

;; ============================================================================
;; Threading-First Model API (New in v0.2.0)
;; ============================================================================

(defn openai-model
  "Creates an OpenAI chat model from a configuration map.
  Supports threading-first pattern.

  Config keys:
  - :api-key (required) - Your OpenAI API key
  - :model (optional) - Model name, defaults to \"gpt-4o-mini\"
  - :temperature (optional) - Temperature 0.0-2.0, defaults to 0.7
  - :timeout (optional) - Timeout in milliseconds, defaults to 60000
  - :log-requests? (optional) - Log requests, defaults to false
  - :log-responses? (optional) - Log responses, defaults to false
  - :max-retries (optional) - Maximum retry attempts
  - :max-tokens (optional) - Maximum tokens to generate

  Examples:

  ;; Simple usage
  (openai-model {:api-key \"sk-...\"})

  ;; With configuration
  (openai-model {:api-key \"sk-...\"
                 :model \"gpt-4\"
                 :temperature 0.8})

  ;; Threading-first pattern
  (-> {:api-key \"sk-...\"}
      (assoc :model \"gpt-4\")
      (assoc :temperature 0.8)
      openai-model)"
  [config]
  (build-openai-model
   (macros/with-defaults config
     {:model "gpt-4o-mini"
      :temperature 0.7
      :timeout 60000
      :log-requests? false
      :log-responses? false})))

(defn anthropic-model
  "Creates an Anthropic chat model from a configuration map.
  Supports threading-first pattern.

  Config keys:
  - :api-key (required) - Your Anthropic API key
  - :model (optional) - Model name, defaults to \"claude-3-5-sonnet-20241022\"
  - :temperature (optional) - Temperature 0.0-1.0, defaults to 0.7
  - :timeout (optional) - Timeout in milliseconds, defaults to 60000
  - :log-requests? (optional) - Log requests, defaults to false
  - :log-responses? (optional) - Log responses, defaults to false
  - :max-retries (optional) - Maximum retry attempts
  - :max-tokens (optional) - Maximum tokens to generate

  Examples:

  ;; Simple usage
  (anthropic-model {:api-key \"sk-ant-...\"})

  ;; With configuration
  (anthropic-model {:api-key \"sk-ant-...\"
                    :model \"claude-3-opus-20240229\"
                    :temperature 0.9})

  ;; Threading-first pattern
  (-> {:api-key \"sk-ant-...\"}
      (assoc :model \"claude-3-opus-20240229\")
      (assoc :temperature 0.9)
      anthropic-model)"
  [config]
  (build-anthropic-model
   (macros/with-defaults config
     {:model "claude-3-5-sonnet-20241022"
      :temperature 0.7
      :timeout 60000
      :log-requests? false
      :log-responses? false})))

(defn google-ai-gemini-model
  "Creates a Google AI Gemini chat model from a configuration map.
  Supports threading-first pattern.

  This uses the direct Google AI API (not Vertex AI).

  Config keys:
  - :api-key (required) - Your Google AI API key
  - :model (optional) - Model name, defaults to gemini-1.5-flash
  - :temperature (optional) - Temperature 0.0-2.0, defaults to 0.7
  - :timeout (optional) - Timeout in milliseconds, defaults to 60000
  - :log-requests? (optional) - Log requests, defaults to false
  - :log-responses? (optional) - Log responses, defaults to false
  - :max-retries (optional) - Maximum retry attempts
  - :max-tokens (optional) - Maximum tokens to generate

  Available models:
  - gemini-1.5-pro - Most capable model
  - gemini-1.5-flash - Fast and efficient (default)
  - gemini-1.0-pro - Legacy model

  Examples:

  ;; Simple usage
  (google-ai-gemini-model {:api-key \"AIza...\"})

  ;; With configuration
  (google-ai-gemini-model {:api-key \"AIza...\"
                           :model \"gemini-1.5-pro\"
                           :temperature 0.8})

  ;; Threading-first pattern
  (-> {:api-key \"AIza...\"}
      (with-model \"gemini-1.5-pro\")
      (with-temperature 0.8)
      google-ai-gemini-model)"
  [config]
  (build-google-ai-gemini-model
   (macros/with-defaults config
     {:model "gemini-1.5-flash"
      :temperature 0.7
      :timeout 60000
      :log-requests? false
      :log-responses? false})))

(defn vertex-ai-gemini-model
  "Creates a Vertex AI Gemini chat model from a configuration map.
  Supports threading-first pattern.

  This uses Google Cloud's Vertex AI (requires GCP setup).

  Config keys:
  - :project (required) - Your GCP project ID
  - :location (optional) - GCP region, defaults to us-central1
  - :model (optional) - Model name, defaults to gemini-1.5-flash
  - :temperature (optional) - Temperature 0.0-2.0, defaults to 0.7
  - :timeout (optional) - Timeout in milliseconds, defaults to 60000
  - :max-retries (optional) - Maximum retry attempts
  - :max-tokens (optional) - Maximum tokens to generate

  Available models:
  - gemini-1.5-pro - Most capable model
  - gemini-1.5-flash - Fast and efficient (default)
  - gemini-1.0-pro - Legacy model

  Available locations:
  - us-central1 (default)
  - us-east1
  - us-west1
  - europe-west1
  - asia-southeast1

  Examples:

  ;; Simple usage
  (vertex-ai-gemini-model {:project \"my-gcp-project\"})

  ;; With configuration
  (vertex-ai-gemini-model {:project \"my-gcp-project\"
                           :location \"us-east1\"
                           :model \"gemini-1.5-pro\"
                           :temperature 0.9})

  ;; Threading-first pattern
  (-> {:project \"my-gcp-project\"}
      (assoc :location \"europe-west1\")
      (with-model \"gemini-1.5-pro\")
      (with-temperature 0.9)
      vertex-ai-gemini-model)"
  [config]
  (build-vertex-ai-gemini-model
   (macros/with-defaults config
     {:model "gemini-1.5-flash"
      :location "us-central1"
      :temperature 0.7
      :timeout 60000})))

(defn ollama-model
  "Creates an Ollama chat model from a configuration map.
  Supports threading-first pattern.

  Ollama allows running LLMs locally without API keys or costs.
  Popular models: llama3.1, mistral, gemma, codellama, etc.

  Config keys:
  - :base-url (optional) - Ollama server URL, defaults to http://localhost:11434
  - :model (optional) - Model name, defaults to llama3.1
  - :temperature (optional) - Temperature 0.0-2.0, defaults to 0.7
  - :timeout (optional) - Timeout in milliseconds, defaults to 60000
  - :top-k (optional) - Top-k sampling
  - :top-p (optional) - Top-p (nucleus) sampling
  - :seed (optional) - Random seed for reproducibility
  - :num-predict (optional) - Maximum tokens to generate
  - :stop (optional) - Stop sequences
  - :log-requests? (optional) - Log requests, defaults to false
  - :log-responses? (optional) - Log responses, defaults to false
  - :max-retries (optional) - Maximum retry attempts

  Popular models available via Ollama:
  - llama3.1 - Meta's Llama 3.1 (default)
  - llama3.1:70b - Larger Llama 3.1 variant
  - mistral - Mistral AI's model
  - gemma - Google's Gemma
  - codellama - Code-specialized Llama
  - phi - Microsoft's Phi models

  Prerequisites:
  1. Install Ollama: https://ollama.ai
  2. Pull a model: `ollama pull llama3.1`
  3. Server runs on http://localhost:11434 by default

  Examples:

  ;; Simple usage (assumes Ollama running locally)
  (ollama-model {})

  ;; With specific model
  (ollama-model {:model \"mistral\"})

  ;; With custom configuration
  (ollama-model {:model \"llama3.1:70b\"
                 :temperature 0.9
                 :top-p 0.95})

  ;; Remote Ollama server
  (ollama-model {:base-url \"http://192.168.1.100:11434\"
                 :model \"codellama\"})

  ;; Threading-first pattern
  (-> {}
      (assoc :model \"gemma\")
      (with-temperature 0.8)
      ollama-model)"
  [config]
  (build-ollama-model
   (macros/with-defaults config
     {:base-url "http://localhost:11434"
      :model "llama3.1"
      :temperature 0.7
      :timeout 60000
      :log-requests false
      :log-responses false})))

(defn mistral-model
  "Creates a Mistral chat model from a configuration map.
  Supports threading-first pattern.

  Config keys:
  - :api-key (required) - Your OpenAI API key
  - :model (optional) - Model name, defaults to \"gpt-4o-mini\"
  - :temperature (optional) - Temperature 0.0-2.0, defaults to 0.7
  - :timeout (optional) - Timeout in milliseconds, defaults to 60000
  - :log-requests? (optional) - Log requests, defaults to false
  - :log-responses? (optional) - Log responses, defaults to false
  - :max-retries (optional) - Maximum retry attempts
  - :max-tokens (optional) - Maximum tokens to generate

  Examples:

  ;; Simple usage
  (mistral-model {:api-key \"sk-...\"})

  ;; With configuration
  (mistral-model {:api-key \"sk-...\"
                  :model \"gpt-4\"
                  :temperature 0.8})

  ;; Threading-first pattern
  (-> {:api-key \"sk-...\"}
      (assoc :model \"mistral-medium-2508\")
      (assoc :temperature 0.8)
      mistral-model)"
  [config]
  (build-mistral-model
   (macros/with-defaults config
     {:model "mistral-medium-2508"
      :temperature 0.7
      :timeout 60000
      :log-requests? false
      :log-responses? false})))

(defn with-model
  "Sets the model name. Use in threading.

  Example:
  (-> {:api-key \"sk-...\"}
      (with-model \"gpt-4\")
      openai-model)"
  [config model-name]
  (assoc config :model model-name))

(defn with-temperature
  "Sets the temperature. Use in threading.

  Example:
  (-> {:api-key \"sk-...\"}
      (with-temperature 0.9)
      openai-model)"
  [config temperature]
  (assoc config :temperature temperature))

(defn with-timeout
  "Sets the timeout in milliseconds. Use in threading.

  Example:
  (-> {:api-key \"sk-...\"}
      (with-timeout 30000)
      openai-model)"
  [config timeout-ms]
  (assoc config :timeout timeout-ms))

(defn with-logging
  "Enables request and response logging. Use in threading.

  Example:
  (-> {:api-key \"sk-...\"}
      (with-logging)
      openai-model)"
  ([config]
   (with-logging config true))
  ([config enabled?]
   (assoc config
          :log-requests? enabled?
          :log-responses? enabled?)))

(defn with-response-format
  "Sets the response format for the chat request.

  ⚠️  PROVIDER SUPPORT:
  - ✅ OpenAI: Fully supported with guaranteed JSON output
  - ✅ Google AI Gemini: Supported
  - ✅ Mistral AI: Supported
  - ✅ Ollama: Supported
  - ❌ Anthropic Claude: NOT supported (will be ignored)
  - ❌ Vertex AI: NOT supported (will be ignored)

  For unsupported providers, use prompt engineering instead.

  Use ResponseFormat/JSON to force JSON output (when supported).

  Example:
  (-> {:prompt \"Return user data\"}
      (with-response-format ResponseFormat/JSON)
      (chat model))

  Or in options map:
  (chat model \"Hello\"
    {:response-format ResponseFormat/JSON})"
  [config format]
  (assoc config :response-format format))

(defn with-json-mode
  "Convenience helper that sets response format to JSON.
  Equivalent to (with-response-format config ResponseFormat/JSON)

  ⚠️  NATIVE JSON MODE IS ONLY GUARANTEED WITH OPENAI!

  Provider Support:
  - ✅ OpenAI: Native JSON mode - 100% guaranteed valid JSON
  - ✅ Google AI Gemini: Supported
  - ✅ Mistral AI: Supported
  - ✅ Ollama: Supported
  - ❌ Anthropic Claude: NOT supported - will have NO EFFECT
  - ❌ Vertex AI: NOT supported - will have NO EFFECT

  For Anthropic and other unsupported providers, use prompt engineering:
  - Add explicit JSON instructions in system message
  - Use prefilling (start assistant response with '{')
  - Use tool calling for structured output

  Example (works with OpenAI):
  (chat openai-model \"Return user data\"
    (with-json-mode {}))

  Example (won't work with Anthropic):
  (chat anthropic-model \"Return user data\"
    (with-json-mode {}))  ;; ❌ No effect! Use prompts instead

  Or with threading:
  (-> {:temperature 0.7}
      with-json-mode
      (->> (chat openai-model \"Return user data\")))"
  [config]
  (assoc config :response-format ResponseFormat/JSON))

(defn- supports-json-mode?
  "Checks if a ChatModel instance supports native JSON mode.

   Returns true for:
   - OpenAI models
   - Google AI Gemini models
   - Ollama models

   Returns false for:
   - Anthropic Claude models
   - Vertex AI models"
  [^ChatModel model]
  (or (instance? OpenAiChatModel model)
      (instance? GoogleAiGeminiChatModel model)
      (instance? OllamaChatModel model)))

(defn- build-chat-request
  "Builds a ChatRequest from message(s) and options map.

  Supports:
  - Single string message or list of ChatMessage objects
  - Tools via :tools key (list of ToolSpecification)
  - Response format via :response-format key (ResponseFormat/JSON)
  - System message via :system-message key
  - Temperature, max-tokens, etc.

  Example:
  (build-chat-request \"Hello\"
    {:tools [calculator-spec]
     :response-format ResponseFormat/JSON
     :temperature 0.8})"
  [message {:keys [tools response-format system-message
                   temperature max-tokens top-p top-k
                   frequency-penalty presence-penalty
                   stop-sequences model-name]}]
  (let [;; Build messages list
        messages (cond
                   ;; If message is already a list of ChatMessages, use it
                   (and (sequential? message) (seq message))
                   (if system-message
                     (cons (if (string? system-message)
                             (SystemMessage. system-message)
                             system-message)
                           message)
                     message)

                   ;; If message is a string, create UserMessage
                   (string? message)
                   (if system-message
                     [(if (string? system-message)
                        (SystemMessage. system-message)
                        system-message)
                      (UserMessage. message)]
                     [(UserMessage. message)])

                   ;; Otherwise, no messages
                   :else [])

        builder (ChatRequest/builder)]
    (cond-> builder
      ;; Messages
      (seq messages) (.messages messages)

      ;; Model configuration
      model-name (.modelName model-name)
      temperature (.temperature temperature)
      max-tokens (.maxOutputTokens (int max-tokens))
      top-p (.topP top-p)
      top-k (.topK (int top-k))
      frequency-penalty (.frequencyPenalty frequency-penalty)
      presence-penalty (.presencePenalty presence-penalty)
      (seq stop-sequences) (.stopSequences stop-sequences)

      ;; Advanced features
      (seq tools) (.toolSpecifications tools)
      response-format (.responseFormat response-format)

      ;; Build the request
      true (.build))))

(defn chat
  "Send a message to the chat model and get a response.

  Two arities:

  1. Simple chat (convenience method):
     (chat model \"Hello\")
     Returns: String

  2. Chat with options (full ChatRequest support):
     (chat model \"Hello\" {:tools [calculator]
                           :response-format ResponseFormat/JSON
                           :temperature 0.8})
     Returns: ChatResponse (with metadata)

  Or with message history:
     (chat model [user-msg ai-msg user-msg] {:tools [...]})

  Options map keys:
  - :tools - Vector of ToolSpecification objects
  - :response-format - ResponseFormat (e.g. ResponseFormat/JSON)
  - :system-message - System message string or SystemMessage
  - :temperature - Temperature (0.0-1.0)
  - :max-tokens - Maximum output tokens
  - :top-p - Top P sampling
  - :top-k - Top K sampling
  - :frequency-penalty - Frequency penalty
  - :presence-penalty - Presence penalty
  - :stop-sequences - Vector of stop sequences
  - :model-name - Override model name

  Examples:

  ;; Simple usage
  (chat model \"What is 2+2?\")
  ;; => \"2 + 2 equals 4\"

  ;; With tools
  (chat model \"Calculate 2+2\" {:tools [calculator-spec]})
  ;; => ChatResponse with tool execution requests

  ;; With JSON mode
  (chat model \"Return user data as JSON\"
    {:response-format ResponseFormat/JSON})
  ;; => ChatResponse with JSON string

  ;; With conversation history
  (chat model [user-msg-1 ai-msg-1 user-msg-2]
    {:temperature 0.9})
  ;; => ChatResponse

  ;; Everything together
  (chat model \"Hello\"
    {:tools [tool1 tool2]
     :response-format ResponseFormat/JSON
     :temperature 0.7
     :max-tokens 1000
     :system-message \"You are a helpful assistant\"})"
  ([^ChatModel model message]
   ;; Simple arity - just call the convenience method
   (.chat model message))

  ([^ChatModel model message options]
   ;; Full arity - use ChatRequest with all options
   (if (empty? options)
     ;; No options? Use simple method for efficiency
     (.chat model message)
     ;; Has options? Build ChatRequest and call chat(ChatRequest)
     (do
       ;; Warn if using response-format with unsupported provider
       (when (and (:response-format options)
                  (not (supports-json-mode? model)))
         (log/warn (str "⚠️  JSON mode (response-format) is NOT supported by "
                        (.getClass model)
                        ". This parameter will have NO EFFECT. "
                        "For Anthropic Claude, use prompt engineering instead. "
                        "See with-json-mode docstring for details.")))

       (let [request (build-chat-request message options)]
         (.chat model request))))))
;; ============================================================================
;; Specs for Public API
;; ============================================================================

(s/fdef create-model
  :args (s/cat :config ::specs/model-config)
  :ret ::specs/chat-model)

(s/fdef chat
  :args (s/or :simple (s/cat :model ::specs/chat-model
                             :message ::specs/message)
              :with-opts (s/cat :model ::specs/chat-model
                                :message (s/or :string string?
                                               :messages ::specs/messages)
                                :options (s/? map?)))
  :ret (s/or :string string?
             :response #(instance? dev.langchain4j.model.chat.response.ChatResponse %)))

;; Helper function specs
(s/fdef with-model
  :args (s/cat :config map? :model-name string?)
  :ret map?)

(s/fdef with-temperature
  :args (s/cat :config map? :temperature ::specs/temperature)
  :ret map?)

(s/fdef with-timeout
  :args (s/cat :config map? :timeout-ms ::specs/timeout)
  :ret map?)

(s/fdef with-logging
  :args (s/cat :config map?
               :log-requests? (s/? boolean?)
               :log-responses? (s/? boolean?))
  :ret map?)

(s/fdef with-json-mode
  :args (s/cat :config map?)
  :ret map?)
