(ns langchain4clj.core
  "Core wrapper functions for LangChain4j"
  (:require [langchain4clj.macros :as macros]
            [langchain4clj.specs :as specs]
            [langchain4clj.constants :as const]
            [clojure.spec.alpha :as s]
            [clojure.tools.logging :as log])
  (:import [dev.langchain4j.model.openai OpenAiChatModel OpenAiChatRequestParameters]
           [dev.langchain4j.model.chat ChatModel]
           [dev.langchain4j.model.anthropic AnthropicChatModel]
           [dev.langchain4j.model.googleai GoogleAiGeminiChatModel GeminiThinkingConfig]
           [dev.langchain4j.model.mistralai MistralAiChatModel]
           [dev.langchain4j.model.vertexai.gemini VertexAiGeminiChatModel]
           [dev.langchain4j.model.ollama OllamaChatModel]
           [dev.langchain4j.model.chat.request ChatRequest ResponseFormat]
           [dev.langchain4j.data.message UserMessage SystemMessage]
           [java.time Duration]))

(defn- duration-from-millis [millis]
  (Duration/ofMillis millis))

(defn- int-from-long [n]
  (when n (int n)))

(defn- build-openai-request-params
  "Build OpenAI request parameters for reasoning models.
   Returns nil if no reasoning config provided."
  [{:keys [effort]}]
  (when effort
    (-> (OpenAiChatRequestParameters/builder)
        (.reasoningEffort (name effort))
        (.build))))

(defn- build-anthropic-thinking-config
  "Extract Anthropic thinking config from unified :thinking map."
  [{:keys [enabled budget-tokens return send]}]
  (cond-> {}
    enabled (assoc :thinking-type "enabled")
    budget-tokens (assoc :thinking-budget-tokens budget-tokens)
    (some? return) (assoc :return-thinking return)
    (some? send) (assoc :send-thinking send)))

(defn- build-gemini-thinking-config
  "Build GeminiThinkingConfig from unified :thinking map.
   Returns nil if thinking is not enabled."
  [{:keys [enabled effort budget-tokens]}]
  (when enabled
    (let [budget (or budget-tokens
                     (get {:low 1024 :medium 4096 :high 8192} effort 4096))]
      (-> (GeminiThinkingConfig/builder)
          (.includeThoughts true)
          (.thinkingBudget (int budget))
          (.build)))))

(macros/defbuilder build-openai-model
  (OpenAiChatModel/builder)
  {:api-key :apiKey
   :model :modelName
   :temperature :temperature
   :timeout [:timeout duration-from-millis]
   :log-requests? :logRequests
   :log-responses? :logResponses
   :max-retries :maxRetries
   :max-tokens :maxTokens
   :listeners :listeners
   ;; Thinking/reasoning support
   :return-thinking :returnThinking
   :default-request-parameters :defaultRequestParameters})

(macros/defbuilder build-anthropic-model
  (AnthropicChatModel/builder)
  {:api-key :apiKey
   :model :modelName
   :temperature :temperature
   :timeout [:timeout duration-from-millis]
   :log-requests? :logRequests
   :log-responses? :logResponses
   :max-retries :maxRetries
   :max-tokens :maxTokens
   :listeners :listeners
   ;; Thinking/Extended reasoning support
   :thinking-type :thinkingType
   :thinking-budget-tokens [:thinkingBudgetTokens int-from-long]
   :return-thinking :returnThinking
   :send-thinking :sendThinking})

(macros/defbuilder build-google-ai-gemini-model
  (GoogleAiGeminiChatModel/builder)
  {:api-key :apiKey
   :model :modelName
   :temperature :temperature
   :timeout [:timeout duration-from-millis]
   :log-requests? :logRequestsAndResponses
   :log-responses? :logRequestsAndResponses
   :max-retries :maxRetries
   :max-tokens :maxOutputTokens
   :listeners :listeners
   ;; Thinking/reasoning support
   :thinking-config :thinkingConfig
   :return-thinking :returnThinking
   :send-thinking :sendThinking})

(macros/defbuilder build-vertex-ai-gemini-model
  (VertexAiGeminiChatModel/builder)
  {:project :project
   :location :location
   :model :modelName
   :temperature :temperature
   :timeout [:timeout duration-from-millis]
   :max-retries :maxRetries
   :max-tokens :maxOutputTokens
   :listeners :listeners})

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
   :max-retries [:maxRetries int-from-long]
   :listeners :listeners})

