(ns langchain4clj.listeners
  "Chat model listeners for observability, token tracking, and event handling.
   
   Provides Clojure-friendly wrappers around LangChain4j's ChatModelListener
   interface, converting Java objects to EDN for easier manipulation.
   
   ## Quick Start
   
   ```clojure
   (require '[langchain4clj.listeners :as listeners])
   
   ;; Create a custom listener
   (def my-listener
     (listeners/create-listener
       {:on-request  (fn [ctx] (println \"Request:\" (:messages ctx)))
        :on-response (fn [ctx] (println \"Tokens:\" (get-in ctx [:response-metadata :token-usage])))
        :on-error    (fn [ctx] (println \"Error:\" (get-in ctx [:error :error-message])))}))
   
   ;; Use pre-built listeners
   (def logger (listeners/logging-listener))
   (def stats (atom {}))
   (def tracker (listeners/token-tracking-listener stats))
   
   ;; Compose multiple listeners
   (def combined (listeners/compose-listeners logger tracker my-listener))
   ```"
  (:require [langchain4clj.listeners.context :as ctx]
            [langchain4clj.listeners.types :as types]
            [clojure.tools.logging :as log])
  (:import [dev.langchain4j.model.chat.listener
            ChatModelListener
            ChatModelRequestContext
            ChatModelResponseContext
            ChatModelErrorContext]
           [dev.langchain4j.model.chat.request ChatRequest]
           [dev.langchain4j.model.chat.response ChatResponse]
           [dev.langchain4j.model ModelProvider]
           [dev.langchain4j.model.output FinishReason TokenUsage]
           [dev.langchain4j.data.message
            AiMessage UserMessage SystemMessage
            ToolExecutionResultMessage ChatMessage]))

;; =============================================================================
;; Java -> EDN Conversion
;; =============================================================================

(defn- model-provider->keyword
  "Convert ModelProvider enum to keyword."
  [^ModelProvider provider]
  (when provider
    (case (.name provider)
      "OPEN_AI" :openai
      "ANTHROPIC" :anthropic
      "GOOGLE_AI_GEMINI" :google-ai-gemini
      "GOOGLE_VERTEX_AI_GEMINI" :vertex-ai-gemini
      "OLLAMA" :ollama
      "MISTRAL_AI" :mistral
      "AMAZON_BEDROCK" :amazon-bedrock
      "AZURE_OPEN_AI" :azure-openai
      "GITHUB_MODELS" :github-models
      "WATSONX" :watsonx
      :other)))

(defn- finish-reason->keyword
  "Convert FinishReason enum to keyword."
  [^FinishReason reason]
  (when reason
    (case (.name reason)
      "STOP" :stop
      "LENGTH" :max-tokens
      "TOOL_EXECUTION" :tool-calls
      "CONTENT_FILTER" :content-filter
      "OTHER" :other
      :unknown)))

(defn- token-usage->edn
  "Convert TokenUsage to EDN map."
  [^TokenUsage usage]
  (when usage
    {:input-tokens (or (.inputTokenCount usage) 0)
     :output-tokens (or (.outputTokenCount usage) 0)
     :total-tokens (or (.totalTokenCount usage) 0)}))

(defn- chat-message->edn
  "Convert a ChatMessage to EDN."
  [^ChatMessage message]
  (cond
    (instance? UserMessage message)
    (let [^UserMessage um message]
      {:message-type :user
       :contents (mapv (fn [content]
                         {:content-type :text
                          :text (str content)})
                       (.contents um))})

    (instance? SystemMessage message)
    (let [^SystemMessage sm message]
      {:message-type :system
       :text (.text sm)})

    (instance? AiMessage message)
    (let [^AiMessage am message
          tool-reqs (.toolExecutionRequests am)]
      (cond-> {:message-type :ai}
        (.text am) (assoc :text (.text am))
        (seq tool-reqs) (assoc :tool-execution-requests
                               (mapv (fn [req]
                                       {:tool-id (.id req)
                                        :tool-name (.name req)
                                        :arguments (.arguments req)})
                                     tool-reqs))))

    (instance? ToolExecutionResultMessage message)
    (let [^ToolExecutionResultMessage trm message]
      {:message-type :tool-execution-result
       :tool-id (.id trm)
       :tool-name (.toolName trm)
       :text (.text trm)})

    :else
    {:message-type :unknown
     :raw (str message)}))

