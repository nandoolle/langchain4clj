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

(defn- duration-from-millis [millis]
  (Duration/ofMillis millis))

(defn- int-from-long [n]
  (when n (int n)))

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
  "Build a chat model based on provider configuration."
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
  "Create a chat model from config map. See docs/CORE_CHAT.md for details."
  [config]
  (build-model config))

(defn openai-model
  "Creates an OpenAI chat model. Supports threading-first pattern."
  [config]
  (build-openai-model
   (macros/with-defaults config
     {:model "gpt-4o-mini"
      :temperature 0.7
      :timeout 60000
      :log-requests? false
      :log-responses? false})))

(defn anthropic-model
  "Creates an Anthropic chat model. Supports threading-first pattern."
  [config]
  (build-anthropic-model
   (macros/with-defaults config
     {:model "claude-3-5-sonnet-20241022"
      :temperature 0.7
      :timeout 60000
      :log-requests? false
      :log-responses? false})))

(defn google-ai-gemini-model
  "Creates a Google AI Gemini chat model. Supports threading-first pattern."
  [config]
  (build-google-ai-gemini-model
   (macros/with-defaults config
     {:model "gemini-1.5-flash"
      :temperature 0.7
      :timeout 60000
      :log-requests? false
      :log-responses? false})))

(defn vertex-ai-gemini-model
  "Creates a Vertex AI Gemini chat model. Supports threading-first pattern."
  [config]
  (build-vertex-ai-gemini-model
   (macros/with-defaults config
     {:model "gemini-1.5-flash"
      :location "us-central1"
      :temperature 0.7
      :timeout 60000})))

(defn ollama-model
  "Creates an Ollama chat model. Supports threading-first pattern."
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
  "Creates a Mistral chat model. Supports threading-first pattern."
  [config]
  (build-mistral-model
   (macros/with-defaults config
     {:model "mistral-medium-2508"
      :temperature 0.7
      :timeout 60000
      :log-requests? false
      :log-responses? false})))

(defn with-model
  "Sets the model name. Use in threading."
  [config model-name]
  (assoc config :model model-name))

(defn with-temperature
  "Sets the temperature. Use in threading."
  [config temperature]
  (assoc config :temperature temperature))

(defn with-timeout
  "Sets the timeout in milliseconds. Use in threading."
  [config timeout-ms]
  (assoc config :timeout timeout-ms))

(defn with-logging
  "Enables request and response logging. Use in threading."
  ([config] (with-logging config true))
  ([config enabled?]
   (assoc config :log-requests? enabled? :log-responses? enabled?)))

(defn with-response-format
  "Sets the response format. Note: not supported by all providers."
  [config format]
  (assoc config :response-format format))

(defn with-json-mode
  "Sets response format to JSON. Only guaranteed with OpenAI.
   For Anthropic, use prompt engineering instead."
  [config]
  (assoc config :response-format ResponseFormat/JSON))

(defn- supports-json-mode? [^ChatModel model]
  (or (instance? OpenAiChatModel model)
      (instance? GoogleAiGeminiChatModel model)
      (instance? OllamaChatModel model)))

(defn- build-chat-request
  [message {:keys [tools response-format system-message
                   temperature max-tokens top-p top-k
                   frequency-penalty presence-penalty
                   stop-sequences model-name]}]
  (let [messages (cond
                   (and (sequential? message) (seq message))
                   (if system-message
                     (cons (if (string? system-message)
                             (SystemMessage. system-message)
                             system-message)
                           message)
                     message)

                   (string? message)
                   (if system-message
                     [(if (string? system-message)
                        (SystemMessage. system-message)
                        system-message)
                      (UserMessage. message)]
                     [(UserMessage. message)])

                   :else [])

        builder (ChatRequest/builder)]
    (cond-> builder
      (seq messages) (.messages messages)
      model-name (.modelName model-name)
      temperature (.temperature temperature)
      max-tokens (.maxOutputTokens (int max-tokens))
      top-p (.topP top-p)
      top-k (.topK (int top-k))
      frequency-penalty (.frequencyPenalty frequency-penalty)
      presence-penalty (.presencePenalty presence-penalty)
      (seq stop-sequences) (.stopSequences stop-sequences)
      (seq tools) (.toolSpecifications tools)
      response-format (.responseFormat response-format)
      true (.build))))

(defn chat
  "Send a message to the chat model.
   Simple: (chat model \"Hello\") => String
   With opts: (chat model \"Hello\" {:temperature 0.8}) => ChatResponse"
  ([^ChatModel model message]
   (.chat model message))

  ([^ChatModel model message options]
   (if (empty? options)
     (.chat model message)
     (do
       (when (and (:response-format options) (not (supports-json-mode? model)))
         (log/warn (str "JSON mode not supported by " (.getClass model))))
       (let [request (build-chat-request message options)]
         (.chat model request))))))

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
