(ns langchain4clj.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [langchain4clj.core :as core]
            )
  (:import [dev.langchain4j.model.chat ChatModel]
           [dev.langchain4j.data.message AiMessage]
           [dev.langchain4j.model.chat.response ChatResponse]
           [dev.langchain4j.model.chat.request ResponseFormat]))

;; ============================================================================
;; Model Creation Tests
;; ============================================================================

(deftest test-create-model
  (testing "Create model with different providers"
    (with-redefs [core/build-model (fn [config]
                                     (reify ChatModel
                                       (^String chat [_ ^String message]
                                         (str (:provider config) ": " message))))]

      (let [openai-model (core/create-model {:provider :openai :api-key "test"})
            anthropic-model (core/create-model {:provider :anthropic :api-key "test"})]

        ;; Both should be ChatLanguageModel instances
        (is (instance? ChatModel openai-model))
        (is (instance? ChatModel anthropic-model))

        ;; Each provider should work correctly
        (is (= ":openai: test" (core/chat openai-model "test")))
        (is (= ":anthropic: test" (core/chat anthropic-model "test")))))))

;; ============================================================================
;; Chat Tests - Simple Arity (Backwards Compatibility)
;; ============================================================================

(deftest test-chat-simple
  (testing "Simple chat arity works correctly"
    (let [mock-model (reify ChatModel
                       (^String chat [_ ^String message]
                         (str "Echo: " message)))
          response (core/chat mock-model "Hello, world!")]

      (is (= "Echo: Hello, world!" response))
      (is (string? response)))))

;; ============================================================================
;; Chat Tests - Options Arity
;; ============================================================================

(deftest test-chat-with-empty-options
  (testing "Chat with empty options uses simple method for efficiency"
    (let [simple-called (atom false)
          request-called (atom false)
          mock-model (reify ChatModel
                       (^String chat [_ ^String message]
                         (reset! simple-called true)
                         "Simple response")
                       (^dev.langchain4j.model.chat.response.ChatResponse chat [_ ^dev.langchain4j.model.chat.request.ChatRequest request]
                         (reset! request-called true)
                         (throw (Exception. "Should not use ChatRequest for empty options"))))]

      (core/chat mock-model "Test" {})

      (is @simple-called "Empty options should use simple chat method")
      (is (not @request-called) "Empty options should NOT use ChatRequest method"))))

(deftest test-chat-with-options-uses-chatrequest
  (testing "Chat with any option uses ChatRequest method"
    (let [simple-called (atom false)
          request-called (atom false)
          mock-model (reify ChatModel
                       (^String chat [_ ^String message]
                         (reset! simple-called true)
                         "Simple")
                       (^dev.langchain4j.model.chat.response.ChatResponse chat [_ ^dev.langchain4j.model.chat.request.ChatRequest request]
                         (reset! request-called true)
                         (-> (ChatResponse/builder)
                             (.aiMessage (-> (AiMessage/builder)
                                             (.text "ChatResponse")
                                             (.build)))
                             (.build))))]

      ;; Test with temperature
      (core/chat mock-model "Test" {:temperature 0.5})
      (is (not @simple-called) "Options should NOT use simple method")
      (is @request-called "Options should use ChatRequest method")

      ;; Reset and test with different option
      (reset! simple-called false)
      (reset! request-called false)

      (core/chat mock-model "Test" {:max-tokens 100})
      (is (not @simple-called))
      (is @request-called))))

(deftest test-chat-with-multiple-options
  (testing "Chat with multiple options passes them to ChatRequest"
    (let [request-received (atom nil)
          mock-model (reify ChatModel
                       (^String chat [_ ^String message]
                         "simple")
                       (^dev.langchain4j.model.chat.response.ChatResponse chat [_ ^dev.langchain4j.model.chat.request.ChatRequest request]
                         (reset! request-received request)
                         (-> (ChatResponse/builder)
                             (.aiMessage (-> (AiMessage/builder)
                                             (.text "Response")
                                             (.build)))
                             (.build))))]

      (core/chat mock-model "Test message"
                 {:temperature 0.7
                  :max-tokens 500
                  :system-message "You are helpful"
                  :top-p 0.9})

      (is (some? @request-received) "ChatRequest should be created with options"))))

