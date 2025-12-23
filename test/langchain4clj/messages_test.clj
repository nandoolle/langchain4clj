(ns langchain4clj.messages-test
  "Tests for message serialization utilities."
  (:require [clojure.test :refer [deftest is testing]]
            [langchain4clj.messages :as msg])
  (:import [dev.langchain4j.data.message
            UserMessage SystemMessage AiMessage ToolExecutionResultMessage]
           [dev.langchain4j.agent.tool ToolExecutionRequest]))

;; =============================================================================
;; Java -> EDN Conversion Tests
;; =============================================================================

(deftest test-user-message->edn
  (testing "Simple user message"
    (let [msg (UserMessage/from "Hello")
          edn (msg/message->edn msg)]
      (is (= :user (:type edn)))
      (is (= 1 (count (:contents edn))))
      (is (= :text (-> edn :contents first :type)))
      (is (= "Hello" (-> edn :contents first :text))))))

(deftest test-system-message->edn
  (testing "System message"
    (let [msg (SystemMessage/from "You are helpful")
          edn (msg/message->edn msg)]
      (is (= :system (:type edn)))
      (is (= "You are helpful" (:text edn))))))

(deftest test-ai-message->edn
  (testing "Simple AI message"
    (let [msg (AiMessage/from "Hello there!")
          edn (msg/message->edn msg)]
      (is (= :ai (:type edn)))
      (is (= "Hello there!" (:text edn)))
      (is (nil? (:tool-execution-requests edn)))))

  (testing "AI message with tool calls"
    (let [tool-req (-> (ToolExecutionRequest/builder)
                       (.id "call-123")
                       (.name "calculator")
                       (.arguments "{\"x\":1}")
                       (.build))
          msg (AiMessage. "Calculating" [tool-req])
          edn (msg/message->edn msg)]
      (is (= :ai (:type edn)))
      (is (= "Calculating" (:text edn)))
      (is (= 1 (count (:tool-execution-requests edn))))
      (is (= "call-123" (-> edn :tool-execution-requests first :id)))
      (is (= "calculator" (-> edn :tool-execution-requests first :name)))
      (is (= "{\"x\":1}" (-> edn :tool-execution-requests first :arguments))))))

(deftest test-tool-result-message->edn
  (testing "Tool execution result"
    (let [msg (ToolExecutionResultMessage. "call-123" "calculator" "42")
          edn (msg/message->edn msg)]
      (is (= :tool-result (:type edn)))
      (is (= "call-123" (:id edn)))
      (is (= "calculator" (:tool-name edn)))
      (is (= "42" (:text edn))))))

(deftest test-messages->edn
  (testing "Multiple messages"
    (let [messages [(UserMessage/from "Hi")
                    (AiMessage/from "Hello!")
                    (UserMessage/from "How are you?")]
          edn (msg/messages->edn messages)]
      (is (= 3 (count edn)))
      (is (= [:user :ai :user] (mapv :type edn))))))

;; =============================================================================
;; EDN -> Java Conversion Tests
;; =============================================================================

(deftest test-edn->user-message
  (testing "Simple form with :text"
    (let [edn {:type :user :text "Hello"}
          java-msg (msg/edn->message edn)]
      (is (instance? UserMessage java-msg))
      (is (= "Hello" (-> java-msg .contents first .text)))))

  (testing "Full form with :contents"
    (let [edn {:type :user :contents [{:type :text :text "Hello"}]}
          java-msg (msg/edn->message edn)]
      (is (instance? UserMessage java-msg))
      (is (= "Hello" (-> java-msg .contents first .text)))))

  (testing "String type (case insensitive)"
    (let [edn {:type "USER" :text "Hello"}
          java-msg (msg/edn->message edn)]
      (is (instance? UserMessage java-msg)))))

(deftest test-edn->system-message
  (testing "System message"
    (let [edn {:type :system :text "Be helpful"}
          java-msg (msg/edn->message edn)]
      (is (instance? SystemMessage java-msg))
      (is (= "Be helpful" (.text java-msg))))))

