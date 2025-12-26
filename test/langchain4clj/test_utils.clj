(ns langchain4clj.test-utils
  "Test utilities and mocks for langchain4clj"
  (:require [langchain4clj :as llm])
  (:import [dev.langchain4j.model.chat ChatModel]
           [dev.langchain4j.data.message UserMessage SystemMessage AiMessage]
           [dev.langchain4j.model.chat.response ChatResponse]
           [java.util ArrayList]))

;; ============================================================================
;; Mock Models
;; ============================================================================

(defn create-java-mock-chat-model
  "Creates a complete Java ChatModel mock with all required overloads.
   
   Usage:
   (create-java-mock-chat-model \"Response text\")
   (create-java-mock-chat-model (fn [msg] (str \"Echo: \" msg)))
   
   The response-fn can be:
   - A string: returns same response always
   - A function of one arg: receives message/request and returns response string"
  [response-fn]
  (let [response-fn (if (string? response-fn)
                      (constantly response-fn)
                      response-fn)
        create-ai-message (fn [text]
                            (-> (AiMessage/builder)
                                (.text text)
                                (.build)))
        create-chat-response (fn [ai-message]
                               (-> (ChatResponse/builder)
                                   (.aiMessage ai-message)
                                   (.build)))]
    (reify ChatModel
      ;; Overload 1: chat(String) -> String
      (^String chat [_ ^String message]
        (response-fn message))

      ;; Overload 2: chat(List<ChatMessage>) -> ChatResponse
      (^dev.langchain4j.model.chat.response.ChatResponse chat [_ ^java.util.List messages]
        (let [last-msg (when (seq messages) (last messages))
              text (cond
                     (instance? UserMessage last-msg) (.singleText ^UserMessage last-msg)
                     (instance? SystemMessage last-msg) (.text ^SystemMessage last-msg)
                     :else "message")
              response-text (response-fn text)
              ai-message (create-ai-message response-text)]
          (create-chat-response ai-message)))

      ;; Overload 3: chat(ChatRequest) -> ChatResponse
      (^dev.langchain4j.model.chat.response.ChatResponse chat [_ ^dev.langchain4j.model.chat.request.ChatRequest request]
        (let [messages (.messages request)
              last-msg (when (seq messages) (last messages))
              text (cond
                     (instance? UserMessage last-msg) (.singleText ^UserMessage last-msg)
                     (instance? SystemMessage last-msg) (.text ^SystemMessage last-msg)
                     :else "message")
              response-text (response-fn text)
              ai-message (create-ai-message response-text)]
          (create-chat-response ai-message)))

      ;; Overload 4: chat(ChatMessage...) -> ChatResponse  
      (^dev.langchain4j.model.chat.response.ChatResponse chat [this ^"[Ldev.langchain4j.data.message.ChatMessage;" messages]
        (let [msg-list (ArrayList. (seq messages))]
          (.chat this msg-list))))))

(defn create-java-mock-chat-model-with-response
  "Creates a ChatModel mock that returns a custom ChatResponse.
   
   Usage:
   (create-java-mock-chat-model-with-response
     (fn [messages] 
       (-> (ChatResponse/builder)
           (.aiMessage (-> (AiMessage/builder) (.text \"Response\") (.build)))
           (.build))))
   
   The response-fn receives the messages list and must return a ChatResponse object."
  [response-fn]
  (reify ChatModel
    ;; Overload 1: chat(String) -> String
    (^String chat [_ ^String message]
      (.text (.aiMessage (response-fn [(UserMessage. message)]))))

    ;; Overload 2: chat(List<ChatMessage>) -> ChatResponse
    (^dev.langchain4j.model.chat.response.ChatResponse chat [_ ^java.util.List messages]
      (response-fn messages))

    ;; Overload 3: chat(ChatRequest) -> ChatResponse
    (^dev.langchain4j.model.chat.response.ChatResponse chat [_ ^dev.langchain4j.model.chat.request.ChatRequest request]
      (response-fn (.messages request)))

    ;; Overload 4: chat(ChatMessage...) -> ChatResponse  
    (^dev.langchain4j.model.chat.response.ChatResponse chat [this ^"[Ldev.langchain4j.data.message.ChatMessage;" messages]
      (let [msg-list (ArrayList. (seq messages))]
        (.chat this msg-list)))))

(defn create-mock-chat-model
  "Creates a mock model that returns predefined responses without Java dependencies"
  ([responses]
   (create-mock-chat-model responses {}))
  ([responses options]
   (let [call-count (atom 0)
         history (atom [])]
     {:type :mock-chat-model
      :responses responses
      :options options
      :call-count call-count
      :history history
      ;; Mock implementation of chat function
      :chat (fn [message]
              (let [idx @call-count
                    response (if (< idx (count responses))
                               (nth responses idx)
                               "Default mock response")]
                (swap! call-count inc)
                (swap! history conj {:message message :response response})
                response))
      ;; For debugging
      :get-history (fn [] @history)
      :reset (fn []
               (reset! call-count 0)
               (reset! history []))})))

(defn create-mock-tool
  "Creates a mock tool for testing"
  [{:keys [name description parameters executor-fn]
    :or {executor-fn (fn [args] (str "Mock execution: " args))}}]
  {:name name
   :description description
   :parameters (or parameters {:type "object" :properties {}})
   :executor-fn executor-fn
   :type :mock-tool})

(defn create-mock-memory
  "Creates a mock memory for testing"
  [max-messages]
  (let [messages (atom [])]
    {:type :mock-memory
     :max-messages max-messages
     :messages messages
     :add-message (fn [msg]
                    (swap! messages conj msg)
                    (when (> (count @messages) max-messages)
                      (swap! messages #(vec (take-last max-messages %)))))
     :get-messages (fn [] @messages)
     :clear (fn [] (reset! messages []))}))

;; ============================================================================
;; Mock Factories
;; ============================================================================

(defn mock-llm-create-model
  "Mock function to replace llm/create-model in tests"
  [config]
  (let [provider (:provider config)
        temperature (:temperature config 0.7)]
    (cond
      ;; Different responses based on provider
      (= provider :openai)
      (create-mock-chat-model
       ["OpenAI response" "Second OpenAI response"]
       {:provider :openai :temperature temperature})

      (= provider :anthropic)
      (create-mock-chat-model
       ["Claude response" "Second Claude response"]
       {:provider :anthropic :temperature temperature})

      :else
      (create-mock-chat-model
       ["Generic response"]
       {:provider :generic}))))

(defn mock-agents-create-memory
  "Mock function to replace agents/create-memory"
  [max-messages]
  (create-mock-memory max-messages))

;; ============================================================================
;; Test Helpers
;; ============================================================================

(defn with-mock-llm
  "Macro to run tests with mocked LLM"
  [_responses & body]
  `(with-redefs [llm/chat (fn [model# message#]
                            (if (map? model#)
                              ((:chat model#) message#)
                              "Mock response"))]
     ~@body))

(defn simulate-conversation
  "Simulates a conversation with a mock model"
  [model messages]
  (mapv #((:chat model) %) messages))

(defn create-test-agent
  "Creates a test agent with mock model"
  [{:keys [name responses]}]
  (let [mock-model (create-mock-chat-model responses)]
    {:name (or name "TestAgent")
     :model mock-model
     :process (fn [input _context]
                ((:chat mock-model) input))}))

;; ============================================================================
;; Response Patterns for Testing
;; ============================================================================

(def response-patterns
  "Common response patterns for testing different scenarios"
  {:analysis ["Based on my analysis, the key insights are..."
              "The data suggests three main trends..."
              "In conclusion, we should focus on..."]

   :coding ["Here's the implementation:\n```clojure\n(defn solve [x] (* x 2))\n```"
            "I've fixed the bug. The issue was..."
            "The optimized version runs 50% faster."]

   :review ["The code looks good overall. Minor suggestions..."
            "Found 3 potential issues: 1) Memory leak..."
            "Approved with comments."]

   :planning ["Phase 1: Research and requirements..."
              "Phase 2: Implementation..."
              "Phase 3: Testing and deployment..."]

   :error ["I encountered an error: Invalid input"
           "Unable to process request"
           "System temporarily unavailable"]

   :tools ["I'll need to use the calculator tool..."
           "Fetching data from the API..."
           "Executing database query..."]})

(defn get-response-pattern
  "Gets a response pattern for testing"
  [pattern-key]
  (get response-patterns pattern-key ["Default test response"]))

;; ============================================================================
;; Assertion Helpers  
;; ============================================================================

(defn assert-chat-called
  "Assert that chat was called with expected message"
  [mock-model expected-message]
  (let [history ((:get-history mock-model))]
    (some #(= (:message %) expected-message) history)))

(defn assert-response-contains
  "Assert that response contains expected text"
  [response expected-text]
  (and (string? response)
       (.contains response expected-text)))

(defn assert-agent-processed
  "Assert that agent processed input correctly"
  [agent input expected-output]
  (let [result ((:process agent) input nil)]
    (= result expected-output)))