(defn- chat-request->edn
  "Convert ChatRequest to EDN."
  [^ChatRequest request]
  (when request
    {:messages (mapv chat-message->edn (.messages request))
     :parameters (cond-> {:model-name (or (.modelName request) "unknown")}
                   (.temperature request) (assoc :temperature (.temperature request))
                   (.maxOutputTokens request) (assoc :max-output-tokens (.maxOutputTokens request))
                   (.topP request) (assoc :top-p (.topP request))
                   (.topK request) (assoc :top-k (.topK request))
                   (.stopSequences request) (assoc :stop-sequences (vec (.stopSequences request)))
                   (.toolSpecifications request) (assoc :tool-specifications
                                                        (mapv (fn [spec]
                                                                {:name (.name spec)
                                                                 :description (.description spec)})
                                                              (.toolSpecifications request))))}))

(defn- chat-response->edn
  "Convert ChatResponse to EDN."
  [^ChatResponse response]
  (when response
    (let [ai-msg (.aiMessage response)
          metadata (.metadata response)]
      {:ai-message (chat-message->edn ai-msg)
       :response-metadata {:response-id (.id response)
                           :model-name (or (.modelName response) "unknown")
                           :finish-reason (finish-reason->keyword (.finishReason response))
                           :token-usage (token-usage->edn (.tokenUsage response))}})))

;; =============================================================================
;; Context Builders
;; =============================================================================

(defn- request-context->edn
  "Convert ChatModelRequestContext to EDN."
  [^ChatModelRequestContext ctx]
  (cond-> {:request (chat-request->edn (.chatRequest ctx))
           :provider (model-provider->keyword (.modelProvider ctx))
           :attributes (into {} (.attributes ctx))}
    true (assoc :raw-context ctx)))

(defn- response-context->edn
  "Convert ChatModelResponseContext to EDN."
  [^ChatModelResponseContext ctx]
  (let [response-edn (chat-response->edn (.chatResponse ctx))]
    (cond-> {:ai-message (:ai-message response-edn)
             :response-metadata (:response-metadata response-edn)
             :request (chat-request->edn (.chatRequest ctx))
             :provider (model-provider->keyword (.modelProvider ctx))
             :attributes (into {} (.attributes ctx))}
      true (assoc :raw-context ctx))))

(defn- error-context->edn
  "Convert ChatModelErrorContext to EDN."
  [^ChatModelErrorContext ctx]
  (let [^Throwable error (.error ctx)]
    (cond-> {:error {:error-message (.getMessage error)
                     :error-type (.getName (.getClass error))
                     :error-cause (when-let [cause (.getCause error)]
                                    (.getMessage cause))}
             :request (chat-request->edn (.chatRequest ctx))
             :provider (model-provider->keyword (.modelProvider ctx))
             :attributes (into {} (.attributes ctx))}
      true (assoc :raw-context ctx))))

;; =============================================================================
;; Core Listener Creation
;; =============================================================================

(defn create-listener
  "Create a ChatModelListener from Clojure handler functions.
   
   Handlers receive EDN data converted from Java objects.
   Exceptions in handlers are caught and logged to prevent breaking model execution.
   
   Arguments:
   - handlers: Map with optional keys:
     - :on-request  - (fn [request-ctx] ...) called before sending request
     - :on-response - (fn [response-ctx] ...) called after receiving response
     - :on-error    - (fn [error-ctx] ...) called on errors
   
   Returns: ChatModelListener instance
   
   Example:
   ```clojure
   (create-listener
     {:on-request  (fn [ctx] (println \"Sending:\" (count (:messages (:request ctx))) \"messages\"))
      :on-response (fn [ctx] (println \"Tokens:\" (get-in ctx [:response-metadata :token-usage])))
      :on-error    (fn [ctx] (println \"Error:\" (get-in ctx [:error :error-message])))})
   ```"
  [{:keys [on-request on-response on-error]}]
  (reify ChatModelListener
    (onRequest [_ ctx]
      (when on-request
        (try
          (on-request (request-context->edn ctx))
          (catch Exception e
            (log/warn e "Exception in on-request listener handler")))))

    (onResponse [_ ctx]
      (when on-response
        (try
          (on-response (response-context->edn ctx))
          (catch Exception e
            (log/warn e "Exception in on-response listener handler")))))

    (onError [_ ctx]
      (when on-error
        (try
          (on-error (error-context->edn ctx))
          (catch Exception e
            (log/warn e "Exception in on-error listener handler")))))))