(macros/defbuilder build-mistral-model
  (MistralAiChatModel/builder)
  {:api-key :apiKey
   :model :modelName
   :temperature :temperature
   :timeout [:timeout duration-from-millis]
   :log-requests? :logRequests
   :log-responses? :logResponses
   :max-retries :maxRetries
   :max-tokens :maxTokens
   :listeners :listeners})

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
  [{:keys [model temperature timeout log-requests? log-responses? thinking]
    :or {model "gpt-4o-mini"
         temperature const/default-temperature
         timeout const/default-timeout-ms
         log-requests? false
         log-responses? false}
    :as config}]
  (let [request-params (build-openai-request-params thinking)
        base-config (merge {:model model
                            :temperature temperature
                            :timeout timeout
                            :log-requests? log-requests?
                            :log-responses? log-responses?}
                           (select-keys config [:api-key :max-retries :max-tokens :listeners])
                           (when request-params
                             {:default-request-parameters request-params})
                           (when (:return thinking)
                             {:return-thinking true}))]
    (build-openai-model base-config)))

(defmethod build-model :anthropic
  [{:keys [model temperature timeout log-requests? log-responses? thinking]
    :or {model "claude-3-5-sonnet-20241022"
         temperature const/default-temperature
         timeout const/default-timeout-ms
         log-requests? false
         log-responses? false}
    :as config}]
  (let [thinking-config (when thinking (build-anthropic-thinking-config thinking))
        base-config (merge {:model model
                            :temperature temperature
                            :timeout timeout
                            :log-requests? log-requests?
                            :log-responses? log-responses?}
                           (select-keys config [:api-key :max-retries :max-tokens :listeners])
                           thinking-config)]
    (build-anthropic-model base-config)))

(defmethod build-model :google-ai-gemini
  [{:keys [model temperature timeout log-requests? log-responses? thinking]
    :or {model "gemini-1.5-flash"
         temperature const/default-temperature
         timeout const/default-timeout-ms
         log-requests? false
         log-responses? false}
    :as config}]
  (let [thinking-config (when thinking (build-gemini-thinking-config thinking))
        base-config (merge {:model model
                            :temperature temperature
                            :timeout timeout
                            :log-requests? log-requests?
                            :log-responses? log-responses?}
                           (select-keys config [:api-key :max-retries :max-tokens :listeners])
                           (when thinking-config
                             {:thinking-config thinking-config})
                           (when (:return thinking)
                             {:return-thinking true})
                           (when (:send thinking)
                             {:send-thinking true}))]
    (build-google-ai-gemini-model base-config)))

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
                                       (select-keys config [:max-retries :max-tokens :listeners]))))

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
                             (select-keys config [:top-k :top-p :seed :num-predict :stop :response-format :max-retries :listeners]))))

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
                              (select-keys config [:api-key :max-retries :max-tokens :listeners]))))

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

(defn with-listeners
  "Adds listeners for observability. Use in threading.
   Listeners receive events for request, response, and errors.
   
   Example:
   ```clojure
   (require '[langchain4clj.listeners :as listeners])
   
   (-> {:provider :openai :api-key \"...\"}
       (with-listeners [(listeners/logging-listener)
                        (listeners/token-tracking-listener stats-atom)])
       create-model)
   ```"
  [config listeners]
  (assoc config :listeners listeners))

(defn with-thinking
  "Enables extended thinking/reasoning mode. Use in threading.
   
   Supported providers:
   - :openai (o1, o3, o3-mini models) - uses :effort (:low :medium :high)
   - :anthropic (claude-3.5+) - uses :enabled, :budget-tokens, :return, :send
   - :google-ai-gemini (gemini-2.5+) - uses :enabled, :budget-tokens or :effort, :return, :send
   
   Options:
   - :enabled       - Enable thinking (Anthropic, Gemini)
   - :effort        - Reasoning effort :low :medium :high (OpenAI, Gemini)
   - :budget-tokens - Max tokens for thinking (Anthropic, Gemini)
   - :return        - Include thinking in response (all providers)
   - :send          - Send thinking in multi-turn (Anthropic, Gemini)
   
   Example:
   ```clojure
   ;; OpenAI o3-mini with high reasoning
   (-> {:provider :openai :api-key \"...\" :model \"o3-mini\"}
       (with-thinking {:effort :high :return true})
       create-model)
   
   ;; Anthropic Claude with extended thinking
   (-> {:provider :anthropic :api-key \"...\" :model \"claude-sonnet-4-20250514\"}
       (with-thinking {:enabled true :budget-tokens 4096 :return true})
       create-model)
   
   ;; Google Gemini with reasoning
   (-> {:provider :google-ai-gemini :api-key \"...\" :model \"gemini-2.5-flash\"}
       (with-thinking {:enabled true :effort :medium :return true})
       create-model)
   ```"
  [config thinking-opts]
  (assoc config :thinking thinking-opts))

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