(deftest test-edn->ai-message
  (testing "Simple AI message"
    (let [edn {:type :ai :text "Hello!"}
          java-msg (msg/edn->message edn)]
      (is (instance? AiMessage java-msg))
      (is (= "Hello!" (.text java-msg)))))

  (testing "AI message with tool calls"
    (let [edn {:type :ai
               :text "Let me calculate"
               :tool-execution-requests [{:id "call-1"
                                          :name "calc"
                                          :arguments "{\"x\":1}"}]}
          java-msg (msg/edn->message edn)]
      (is (instance? AiMessage java-msg))
      (is (= "Let me calculate" (.text java-msg)))
      (is (= 1 (count (.toolExecutionRequests java-msg))))
      (is (= "call-1" (-> java-msg .toolExecutionRequests first .id))))))

(deftest test-edn->tool-result-message
  (testing "Tool result"
    (let [edn {:type :tool-result :id "call-1" :tool-name "calc" :text "42"}
          java-msg (msg/edn->message edn)]
      (is (instance? ToolExecutionResultMessage java-msg))
      (is (= "call-1" (.id java-msg)))
      (is (= "calc" (.toolName java-msg)))
      (is (= "42" (.text java-msg))))))

(deftest test-edn->messages
  (testing "Multiple messages"
    (let [edn [{:type :user :text "Hi"}
               {:type :ai :text "Hello!"}]
          java-list (msg/edn->messages edn)]
      (is (instance? java.util.List java-list))
      (is (= 2 (count java-list)))
      (is (instance? UserMessage (first java-list)))
      (is (instance? AiMessage (second java-list))))))

(deftest test-edn->message-unknown-type
  (testing "Unknown type throws"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Unknown message type"
                          (msg/edn->message {:type :unknown-type :text "x"})))))

;; =============================================================================
;; Roundtrip Tests
;; =============================================================================

(deftest test-user-message-roundtrip
  (testing "UserMessage roundtrip"
    (let [original (UserMessage/from "Hello world")
          edn (msg/message->edn original)
          reconstructed (msg/edn->message edn)]
      (is (instance? UserMessage reconstructed))
      (is (= (-> original .contents first .text)
             (-> reconstructed .contents first .text))))))

(deftest test-system-message-roundtrip
  (testing "SystemMessage roundtrip"
    (let [original (SystemMessage/from "You are an assistant")
          edn (msg/message->edn original)
          reconstructed (msg/edn->message edn)]
      (is (instance? SystemMessage reconstructed))
      (is (= (.text original) (.text reconstructed))))))

(deftest test-ai-message-roundtrip
  (testing "Simple AiMessage roundtrip"
    (let [original (AiMessage/from "I can help!")
          edn (msg/message->edn original)
          reconstructed (msg/edn->message edn)]
      (is (instance? AiMessage reconstructed))
      (is (= (.text original) (.text reconstructed)))))

  (testing "AiMessage with tools roundtrip"
    (let [tool-req (-> (ToolExecutionRequest/builder)
                       (.id "id-1")
                       (.name "tool-1")
                       (.arguments "{\"a\":1}")
                       (.build))
          original (AiMessage. "Using tool" [tool-req])
          edn (msg/message->edn original)
          reconstructed (msg/edn->message edn)]
      (is (instance? AiMessage reconstructed))
      (is (= (.text original) (.text reconstructed)))
      (is (= (count (.toolExecutionRequests original))
             (count (.toolExecutionRequests reconstructed))))
      (is (= (-> original .toolExecutionRequests first .id)
             (-> reconstructed .toolExecutionRequests first .id))))))

(deftest test-tool-result-roundtrip
  (testing "ToolExecutionResultMessage roundtrip"
    (let [original (ToolExecutionResultMessage. "call-1" "my-tool" "result-data")
          edn (msg/message->edn original)
          reconstructed (msg/edn->message edn)]
      (is (instance? ToolExecutionResultMessage reconstructed))
      (is (= (.id original) (.id reconstructed)))
      (is (= (.toolName original) (.toolName reconstructed)))
      (is (= (.text original) (.text reconstructed))))))

