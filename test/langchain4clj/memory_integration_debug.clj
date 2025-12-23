(ns langchain4clj.memory-integration-debug
  "Debug test to investigate message filtering behavior."
  (:require [clojure.test :refer [deftest testing is]]
            [langchain4clj.memory :as mem]
            [langchain4clj.assistant :as asst])
  (:import [dev.langchain4j.data.message UserMessage SystemMessage AiMessage]
           [dev.langchain4j.model.chat ChatModel]
           [dev.langchain4j.model.chat.response ChatResponse]
           [dev.langchain4j.model.output TokenUsage]))

;; Mock chat model for debugging
(defn create-debug-model
  [responses]
  (let [call-count (atom 0)
        create-response (fn []
                          (let [response-text (nth responses @call-count "Mock response")
                                token-usage (TokenUsage. (int 50) (int 50))]
                            (println "DEBUG: Mock model called, count:" @call-count "response:" response-text)
                            (swap! call-count inc)
                            (-> (dev.langchain4j.model.chat.response.ChatResponse/builder)
                                (.aiMessage (AiMessage. response-text))
                                (.tokenUsage token-usage)
                                (.build))))]
    (reify ChatModel
      (^String chat [_ ^String message]
        (let [response-text (nth responses @call-count "Mock response")]
          (println "DEBUG: String chat called, count:" @call-count)
          (swap! call-count inc)
          response-text))

      (^dev.langchain4j.model.chat.response.ChatResponse chat [_ ^java.util.List messages]
        (println "DEBUG: List chat called, messages count:" (count messages))
        (create-response))

      (^dev.langchain4j.model.chat.response.ChatResponse chat [_ ^dev.langchain4j.model.chat.request.ChatRequest request]
        (println "DEBUG: ChatRequest chat called")
        (create-response)))))

(defn print-message-details
  [msg idx]
  (println (format "  [%d] Type: %s | Text: %s"
                   idx
                   (.getSimpleName (.getClass msg))
                   (cond
                     (instance? UserMessage msg) (.singleText msg)
                     (instance? SystemMessage msg) (.text msg)
                     (instance? AiMessage msg) (.text msg)
                     :else "unknown"))))

(deftest debug-message-filtering
  (testing "Debug: Can filter messages from assistant interactions"
    (println "\n=== DEBUG TEST START ===")
    (let [memory (mem/create-memory {:max-messages 10})
          model (create-debug-model ["AI 1" "AI 2"])
          _ (println "Creating assistant with system-message")
          assistant (asst/create-assistant {:model model
                                            :memory memory
                                            :system-message "System"})]

      (println "\n--- Before first call ---")
      (println "Memory stats:" (mem/stats memory))

      (println "\n--- Calling (assistant \"User 1\") ---")
      (assistant "User 1")

      (println "\n--- After first call ---")
      (println "Memory stats:" (mem/stats memory))
      (let [all-messages (mem/get-messages memory)]
        (println "Total messages:" (count all-messages))
        (doseq [[idx msg] (map-indexed vector all-messages)]
          (print-message-details msg idx)))

      (println "\n--- Calling (assistant \"User 2\") ---")
      (assistant "User 2")

      (println "\n--- After second call ---")
      (println "Memory stats:" (mem/stats memory))
      (let [all-messages (mem/get-messages memory)]
        (println "Total messages:" (count all-messages))
        (doseq [[idx msg] (map-indexed vector all-messages)]
          (print-message-details msg idx)))

      ;; Filter only user messages
      (println "\n--- Filtering UserMessage ---")
      (let [user-messages (mem/get-messages memory {:type UserMessage})]
        (println "User messages count:" (count user-messages))
        (doseq [[idx msg] (map-indexed vector user-messages)]
          (print-message-details msg idx))
        (is (= 2 (count user-messages))))

      ;; Filter only AI messages
      (println "\n--- Filtering AiMessage ---")
      (let [ai-messages (mem/get-messages memory {:type AiMessage})]
        (println "AI messages count:" (count ai-messages))
        (doseq [[idx msg] (map-indexed vector ai-messages)]
          (print-message-details msg idx))
        (println "\nEXPECTED: 2 AI messages")
        (println "ACTUAL:" (count ai-messages) "AI messages")
        (is (= 2 (count ai-messages)) "Should have exactly 2 AI messages"))

      (println "\n=== DEBUG TEST END ===\n"))))
