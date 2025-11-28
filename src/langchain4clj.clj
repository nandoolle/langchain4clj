(ns langchain4clj
  (:import [dev.langchain4j.model.chat ChatModel]
           [dev.langchain4j.model.openai OpenAiChatModel]
           [dev.langchain4j.model.anthropic AnthropicChatModel]
           [dev.langchain4j.model.mistralai MistralAiChatModel]))

(defn- build-openai-model
  "Creates an OpenAI chat model instance"
  [{:keys [api-key model temperature timeout log-requests log-responses]
    :or {model "gpt-4o-mini"
         temperature 0.7
         timeout 60000
         log-requests false
         log-responses false}}]
  (cond-> (OpenAiChatModel/builder)
    api-key (.apiKey api-key)
    model (.modelName model)
    temperature (.temperature temperature)
    timeout (.timeout (java.time.Duration/ofMillis timeout))
    log-requests (.logRequests log-requests)
    log-responses (.logResponses log-responses)
    true (.build)))

(defn- build-mistral-model
  "Creates a Mistral chat model instance"
  [{:keys [api-key model temperature timeout log-requests log-responses]
    :or {model "mistral-small-latest"
         temperature 0.7
         timeout 60000
         log-requests false
         log-responses false}}]
  (cond-> (MistralAiChatModel/builder)
    api-key (.apiKey api-key)
    model (.modelName model)
    temperature (.temperature temperature)
    timeout (.timeout (java.time.Duration/ofMillis timeout))
    log-requests (.logRequests log-requests)
    log-responses (.logResponses log-responses)
    true (.build)))

(defn- build-anthropic-model
  "Creates an Anthropic chat model instance"
  [{:keys [api-key model temperature timeout log-requests log-responses]
    :or {model "claude-3-5-sonnet-20241022"
         temperature 0.7
         timeout 60000
         log-requests false
         log-responses false}}]
  (cond-> (AnthropicChatModel/builder)
    api-key (.apiKey api-key)
    model (.modelName model)
    temperature (.temperature temperature)
    timeout (.timeout (java.time.Duration/ofMillis timeout))
    log-requests (.logRequests log-requests)
    log-responses (.logResponses log-responses)
    true (.build)))

(defn create-model
  "Creates a chat model based on data-driven configuration.

  Accepted configuration:
  - :provider  - :openai :anthropic or :mistral (required)
  - :api-key   - Provider API key (required)
  - :model     - Model name (optional)
  - :temperature - Model temperature 0.0-1.0 (default: 0.7)
  - :timeout   - Timeout in ms (default: 60000)
  - :log-requests - Log requests (default: false)
  - :log-responses - Log responses (default: false)

  Example:
  (create-model {:provider :openai
                 :api-key \"sk-...\"
                 :model \"gpt-4o-mini\"
                 :temperature 0.7})"
  [{:keys [provider] :as config}]
  (case provider
    :openai (build-openai-model config)
    :anthropic (build-anthropic-model config)
    :mistral (build-mistral-model config)
    (throw (ex-info "Unsupported provider"
                    {:provider provider
                     :supported-providers [:openai :anthropic :mistral]}))))

(defn chat
  "Sends a message to the model and returns the response.

  Parameters:
  - model: ChatModel instance created with create-model
  - message: string with the message

  Returns: string with the model's response

  Example:
  (def model (create-model {:provider :openai :api-key \"sk-...\"}))
  (chat model \"Hello, how are you?\")"
  [^ChatModel model ^String message]
  (.chat model message))