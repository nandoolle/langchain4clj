(ns langchain4clj
  (:import [dev.langchain4j.model.chat ChatModel]
           [dev.langchain4j.model.openai OpenAiChatModel]
           [dev.langchain4j.model.anthropic AnthropicChatModel]
           [dev.langchain4j.model.mistralai MistralAiChatModel]))

(defn- build-openai-model
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
  "Creates a chat model. Required: :provider (:openai, :anthropic, :mistral), :api-key."
  [{:keys [provider] :as config}]
  (case provider
    :openai (build-openai-model config)
    :anthropic (build-anthropic-model config)
    :mistral (build-mistral-model config)
    (throw (ex-info "Unsupported provider"
                    {:provider provider
                     :supported-providers [:openai :anthropic :mistral]}))))

(defn chat
  "Sends a message to the model and returns the response string."
  [^ChatModel model ^String message]
  (.chat model message))