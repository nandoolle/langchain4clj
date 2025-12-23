(ns langchain4clj.listeners-test
  (:require [clojure.test :refer [deftest is testing]]
            [langchain4clj.listeners :as listeners])
  (:import [dev.langchain4j.model.chat.listener
            ChatModelListener
            ChatModelRequestContext
            ChatModelResponseContext
            ChatModelErrorContext]
           [dev.langchain4j.model.chat.request ChatRequest]
           [dev.langchain4j.model.chat.response ChatResponse]
           [dev.langchain4j.model ModelProvider]
           [dev.langchain4j.model.output FinishReason TokenUsage]
           [dev.langchain4j.data.message UserMessage SystemMessage AiMessage]))

;; =============================================================================
;; Test Helpers - Create mock Java objects
;; =============================================================================

(defn- make-chat-request
  "Create a mock ChatRequest for testing."
  [messages model-name]
  (-> (ChatRequest/builder)
      (.messages messages)
      (.modelName model-name)
      (.temperature (double 0.7))
      (.maxOutputTokens (int 1024))
      .build))

(defn- make-chat-response
  "Create a mock ChatResponse for testing."
  [text model-name input-tokens output-tokens]
  (-> (ChatResponse/builder)
      (.aiMessage (AiMessage/from text))
      (.metadata (-> (dev.langchain4j.model.chat.response.ChatResponseMetadata/builder)
                     (.modelName model-name)
                     (.finishReason FinishReason/STOP)
                     (.tokenUsage (TokenUsage. (int input-tokens) (int output-tokens)))
                     .build))
      .build))

(defn- make-request-context
  "Create a mock ChatModelRequestContext."
  [request provider]
  (ChatModelRequestContext. request provider (java.util.HashMap.)))

(defn- make-response-context
  "Create a mock ChatModelResponseContext.
   Constructor order: (response, request, provider, attributes)"
  [request response provider]
  (ChatModelResponseContext. response request provider (java.util.HashMap.)))

(defn- make-error-context
  "Create a mock ChatModelErrorContext.
   Constructor order: (error, request, provider, attributes)"
  [request error provider]
  (ChatModelErrorContext. error request provider (java.util.HashMap.)))

;; =============================================================================
;; create-listener Tests
;; =============================================================================

(deftest create-listener-test
  (testing "creates a ChatModelListener instance"
    (let [listener (listeners/create-listener {})]
      (is (instance? ChatModelListener listener))))

  (testing "on-request handler is called"
    (let [called (atom nil)
          listener (listeners/create-listener
                    {:on-request (fn [ctx] (reset! called ctx))})
          request (make-chat-request [(UserMessage/from "Hello")] "gpt-4o")
          ctx (make-request-context request ModelProvider/OPEN_AI)]
      (.onRequest listener ctx)
      (is (some? @called))
      (is (= :openai (:provider @called)))
      (is (= 1 (count (get-in @called [:request :messages]))))))

  (testing "on-response handler is called"
    (let [called (atom nil)
          listener (listeners/create-listener
                    {:on-response (fn [ctx] (reset! called ctx))})
          request (make-chat-request [(UserMessage/from "Hello")] "gpt-4o")
          response (make-chat-response "Hi there!" "gpt-4o" 10 20)
          ctx (make-response-context request response ModelProvider/OPEN_AI)]
      (.onResponse listener ctx)
      (is (some? @called))
      (is (= :openai (:provider @called)))
      (is (= "Hi there!" (get-in @called [:ai-message :text])))
      (is (= 10 (get-in @called [:response-metadata :token-usage :input-tokens])))
      (is (= 20 (get-in @called [:response-metadata :token-usage :output-tokens])))))

  (testing "on-error handler is called"
    (let [called (atom nil)
          listener (listeners/create-listener
                    {:on-error (fn [ctx] (reset! called ctx))})
          request (make-chat-request [(UserMessage/from "Hello")] "gpt-4o")
          error (Exception. "Test error")
          ctx (make-error-context request error ModelProvider/OPEN_AI)]
      (.onError listener ctx)
      (is (some? @called))
      (is (= "Test error" (get-in @called [:error :error-message])))))

  (testing "handler exceptions are caught and don't propagate"
    (let [listener (listeners/create-listener
                    {:on-request (fn [_] (throw (Exception. "Handler error")))})
          request (make-chat-request [(UserMessage/from "Hello")] "gpt-4o")
          ctx (make-request-context request ModelProvider/OPEN_AI)]
      ;; Should not throw
      (is (nil? (.onRequest listener ctx)))))

  (testing "nil handlers are ignored"
    (let [listener (listeners/create-listener
                    {:on-request nil
                     :on-response nil
                     :on-error nil})
          request (make-chat-request [(UserMessage/from "Hello")] "gpt-4o")
          ctx (make-request-context request ModelProvider/OPEN_AI)]
      ;; Should not throw
      (is (nil? (.onRequest listener ctx))))))