;; =============================================================================
;; Pre-built Listeners
;; =============================================================================

(defn logging-listener
  "Create a listener that logs events using clojure.tools.logging.
   
   Arguments (optional):
   - levels: Map with log levels for each event type
     - :request  - log level for requests (default: :debug)
     - :response - log level for responses (default: :info)
     - :error    - log level for errors (default: :error)
   
   Example:
   ```clojure
   (logging-listener)
   (logging-listener {:request :info :response :debug})
   ```"
  ([]
   (logging-listener {:request :debug :response :info :error :error}))
  ([{:keys [request response error]
     :or {request :debug response :info error :error}}]
   (create-listener
    {:on-request
     (fn [ctx]
       (let [msg-count (count (get-in ctx [:request :messages]))
             model (get-in ctx [:request :parameters :model-name])
             provider (:provider ctx)]
         (case request
           :trace (log/trace "Chat request:" {:provider provider :model model :messages msg-count})
           :debug (log/debug "Chat request:" {:provider provider :model model :messages msg-count})
           :info (log/info "Chat request:" {:provider provider :model model :messages msg-count})
           :warn (log/warn "Chat request:" {:provider provider :model model :messages msg-count})
           nil)))

     :on-response
     (fn [ctx]
       (let [tokens (get-in ctx [:response-metadata :token-usage])
             model (get-in ctx [:response-metadata :model-name])
             finish (get-in ctx [:response-metadata :finish-reason])]
         (case response
           :trace (log/trace "Chat response:" {:model model :finish finish :tokens tokens})
           :debug (log/debug "Chat response:" {:model model :finish finish :tokens tokens})
           :info (log/info "Chat response:" {:model model :finish finish :tokens tokens})
           :warn (log/warn "Chat response:" {:model model :finish finish :tokens tokens})
           nil)))

     :on-error
     (fn [ctx]
       (let [err-msg (get-in ctx [:error :error-message])
             err-type (get-in ctx [:error :error-type])
             provider (:provider ctx)]
         (case error
           :trace (log/trace "Chat error:" {:provider provider :type err-type :message err-msg})
           :debug (log/debug "Chat error:" {:provider provider :type err-type :message err-msg})
           :info (log/info "Chat error:" {:provider provider :type err-type :message err-msg})
           :warn (log/warn "Chat error:" {:provider provider :type err-type :message err-msg})
           :error (log/error "Chat error:" {:provider provider :type err-type :message err-msg})
           nil)))})))

(defn token-tracking-listener
  "Create a listener that accumulates token usage in an atom.
   
   The atom will be updated with accumulated stats:
   ```clojure
   {:input-tokens n
    :output-tokens n
    :total-tokens n
    :request-count n
    :last-request {...}  ; most recent request stats
    :by-model {\"model-name\" {:input-tokens n :output-tokens n :total-tokens n :count n}}}
   ```
   
   Arguments:
   - stats-atom: Atom to accumulate stats into
   
   Example:
   ```clojure
   (def stats (atom {}))
   (def tracker (token-tracking-listener stats))
   ;; After some requests...
   @stats
   ;; => {:input-tokens 1500 :output-tokens 800 :total-tokens 2300 :request-count 5 ...}
   ```"
  [stats-atom]
  (create-listener
   {:on-response
    (fn [ctx]
      (let [tokens (get-in ctx [:response-metadata :token-usage])
            model (get-in ctx [:response-metadata :model-name])]
        (when tokens
          (swap! stats-atom
                 (fn [current]
                   (-> current
                       (update :input-tokens (fnil + 0) (:input-tokens tokens))
                       (update :output-tokens (fnil + 0) (:output-tokens tokens))
                       (update :total-tokens (fnil + 0) (:total-tokens tokens))
                       (update :request-count (fnil inc 0))
                       (assoc :last-request {:model model
                                             :tokens tokens
                                             :timestamp (System/currentTimeMillis)})
                       (update-in [:by-model model :input-tokens] (fnil + 0) (:input-tokens tokens))
                       (update-in [:by-model model :output-tokens] (fnil + 0) (:output-tokens tokens))
                       (update-in [:by-model model :total-tokens] (fnil + 0) (:total-tokens tokens))
                       (update-in [:by-model model :count] (fnil inc 0))))))))}))