(deftest test-messages-roundtrip
  (testing "Full conversation roundtrip"
    (let [original [(SystemMessage/from "You are helpful")
                    (UserMessage/from "Hi")
                    (AiMessage/from "Hello!")
                    (UserMessage/from "Calculate 1+1")
                    (AiMessage. "Sure" [(-> (ToolExecutionRequest/builder)
                                            (.id "c1")
                                            (.name "calc")
                                            (.arguments "{}")
                                            (.build))])
                    (ToolExecutionResultMessage. "c1" "calc" "2")
                    (AiMessage/from "The result is 2")]
          edn (msg/messages->edn original)
          reconstructed (msg/edn->messages edn)]
      (is (= (count original) (count reconstructed)))
      (is (= (mapv class original) (mapv class reconstructed))))))

;; =============================================================================
;; JSON Conversion Tests
;; =============================================================================

(deftest test-message->json
  (testing "Single message to JSON"
    (let [msg (UserMessage/from "Hello")
          json (msg/message->json msg)]
      (is (string? json))
      (is (.contains json "USER"))
      (is (.contains json "Hello")))))

(deftest test-messages->json
  (testing "Multiple messages to JSON"
    (let [messages [(UserMessage/from "Hi") (AiMessage/from "Hello!")]
          json (msg/messages->json messages)]
      (is (string? json))
      (is (.startsWith json "["))
      (is (.endsWith json "]")))))

(deftest test-json->message
  (testing "JSON to single message"
    (let [json "{\"type\":\"USER\",\"contents\":[{\"type\":\"TEXT\",\"text\":\"Hello\"}]}"
          msg (msg/json->message json)]
      (is (instance? UserMessage msg))
      (is (= "Hello" (-> msg .contents first .text))))))

(deftest test-json->messages
  (testing "JSON to multiple messages"
    (let [json "[{\"type\":\"USER\",\"contents\":[{\"type\":\"TEXT\",\"text\":\"Hi\"}]},{\"type\":\"AI\",\"text\":\"Hello!\"}]"
          messages (msg/json->messages json)]
      (is (= 2 (count messages)))
      (is (instance? UserMessage (first messages)))
      (is (instance? AiMessage (second messages))))))

(deftest test-json-roundtrip
  (testing "JSON roundtrip"
    (let [original [(UserMessage/from "Question")
                    (AiMessage/from "Answer")]
          json (msg/messages->json original)
          reconstructed (msg/json->messages json)]
      (is (= (count original) (count reconstructed)))
      (is (= "Question" (-> reconstructed first .contents first .text)))
      (is (= "Answer" (-> reconstructed second .text))))))

;; =============================================================================
;; Helper Function Tests
;; =============================================================================

(deftest test-parse-tool-arguments
  (testing "Parses JSON string arguments"
    (let [edn {:type :ai
               :text "Calling tool"
               :tool-execution-requests [{:id "c1"
                                          :name "calc"
                                          :arguments "{\"x\":1,\"y\":2}"}]}
          parsed (msg/parse-tool-arguments edn)]
      (is (= {:x 1 :y 2}
             (-> parsed :tool-execution-requests first :arguments)))))

  (testing "Handles nil arguments"
    (let [edn {:type :ai
               :text "Calling tool"
               :tool-execution-requests [{:id "c1"
                                          :name "calc"
                                          :arguments nil}]}
          parsed (msg/parse-tool-arguments edn)]
      (is (nil? (-> parsed :tool-execution-requests first :arguments)))))

  (testing "No-op for messages without tool requests"
    (let [edn {:type :ai :text "Just text"}
          parsed (msg/parse-tool-arguments edn)]
      (is (= edn parsed))))

  (testing "Handles complex nested JSON"
    (let [edn {:type :ai
               :tool-execution-requests [{:id "c1"
                                          :name "tool"
                                          :arguments "{\"nested\":{\"a\":1},\"list\":[1,2,3]}"}]}
          parsed (msg/parse-tool-arguments edn)]
      (is (= {:nested {:a 1} :list [1 2 3]}
             (-> parsed :tool-execution-requests first :arguments))))))