;; =============================================================================
;; Message Conversion Tests
;; =============================================================================

(deftest message-conversion-test
  (testing "UserMessage is converted correctly"
    (let [called (atom nil)
          listener (listeners/create-listener
                    {:on-request (fn [ctx] (reset! called ctx))})
          request (make-chat-request [(UserMessage/from "Hello world")] "gpt-4o")
          ctx (make-request-context request ModelProvider/OPEN_AI)]
      (.onRequest listener ctx)
      (let [msg (first (get-in @called [:request :messages]))]
        (is (= :user (:message-type msg)))
        (is (= 1 (count (:contents msg)))))))

  (testing "SystemMessage is converted correctly"
    (let [called (atom nil)
          listener (listeners/create-listener
                    {:on-request (fn [ctx] (reset! called ctx))})
          request (make-chat-request [(SystemMessage/from "Be helpful")] "gpt-4o")
          ctx (make-request-context request ModelProvider/OPEN_AI)]
      (.onRequest listener ctx)
      (let [msg (first (get-in @called [:request :messages]))]
        (is (= :system (:message-type msg)))
        (is (= "Be helpful" (:text msg))))))

  (testing "AiMessage is converted correctly"
    (let [called (atom nil)
          listener (listeners/create-listener
                    {:on-response (fn [ctx] (reset! called ctx))})
          request (make-chat-request [(UserMessage/from "Hello")] "gpt-4o")
          response (make-chat-response "I am fine" "gpt-4o" 5 10)
          ctx (make-response-context request response ModelProvider/OPEN_AI)]
      (.onResponse listener ctx)
      (let [msg (:ai-message @called)]
        (is (= :ai (:message-type msg)))
        (is (= "I am fine" (:text msg)))))))

;; =============================================================================
;; Provider Conversion Tests
;; =============================================================================

(deftest provider-conversion-test
  (testing "OpenAI provider"
    (let [called (atom nil)
          listener (listeners/create-listener {:on-request (fn [ctx] (reset! called ctx))})
          request (make-chat-request [(UserMessage/from "Hi")] "gpt-4o")
          ctx (make-request-context request ModelProvider/OPEN_AI)]
      (.onRequest listener ctx)
      (is (= :openai (:provider @called)))))

  (testing "Anthropic provider"
    (let [called (atom nil)
          listener (listeners/create-listener {:on-request (fn [ctx] (reset! called ctx))})
          request (make-chat-request [(UserMessage/from "Hi")] "claude-3")
          ctx (make-request-context request ModelProvider/ANTHROPIC)]
      (.onRequest listener ctx)
      (is (= :anthropic (:provider @called)))))

  (testing "Google AI Gemini provider"
    (let [called (atom nil)
          listener (listeners/create-listener {:on-request (fn [ctx] (reset! called ctx))})
          request (make-chat-request [(UserMessage/from "Hi")] "gemini-pro")
          ctx (make-request-context request ModelProvider/GOOGLE_AI_GEMINI)]
      (.onRequest listener ctx)
      (is (= :google-ai-gemini (:provider @called)))))

  (testing "Ollama provider"
    (let [called (atom nil)
          listener (listeners/create-listener {:on-request (fn [ctx] (reset! called ctx))})
          request (make-chat-request [(UserMessage/from "Hi")] "llama3")
          ctx (make-request-context request ModelProvider/OLLAMA)]
      (.onRequest listener ctx)
      (is (= :ollama (:provider @called))))))