(defn message-capturing-listener
  "Create a listener that captures all request/response pairs.
   
   Returns a vector: [listener messages-atom]
   
   The messages-atom will contain a vector of conversation records:
   ```clojure
   [{:request {...}
     :response {...}
     :timestamp 1234567890}
    ...]
   ```
   
   Example:
   ```clojure
   (let [[listener messages] (message-capturing-listener)]
     ;; Use listener with model...
     @messages)
   ;; => [{:request {...} :response {...} :timestamp ...}]
   ```"
  []
  (let [messages-atom (atom [])
        current-request (atom nil)]
    [(create-listener
      {:on-request
       (fn [ctx]
         (reset! current-request {:request (:request ctx)
                                  :provider (:provider ctx)
                                  :timestamp (System/currentTimeMillis)}))

       :on-response
       (fn [ctx]
         (when-let [req @current-request]
           (swap! messages-atom conj
                  (assoc req
                         :response {:ai-message (:ai-message ctx)
                                    :metadata (:response-metadata ctx)}
                         :completed-at (System/currentTimeMillis)))
           (reset! current-request nil)))

       :on-error
       (fn [ctx]
         (when-let [req @current-request]
           (swap! messages-atom conj
                  (assoc req
                         :error (:error ctx)
                         :completed-at (System/currentTimeMillis)))
           (reset! current-request nil)))})
     messages-atom]))

(defn request-interceptor
  "Create a listener that can intercept and optionally modify requests.
   
   The interceptor function receives the request context and can use the
   raw-context to modify the request before it's sent.
   
   Note: This is for advanced use cases. The interceptor function receives
   the raw Java ChatModelRequestContext which has mutable attributes.
   
   Arguments:
   - interceptor-fn: (fn [ctx] ...) - receives EDN context with :raw-context
   
   Example:
   ```clojure
   (request-interceptor
     (fn [ctx]
       ;; Add custom attribute
       (.put (.attributes (:raw-context ctx)) \"session-id\" \"abc123\")))
   ```"
  [interceptor-fn]
  (create-listener {:on-request interceptor-fn}))

;; =============================================================================
;; Listener Composition
;; =============================================================================

(defn compose-listeners
  "Compose multiple listeners into one.
   
   All listeners receive all events in the order they are provided.
   If one listener throws an exception, it's logged but other listeners
   still receive the event.
   
   Arguments:
   - listeners: One or more ChatModelListener instances
   
   Returns: A single ChatModelListener that delegates to all provided listeners
   
   Example:
   ```clojure
   (def combined
     (compose-listeners
       (logging-listener)
       (token-tracking-listener stats-atom)
       my-custom-listener))
   ```"
  [& listeners]
  (let [listeners (filterv some? listeners)]
    (reify ChatModelListener
      (onRequest [_ ctx]
        (doseq [^ChatModelListener listener listeners]
          (try
            (.onRequest listener ctx)
            (catch Exception e
              (log/warn e "Exception in composed listener onRequest")))))

      (onResponse [_ ctx]
        (doseq [^ChatModelListener listener listeners]
          (try
            (.onResponse listener ctx)
            (catch Exception e
              (log/warn e "Exception in composed listener onResponse")))))

      (onError [_ ctx]
        (doseq [^ChatModelListener listener listeners]
          (try
            (.onError listener ctx)
            (catch Exception e
              (log/warn e "Exception in composed listener onError"))))))))

;; =============================================================================
;; Utility Functions
;; =============================================================================

(defn listeners->java-list
  "Convert a collection of listeners to a Java List for use with model builders.
   
   Arguments:
   - listeners: A collection of ChatModelListener instances, or a single listener
   
   Returns: java.util.List<ChatModelListener>"
  [listeners]
  (cond
    (nil? listeners) (java.util.ArrayList.)
    (instance? ChatModelListener listeners) (java.util.Arrays/asList (into-array ChatModelListener [listeners]))
    (sequential? listeners) (java.util.ArrayList. ^java.util.Collection (vec listeners))
    :else (java.util.Arrays/asList (into-array ChatModelListener [listeners]))))

(defn with-listeners
  "Helper to add listeners to a model configuration map.
   
   Arguments:
   - config: Model configuration map
   - listeners: Listener(s) to add (single listener or collection)
   
   Returns: Updated config with :listeners key
   
   Example:
   ```clojure
   (-> {:provider :openai :api-key \"...\"}
       (with-listeners [(logging-listener) (token-tracking-listener stats)]))
   ```"
  [config listeners]
  (assoc config :listeners (listeners->java-list listeners)))