(deftest test-chat-arity-selection
  (testing "Chat correctly selects arity based on arguments"
    (let [simple-calls (atom 0)
          request-calls (atom 0)
          mock-model (reify ChatModel
                       (^String chat [_ ^String message]
                         (swap! simple-calls inc)
                         "simple")
                       (^dev.langchain4j.model.chat.response.ChatResponse chat [_ ^dev.langchain4j.model.chat.request.ChatRequest request]
                         (swap! request-calls inc)
                         (-> (ChatResponse/builder)
                             (.aiMessage (-> (AiMessage/builder)
                                             (.text "Response")
                                             (.build)))
                             (.build))))]

      ;; 1-arity: simple chat
      (core/chat mock-model "Test1")
      (is (= 1 @simple-calls))
      (is (= 0 @request-calls))

      ;; 2-arity with empty options: simple chat (optimization)
      (core/chat mock-model "Test2" {})
      (is (= 2 @simple-calls))
      (is (= 0 @request-calls))

      ;; 2-arity with options: ChatRequest
      (core/chat mock-model "Test3" {:temperature 0.5})
      (is (= 2 @simple-calls))
      (is (= 1 @request-calls))

      ;; 2-arity with tools: ChatRequest
      (core/chat mock-model "Test4" {:tools [(Object.)]})
      (is (= 2 @simple-calls))
      (is (= 2 @request-calls)))))

;; ============================================================================
;; Integration-Style Tests
;; ============================================================================