;; =============================================================================
;; Token Usage Conversion Tests
;; =============================================================================

(deftest token-usage-conversion-test
  (testing "token usage is extracted correctly"
    (let [called (atom nil)
          listener (listeners/create-listener {:on-response (fn [ctx] (reset! called ctx))})
          request (make-chat-request [(UserMessage/from "Hi")] "gpt-4o")
          response (make-chat-response "Hello!" "gpt-4o" 100 50)
          ctx (make-response-context request response ModelProvider/OPEN_AI)]
      (.onResponse listener ctx)
      (let [usage (get-in @called [:response-metadata :token-usage])]
        (is (= 100 (:input-tokens usage)))
        (is (= 50 (:output-tokens usage)))
        (is (= 150 (:total-tokens usage)))))))

;; =============================================================================
;; logging-listener Tests
;; =============================================================================

(deftest logging-listener-test
  (testing "creates a listener with default levels"
    (let [listener (listeners/logging-listener)]
      (is (instance? ChatModelListener listener))))

  (testing "creates a listener with custom levels"
    (let [listener (listeners/logging-listener {:request :info :response :debug :error :warn})]
      (is (instance? ChatModelListener listener))))

  (testing "handles events without errors"
    (let [listener (listeners/logging-listener)
          request (make-chat-request [(UserMessage/from "Hi")] "gpt-4o")
          response (make-chat-response "Hello!" "gpt-4o" 10 20)
          req-ctx (make-request-context request ModelProvider/OPEN_AI)
          resp-ctx (make-response-context request response ModelProvider/OPEN_AI)]
      ;; Should not throw
      (is (nil? (.onRequest listener req-ctx)))
      (is (nil? (.onResponse listener resp-ctx))))))

;; =============================================================================
;; token-tracking-listener Tests
;; =============================================================================

(deftest token-tracking-listener-test
  (testing "accumulates token usage"
    (let [stats (atom {})
          listener (listeners/token-tracking-listener stats)
          request (make-chat-request [(UserMessage/from "Hi")] "gpt-4o")
          response1 (make-chat-response "Hello!" "gpt-4o" 10 20)
          response2 (make-chat-response "How are you?" "gpt-4o" 15 25)
          ctx1 (make-response-context request response1 ModelProvider/OPEN_AI)
          ctx2 (make-response-context request response2 ModelProvider/OPEN_AI)]
      (.onResponse listener ctx1)
      (.onResponse listener ctx2)
      (is (= 25 (:input-tokens @stats)))
      (is (= 45 (:output-tokens @stats)))
      (is (= 70 (:total-tokens @stats)))
      (is (= 2 (:request-count @stats)))))

  (testing "tracks by model"
    (let [stats (atom {})
          listener (listeners/token-tracking-listener stats)
          request (make-chat-request [(UserMessage/from "Hi")] "gpt-4o")
          response (make-chat-response "Hello!" "gpt-4o" 10 20)
          ctx (make-response-context request response ModelProvider/OPEN_AI)]
      (.onResponse listener ctx)
      (is (= 10 (get-in @stats [:by-model "gpt-4o" :input-tokens])))
      (is (= 20 (get-in @stats [:by-model "gpt-4o" :output-tokens])))
      (is (= 1 (get-in @stats [:by-model "gpt-4o" :count])))))

  (testing "records last request"
    (let [stats (atom {})
          listener (listeners/token-tracking-listener stats)
          request (make-chat-request [(UserMessage/from "Hi")] "gpt-4o")
          response (make-chat-response "Hello!" "gpt-4o" 10 20)
          ctx (make-response-context request response ModelProvider/OPEN_AI)]
      (.onResponse listener ctx)
      (is (= "gpt-4o" (get-in @stats [:last-request :model])))
      (is (some? (get-in @stats [:last-request :timestamp]))))))

;; =============================================================================
;; message-capturing-listener Tests
;; =============================================================================

