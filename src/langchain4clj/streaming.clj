(ns langchain4clj.streaming
  "Streaming response support for chat models."
  (:require [langchain4clj.macros :as macros]
            [langchain4clj.specs :as specs]
            [langchain4clj.constants :as const]
            [clojure.spec.alpha :as s])
  (:import [dev.langchain4j.model.chat StreamingChatModel]
           [dev.langchain4j.model.chat.response StreamingChatResponseHandler]
           [dev.langchain4j.model.openai OpenAiStreamingChatModel]
           [dev.langchain4j.model.anthropic AnthropicStreamingChatModel]
           [dev.langchain4j.model.ollama OllamaStreamingChatModel]
           [java.time Duration]))

(defn- duration-from-millis [millis]
  (Duration/ofMillis millis))

(defn- int-from-long [n]
  (when n (int n)))

(defn- create-handler
  "Creates StreamingChatResponseHandler from callbacks map.
   Required: :on-token. Optional: :on-complete, :on-error."
  [{:keys [on-token on-complete on-error]}]
  (reify StreamingChatResponseHandler
    (onPartialResponse [_ token]
      (on-token token))
    (onCompleteResponse [_ response]
      (when on-complete (on-complete response)))
    (onError [_ error]
      (when on-error (on-error error)))))

(macros/defbuilder build-openai-streaming-model
  (OpenAiStreamingChatModel/builder)
  {:api-key :apiKey
   :model :modelName
   :temperature :temperature
   :timeout [:timeout duration-from-millis]
   :max-tokens [:maxTokens int-from-long]
   :top-p :topP
   :log-requests :logRequests
   :log-responses :logResponses})

(macros/defbuilder build-anthropic-streaming-model
  (AnthropicStreamingChatModel/builder)
  {:api-key :apiKey
   :model :modelName
   :temperature :temperature
   :timeout [:timeout duration-from-millis]
   :max-tokens [:maxTokens int-from-long]
   :top-p :topP
   :top-k :topK})

(macros/defbuilder build-ollama-streaming-model
  (OllamaStreamingChatModel/builder)
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

(defmulti create-streaming-model
  "Creates a streaming chat model. See docs/STREAMING.md for details."
  :provider)

(defmethod create-streaming-model :openai
  [{:keys [api-key model temperature timeout max-tokens top-p log-requests log-responses]
    :or {model "gpt-4o-mini"
         temperature const/default-temperature}}]
  (build-openai-streaming-model
   (cond-> {:api-key api-key
            :model model
            :temperature temperature}
     timeout (assoc :timeout timeout)
     max-tokens (assoc :max-tokens max-tokens)
     top-p (assoc :top-p top-p)
     log-requests (assoc :log-requests log-requests)
     log-responses (assoc :log-responses log-responses))))

(defmethod create-streaming-model :anthropic
  [{:keys [api-key model temperature timeout max-tokens top-p top-k]
    :or {model "claude-3-5-sonnet-20241022"
         temperature const/default-temperature}}]
  (build-anthropic-streaming-model
   (cond-> {:api-key api-key
            :model model
            :temperature temperature}
     timeout (assoc :timeout timeout)
     max-tokens (assoc :max-tokens max-tokens)
     top-p (assoc :top-p top-p)
     top-k (assoc :top-k top-k))))

(defmethod create-streaming-model :ollama
  [{:keys [base-url model temperature timeout top-k top-p seed num-predict stop log-requests log-responses max-retries]
    :or {base-url "http://localhost:11434"
         model "llama3.1"
         temperature const/default-temperature}}]
  (build-ollama-streaming-model
   (cond-> {:base-url base-url
            :model model
            :temperature temperature}
     timeout (assoc :timeout timeout)
     top-k (assoc :top-k top-k)
     top-p (assoc :top-p top-p)
     seed (assoc :seed seed)
     num-predict (assoc :num-predict num-predict)
     stop (assoc :stop stop)
     log-requests (assoc :log-requests log-requests)
     log-responses (assoc :log-responses log-responses)
     max-retries (assoc :max-retries max-retries))))

(defn stream-chat
  "Streams chat with callbacks. Requires :on-token fn in opts."
  [^StreamingChatModel model message {:keys [on-token on-complete on-error]}]
  {:pre [(some? model) (string? message) (fn? on-token)]}
  (let [handler (create-handler {:on-token on-token
                                 :on-complete on-complete
                                 :on-error on-error})]
    (.chat model message handler)))

(comment
  (require '[langchain4clj.streaming :as streaming])

  (def model (streaming/create-streaming-model
              {:provider :openai
               :api-key (System/getenv "OPENAI_API_KEY")
               :model "gpt-4o-mini"}))

  (streaming/stream-chat model "Say hello in 3 words"
                         {:on-token (fn [token] (print token) (flush))
                          :on-complete (fn [_] (println "\nDone!"))
                          :on-error (fn [err] (println "\nError:" (.getMessage err)))}))

(s/fdef create-streaming-model
  :args (s/cat :config ::specs/model-config)
  :ret ::specs/streaming-chat-model)

(s/fdef stream-chat
  :args (s/cat :model ::specs/streaming-chat-model
               :message string?
               :callbacks ::specs/streaming-callbacks)
  :ret nil?)