(deftest test-chat-options-integration
  (testing "Various option types work correctly"
    (let [options-tested (atom #{})
          mock-model (reify ChatModel
                       (^String chat [_ ^String message]
                         "simple")
                       (^dev.langchain4j.model.chat.response.ChatResponse chat [_ ^dev.langchain4j.model.chat.request.ChatRequest request]
                         ;; Track which options triggered ChatRequest
                         (reset! options-tested #{:chatrequest-used})
                         (-> (ChatResponse/builder)
                             (.aiMessage (-> (AiMessage/builder)
                                             (.text "Response")
                                             (.build)))
                             (.build))))]

      ;; System message
      (core/chat mock-model "Hi" {:system-message "Be helpful"})
      (is (contains? @options-tested :chatrequest-used))

      ;; Temperature
      (reset! options-tested #{})
      (core/chat mock-model "Hi" {:temperature 0.3})
      (is (contains? @options-tested :chatrequest-used))

      ;; Max tokens
      (reset! options-tested #{})
      (core/chat mock-model "Hi" {:max-tokens 100})
      (is (contains? @options-tested :chatrequest-used))

      ;; Tools
      (reset! options-tested #{})
      (core/chat mock-model "Hi" {:tools [(Object.)]})
      (is (contains? @options-tested :chatrequest-used)))))

;; ============================================================================
;; JSON Mode Tests (Native Response Format Support)
;; ============================================================================

(deftest test-with-json-mode
  (testing "with-json-mode helper sets ResponseFormat/JSON"
    (let [config (core/with-json-mode {:temperature 0.7})]
      (is (= 0.7 (:temperature config)))
      (is (= ResponseFormat/JSON (:response-format config))))))

(deftest test-with-response-format
  (testing "with-response-format helper sets custom format"
    (let [config (core/with-response-format {:temperature 0.7} ResponseFormat/JSON)]
      (is (= 0.7 (:temperature config)))
      (is (= ResponseFormat/JSON (:response-format config))))))

(deftest test-chat-with-json-mode
  (testing "Chat with JSON mode passes ResponseFormat to ChatRequest"
    (let [response-format-received (atom nil)
          mock-model (reify ChatModel
                       (^String chat [_ ^String message]
                         "simple")
                       (^dev.langchain4j.model.chat.response.ChatResponse chat [_ ^dev.langchain4j.model.chat.request.ChatRequest request]
                         (reset! response-format-received (.responseFormat request))
                         (-> (ChatResponse/builder)
                             (.aiMessage (-> (AiMessage/builder)
                                             (.text "{\"name\": \"John\", \"age\": 30}")
                                             (.build)))
                             (.build))))]

      ;; Test with :response-format key
      (core/chat mock-model "Return JSON" {:response-format ResponseFormat/JSON})
      (is (= ResponseFormat/JSON @response-format-received))

      ;; Test with with-json-mode helper
      (reset! response-format-received nil)
      (core/chat mock-model "Return JSON" (core/with-json-mode {}))
      (is (= ResponseFormat/JSON @response-format-received)))))

(deftest test-json-mode-threading
  (testing "JSON mode works with threading-first pattern"
    (let [response-format-received (atom nil)
          mock-model (reify ChatModel
                       (^String chat [_ ^String message]
                         "simple")
                       (^dev.langchain4j.model.chat.response.ChatResponse chat [_ ^dev.langchain4j.model.chat.request.ChatRequest request]
                         (reset! response-format-received (.responseFormat request))
                         (-> (ChatResponse/builder)
                             (.aiMessage (-> (AiMessage/builder)
                                             (.text "{\"result\": true}")
                                             (.build)))
                             (.build))))]

      ;; Threading-first pattern
      (-> {:temperature 0.5}
          core/with-json-mode
          (as-> opts (core/chat mock-model "Test" opts)))

      (is (= ResponseFormat/JSON @response-format-received)))))

(deftest test-json-mode-with-other-options
  (testing "JSON mode combines with other options correctly"
    (let [request-received (atom nil)
          mock-model (reify ChatModel
                       (^String chat [_ ^String message]
                         "simple")
                       (^dev.langchain4j.model.chat.response.ChatResponse chat [_ ^dev.langchain4j.model.chat.request.ChatRequest request]
                         (reset! request-received request)
                         (-> (ChatResponse/builder)
                             (.aiMessage (-> (AiMessage/builder)
                                             (.text "{\"data\": []}")
                                             (.build)))
                             (.build))))]

      (core/chat mock-model "Return JSON"
                 {:response-format ResponseFormat/JSON
                  :temperature 0.7
                  :max-tokens 500
                  :system-message "You are helpful"})

      (is (some? @request-received))
      (is (= ResponseFormat/JSON (.responseFormat @request-received)))
      (is (= 0.7 (.temperature @request-received)))
      (is (= 500 (.maxOutputTokens @request-received))))))

;; ============================================================================
;; Google AI Gemini Tests
;; ============================================================================

(deftest test-google-ai-gemini-model-creation
  (testing "Google AI Gemini model creation via create-model"
    (with-redefs [core/build-model (fn [config]
                                     (when (= :google-ai-gemini (:provider config))
                                       (reify ChatModel
                                         (^String chat [_ ^String message]
                                           (str "Gemini: " message)))))]

      (let [model (core/create-model {:provider :google-ai-gemini :api-key "test-key"})]
        (is (instance? ChatModel model))
        (is (= "Gemini: Hello" (core/chat model "Hello")))))))

(deftest test-google-ai-gemini-defaults
  (testing "Google AI Gemini applies correct defaults via build-model"
    (let [config-received (atom nil)]
      (with-redefs [core/build-model
                    (fn [config]
                      (when (= :google-ai-gemini (:provider config))
                        (reset! config-received config)
                        (reify ChatModel
                          (^String chat [_ ^String message] "test"))))]

        ;; Test with minimal config
        (core/create-model {:provider :google-ai-gemini :api-key "test-key"})

        (is (= :google-ai-gemini (:provider @config-received)))
        (is (= "test-key" (:api-key @config-received)))))))

(deftest test-google-ai-gemini-custom-config
  (testing "Google AI Gemini accepts custom configuration"
    (let [config-received (atom nil)]
      (with-redefs [core/build-model
                    (fn [config]
                      (when (= :google-ai-gemini (:provider config))
                        (reset! config-received config)
                        (reify ChatModel
                          (^String chat [_ ^String message] "test"))))]

        (core/create-model
         {:provider :google-ai-gemini
          :api-key "test-key"
          :model "gemini-1.5-pro"
          :temperature 0.9
          :timeout 120000
          :max-tokens 2000
          :max-retries 5
          :log-requests? true
          :log-responses? true})

        (is (= :google-ai-gemini (:provider @config-received)))
        (is (= "gemini-1.5-pro" (:model @config-received)))
        (is (= 0.9 (:temperature @config-received)))
        (is (= 120000 (:timeout @config-received)))
        (is (= 2000 (:max-tokens @config-received)))
        (is (= 5 (:max-retries @config-received)))
        (is (= true (:log-requests? @config-received)))
        (is (= true (:log-responses? @config-received)))))))

(deftest test-google-ai-gemini-threading
  (testing "Google AI Gemini configuration with threading patterns"
    (with-redefs [core/build-model
                  (fn [config]
                    (when (= :google-ai-gemini (:provider config))
                      (reify ChatModel
                        (^String chat [_ ^String message]
                          (str "Model: " (:model config))))))]

      (let [model (core/create-model
                   (-> {:provider :google-ai-gemini
                        :api-key "test-key"}
                       (assoc :model "gemini-1.5-pro")
                       (assoc :temperature 0.8)))]
        (is (instance? ChatModel model))))))

(deftest test-google-ai-gemini-via-create-model
  (testing "Google AI Gemini multimethod dispatch works correctly"
    (with-redefs [core/build-model
                  (fn [config]
                    (when (= :google-ai-gemini (:provider config))
                      (reify ChatModel
                        (^String chat [_ ^String message]
                          (str "Gemini via create-model: " message)))))]

      (let [model (core/create-model {:provider :google-ai-gemini
                                      :api-key "test-key"})]
        (is (instance? ChatModel model))
        (is (= "Gemini via create-model: test" (core/chat model "test")))))))

;; ============================================================================
;; Vertex AI Gemini Tests
;; ============================================================================

(deftest test-vertex-ai-gemini-model-creation
  (testing "Vertex AI Gemini model creation via create-model"
    (with-redefs [core/build-model (fn [config]
                                     (when (= :vertex-ai-gemini (:provider config))
                                       (reify ChatModel
                                         (^String chat [_ ^String message]
                                           (str "Vertex Gemini: " message)))))]

      (let [model (core/create-model {:provider :vertex-ai-gemini :project "test-project"})]
        (is (instance? ChatModel model))
        (is (= "Vertex Gemini: Hello" (core/chat model "Hello")))))))

(deftest test-vertex-ai-gemini-defaults
  (testing "Vertex AI Gemini applies correct defaults via build-model"
    (let [config-received (atom nil)]
      (with-redefs [core/build-model
                    (fn [config]
                      (when (= :vertex-ai-gemini (:provider config))
                        (reset! config-received config)
                        (reify ChatModel
                          (^String chat [_ ^String message] "test"))))]

        ;; Test with minimal config
        (core/create-model {:provider :vertex-ai-gemini :project "test-project"})

        (is (= :vertex-ai-gemini (:provider @config-received)))
        (is (= "test-project" (:project @config-received)))))))

(deftest test-vertex-ai-gemini-custom-config
  (testing "Vertex AI Gemini accepts custom configuration"
    (let [config-received (atom nil)]
      (with-redefs [core/build-model
                    (fn [config]
                      (when (= :vertex-ai-gemini (:provider config))
                        (reset! config-received config)
                        (reify ChatModel
                          (^String chat [_ ^String message] "test"))))]

        (core/create-model
         {:provider :vertex-ai-gemini
          :project "my-gcp-project"
          :location "us-west1"
          :model "gemini-1.5-pro"
          :temperature 0.9
          :timeout 120000
          :max-tokens 2000
          :max-retries 5})

        (is (= :vertex-ai-gemini (:provider @config-received)))
        (is (= "my-gcp-project" (:project @config-received)))
        (is (= "us-west1" (:location @config-received)))
        (is (= "gemini-1.5-pro" (:model @config-received)))
        (is (= 0.9 (:temperature @config-received)))
        (is (= 120000 (:timeout @config-received)))
        (is (= 2000 (:max-tokens @config-received)))
        (is (= 5 (:max-retries @config-received)))))))

(deftest test-vertex-ai-gemini-threading
  (testing "Vertex AI Gemini configuration with threading patterns"
    (with-redefs [core/build-model
                  (fn [config]
                    (when (= :vertex-ai-gemini (:provider config))
                      (reify ChatModel
                        (^String chat [_ ^String message]
                          (str "Model: " (:model config))))))]

      (let [model (core/create-model
                   (-> {:provider :vertex-ai-gemini
                        :project "test-project"}
                       (assoc :location "us-east1")
                       (assoc :model "gemini-1.5-pro")
                       (assoc :temperature 0.8)))]
        (is (instance? ChatModel model))))))

(deftest test-vertex-ai-gemini-via-create-model
  (testing "Vertex AI Gemini multimethod dispatch works correctly"
    (with-redefs [core/build-model
                  (fn [config]
                    (when (= :vertex-ai-gemini (:provider config))
                      (reify ChatModel
                        (^String chat [_ ^String message]
                          (str "Vertex Gemini via create-model: " message)))))]

      (let [model (core/create-model {:provider :vertex-ai-gemini
                                      :project "test-project"})]
        (is (instance? ChatModel model))
        (is (= "Vertex Gemini via create-model: test" (core/chat model "test")))))))

;; ============================================================================
;; Cross-Provider Comparison Tests
;; ============================================================================

;; ============================================================================
;; Ollama Tests
;; ============================================================================

(deftest test-ollama-model-creation
  (testing "Ollama model creation via create-model"
    (with-redefs [core/build-model (fn [config]
                                     (when (= :ollama (:provider config))
                                       (reify ChatModel
                                         (^String chat [_ ^String message]
                                           (str "Ollama: " message)))))]

      (let [model (core/create-model {:provider :ollama})]
        (is (instance? ChatModel model))
        (is (= "Ollama: Hello" (core/chat model "Hello")))))))

(deftest test-ollama-defaults
  (testing "Ollama applies correct defaults via build-model"
    (let [config-received (atom nil)]
      (with-redefs [core/build-ollama-model
                    (fn [config]
                      (reset! config-received config)
                      (reify ChatModel
                        (^String chat [_ ^String message] "test")))]

        ;; Test with minimal config (no API key needed)
        (core/create-model {:provider :ollama})

        (is (= "http://localhost:11434" (:base-url @config-received)))
        (is (= "llama3.1" (:model @config-received)))
        (is (= 0.7 (:temperature @config-received)))))))

(deftest test-ollama-custom-config
  (testing "Ollama accepts custom configuration"
    (let [config-received (atom nil)]
      (with-redefs [core/build-model
                    (fn [config]
                      (when (= :ollama (:provider config))
                        (reset! config-received config)
                        (reify ChatModel
                          (^String chat [_ ^String message] "test"))))]

        (core/create-model
         {:provider :ollama
          :base-url "http://192.168.1.100:11434"
          :model "mistral"
          :temperature 0.9
          :timeout 120000
          :top-k 50
          :top-p 0.95
          :seed 42
          :num-predict 500
          :max-retries 5
          :log-requests? true
          :log-responses? true})

        (is (= :ollama (:provider @config-received)))
        (is (= "http://192.168.1.100:11434" (:base-url @config-received)))
        (is (= "mistral" (:model @config-received)))
        (is (= 0.9 (:temperature @config-received)))
        (is (= 120000 (:timeout @config-received)))
        (is (= 50 (:top-k @config-received)))
        (is (= 0.95 (:top-p @config-received)))
        (is (= 42 (:seed @config-received)))
        (is (= 500 (:num-predict @config-received)))
        (is (= 5 (:max-retries @config-received)))
        (is (= true (:log-requests? @config-received)))
        (is (= true (:log-responses? @config-received)))))))

(deftest test-ollama-threading
  (testing "Ollama configuration with threading patterns"
    (with-redefs [core/build-model
                  (fn [config]
                    (when (= :ollama (:provider config))
                      (reify ChatModel
                        (^String chat [_ ^String message]
                          (str "Model: " (:model config))))))]

      (let [model (core/create-model
                   (-> {:provider :ollama}
                       (assoc :model "codellama")
                       (assoc :temperature 0.8)))]
        (is (instance? ChatModel model))))))

(deftest test-ollama-via-create-model
  (testing "Ollama multimethod dispatch works correctly"
    (with-redefs [core/build-model
                  (fn [config]
                    (when (= :ollama (:provider config))
                      (reify ChatModel
                        (^String chat [_ ^String message]
                          (str "Ollama via create-model: " message)))))]

      (let [model (core/create-model {:provider :ollama})]
        (is (instance? ChatModel model))
        (is (= "Ollama via create-model: test" (core/chat model "test")))))))

(deftest test-ollama-helper-function
  (testing "ollama-model helper function works correctly"
    (with-redefs [core/build-ollama-model
                  (fn [config]
                    (reify ChatModel
                      (^String chat [_ ^String message]
                        (str "Ollama helper: " message))))]

      (let [model (core/ollama-model {})]
        (is (instance? ChatModel model))
        (is (= "Ollama helper: test" (core/chat model "test")))))))

;; ============================================================================
;; Mistral Tests
;; ============================================================================

(deftest test-mistral-model-creation
  (testing "Mistral model creation via create-model"
    (with-redefs [core/build-model (fn [config]
                                     (when (= :mistral (:provider config))
                                       (reify ChatModel
                                         (^String chat [_ ^String message]
                                           (str "Mistral: " message)))))]

      (let [model (core/create-model {:provider :mistral :api-key "test-key"})]
        (is (instance? ChatModel model))
        (is (= "Mistral: Hello" (core/chat model "Hello")))))))

(deftest test-mistral-defaults
  (testing "Mistral applies correct defaults via build-model"
    (let [config-received (atom nil)]
      (with-redefs [core/build-mistral-model
                    (fn [config]
                      (reset! config-received config)
                      (reify ChatModel
                        (^String chat [_ ^String message] "test")))]

        ;; Test with minimal config
        (core/create-model {:provider :mistral :api-key "test-key"})

        (is (= "test-key" (:api-key @config-received)))
        (is (= "mistral-medium-2508" (:model @config-received)))))))

(deftest test-mistral-custom-config
  (testing "Mistral accepts custom configuration"
    (let [config-received (atom nil)]
      (with-redefs [core/build-model
                    (fn [config]
                      (when (= :mistral (:provider config))
                        (reset! config-received config)
                        (reify ChatModel
                          (^String chat [_ ^String message] "test"))))]

        (core/create-model
         {:provider :mistral
          :api-key "test-key"
          :model "mistral-large-latest"
          :temperature 0.9
          :timeout 120000
          :max-tokens 2000
          :max-retries 5
          :log-requests? true
          :log-responses? true})

        (is (= :mistral (:provider @config-received)))
        (is (= "test-key" (:api-key @config-received)))
        (is (= "mistral-large-latest" (:model @config-received)))
        (is (= 0.9 (:temperature @config-received)))
        (is (= 120000 (:timeout @config-received)))
        (is (= 2000 (:max-tokens @config-received)))
        (is (= 5 (:max-retries @config-received)))
        (is (= true (:log-requests? @config-received)))
        (is (= true (:log-responses? @config-received)))))))

(deftest test-mistral-threading
  (testing "Mistral configuration with threading patterns"
    (with-redefs [core/build-model
                  (fn [config]
                    (when (= :mistral (:provider config))
                      (reify ChatModel
                        (^String chat [_ ^String message]
                          (str "Model: " (:model config))))))]

      (let [model (core/create-model
                   (-> {:provider :mistral
                        :api-key "test-key"}
                       (assoc :model "mistral-small-latest")
                       (assoc :temperature 0.8)))]
        (is (instance? ChatModel model))))))

(deftest test-mistral-via-create-model
  (testing "Mistral multimethod dispatch works correctly"
    (with-redefs [core/build-model
                  (fn [config]
                    (when (= :mistral (:provider config))
                      (reify ChatModel
                        (^String chat [_ ^String message]
                          (str "Mistral via create-model: " message)))))]

      (let [model (core/create-model {:provider :mistral :api-key "test-key"})]
        (is (instance? ChatModel model))
        (is (= "Mistral via create-model: test" (core/chat model "test")))))))

(deftest test-all-providers-create-chatmodel
  (testing "All providers create ChatModel instances"
    (with-redefs [core/build-model (fn [config]
                                     (case (:provider config)
                                       :openai (reify ChatModel (^String chat [_ ^String m] "openai"))
                                       :anthropic (reify ChatModel (^String chat [_ ^String m] "anthropic"))
                                       :google-ai-gemini (reify ChatModel (^String chat [_ ^String m] "google-ai-gemini"))
                                       :vertex-ai-gemini (reify ChatModel (^String chat [_ ^String m] "vertex-ai-gemini"))
                                       :ollama (reify ChatModel (^String chat [_ ^String m] "ollama"))
                                       :mistral (reify ChatModel (^String chat [_ ^String m] "mistral"))
                                       nil))]

      (let [openai (core/create-model {:provider :openai :api-key "test"})
            anthropic (core/create-model {:provider :anthropic :api-key "test"})
            google-ai (core/create-model {:provider :google-ai-gemini :api-key "test"})
            vertex-ai (core/create-model {:provider :vertex-ai-gemini :project "test"})
            ollama (core/create-model {:provider :ollama})
            mistral (core/create-model {:provider :mistral})]

        (is (instance? ChatModel openai))
        (is (instance? ChatModel anthropic))
        (is (instance? ChatModel google-ai))
        (is (instance? ChatModel vertex-ai))
        (is (instance? ChatModel ollama))
        (is (instance? ChatModel mistral))

        (is (= "openai" (core/chat openai "test")))
        (is (= "anthropic" (core/chat anthropic "test")))
        (is (= "google-ai-gemini" (core/chat google-ai "test")))
        (is (= "vertex-ai-gemini" (core/chat vertex-ai "test")))
        (is (= "ollama" (core/chat ollama "test")))
        (is (= "mistral" (core/chat mistral "test")))))))