(deftest message-capturing-listener-test
  (testing "returns listener and atom tuple"
    (let [[listener messages] (listeners/message-capturing-listener)]
      (is (instance? ChatModelListener listener))
      (is (instance? clojure.lang.Atom messages))
      (is (= [] @messages))))

  (testing "captures request/response pairs"
    (let [[listener messages] (listeners/message-capturing-listener)
          request (make-chat-request [(UserMessage/from "Hi")] "gpt-4o")
          response (make-chat-response "Hello!" "gpt-4o" 10 20)
          req-ctx (make-request-context request ModelProvider/OPEN_AI)
          resp-ctx (make-response-context request response ModelProvider/OPEN_AI)]
      (.onRequest listener req-ctx)
      (.onResponse listener resp-ctx)
      (is (= 1 (count @messages)))
      (let [captured (first @messages)]
        (is (some? (:request captured)))
        (is (some? (:response captured)))
        (is (some? (:timestamp captured)))
        (is (some? (:completed-at captured))))))

  (testing "captures errors"
    (let [[listener messages] (listeners/message-capturing-listener)
          request (make-chat-request [(UserMessage/from "Hi")] "gpt-4o")
          error (Exception. "Test error")
          req-ctx (make-request-context request ModelProvider/OPEN_AI)
          err-ctx (make-error-context request error ModelProvider/OPEN_AI)]
      (.onRequest listener req-ctx)
      (.onError listener err-ctx)
      (is (= 1 (count @messages)))
      (let [captured (first @messages)]
        (is (some? (:error captured)))
        (is (= "Test error" (get-in captured [:error :error-message])))))))

;; =============================================================================
;; compose-listeners Tests
;; =============================================================================

(deftest compose-listeners-test
  (testing "composes multiple listeners"
    (let [calls (atom [])
          listener1 (listeners/create-listener
                     {:on-request (fn [_] (swap! calls conj :listener1))})
          listener2 (listeners/create-listener
                     {:on-request (fn [_] (swap! calls conj :listener2))})
          composed (listeners/compose-listeners listener1 listener2)
          request (make-chat-request [(UserMessage/from "Hi")] "gpt-4o")
          ctx (make-request-context request ModelProvider/OPEN_AI)]
      (.onRequest composed ctx)
      (is (= [:listener1 :listener2] @calls))))

  (testing "handles nil listeners"
    (let [calls (atom [])
          listener (listeners/create-listener
                    {:on-request (fn [_] (swap! calls conj :called))})
          composed (listeners/compose-listeners nil listener nil)
          request (make-chat-request [(UserMessage/from "Hi")] "gpt-4o")
          ctx (make-request-context request ModelProvider/OPEN_AI)]
      (.onRequest composed ctx)
      (is (= [:called] @calls))))

  (testing "one failure doesn't affect others"
    (let [calls (atom [])
          listener1 (listeners/create-listener
                     {:on-request (fn [_] (throw (Exception. "Error")))})
          listener2 (listeners/create-listener
                     {:on-request (fn [_] (swap! calls conj :listener2))})
          composed (listeners/compose-listeners listener1 listener2)
          request (make-chat-request [(UserMessage/from "Hi")] "gpt-4o")
          ctx (make-request-context request ModelProvider/OPEN_AI)]
      (.onRequest composed ctx)
      (is (= [:listener2] @calls)))))

;; =============================================================================
;; listeners->java-list Tests
;; =============================================================================

(deftest listeners->java-list-test
  (testing "converts collection to Java list"
    (let [listener1 (listeners/logging-listener)
          listener2 (listeners/logging-listener)
          result (listeners/listeners->java-list [listener1 listener2])]
      (is (instance? java.util.List result))
      (is (= 2 (.size result)))))

  (testing "handles single listener"
    (let [listener (listeners/logging-listener)
          result (listeners/listeners->java-list listener)]
      (is (instance? java.util.List result))
      (is (= 1 (.size result)))))

  (testing "handles nil"
    (let [result (listeners/listeners->java-list nil)]
      (is (instance? java.util.List result))
      (is (= 0 (.size result))))))

;; =============================================================================
;; with-listeners Tests
;; =============================================================================

(deftest with-listeners-test
  (testing "adds listeners to config"
    (let [listener (listeners/logging-listener)
          config {:provider :openai :api-key "test"}
          result (listeners/with-listeners config [listener])]
      (is (contains? result :listeners))
      (is (instance? java.util.List (:listeners result))))))
