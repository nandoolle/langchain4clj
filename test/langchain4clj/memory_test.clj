(ns langchain4clj.memory-test
  (:require [clojure.test :refer [deftest testing is]]
            [langchain4clj.memory :as mem]
            [langchain4clj.memory.core :as core])
  (:import [dev.langchain4j.data.message UserMessage SystemMessage AiMessage]
           [dev.langchain4j.model.output TokenUsage]))

;; Helper to create mock TokenUsage
(defn- mock-token-usage [total-tokens]
  (TokenUsage. (int (quot total-tokens 2)) (int (quot total-tokens 2))))

(deftest test-basic-memory-creation
  (testing "Create memory with default settings"
    (let [memory (mem/create-memory)]
      (is (satisfies? core/ChatMemory memory))
      (is (= {:message-count 0 :token-count 0} (mem/stats memory)))))

  (testing "Create memory with custom max-messages"
    (let [memory (mem/create-memory {:max-messages 50})]
      (is (satisfies? core/ChatMemory memory)))))

(deftest test-add-message-without-metadata
  (testing "Add messages without token metadata"
    (let [memory (mem/create-memory {:max-messages 3})]
      (mem/add-message! memory (UserMessage. "Hello"))
      (mem/add-message! memory (AiMessage. "Hi there"))

      (is (= 2 (:message-count (mem/stats memory))))
      (is (= 0 (:token-count (mem/stats memory)))))))

(deftest test-add-message-with-token-metadata
  (testing "Add messages with TokenUsage metadata"
    (let [memory (mem/create-memory)]
      (mem/add-message! memory (UserMessage. "Hello") nil)
      (mem/add-message! memory (AiMessage. "Hi") {:token-usage (mock-token-usage 100)})
      (mem/add-message! memory (AiMessage. "How are you?") {:token-usage (mock-token-usage 50)})

      (let [stats (mem/stats memory)]
        (is (= 3 (:message-count stats)))
        (is (= 150 (:token-count stats)))))))

(deftest test-sliding-window
  (testing "Memory maintains sliding window"
    (let [memory (mem/create-memory {:max-messages 3})]
      (mem/add-message! memory (UserMessage. "Message 1"))
      (mem/add-message! memory (UserMessage. "Message 2"))
      (mem/add-message! memory (UserMessage. "Message 3"))
      (mem/add-message! memory (UserMessage. "Message 4"))

      (is (= 3 (:message-count (mem/stats memory))))
      (let [messages (mem/get-messages memory)]
        (is (= "Message 2" (.singleText (first messages))))
        (is (= "Message 4" (.singleText (last messages))))))))

(deftest test-clear-memory
  (testing "Clear removes all messages and resets token count"
    (let [memory (mem/create-memory)]
      (mem/add-message! memory (UserMessage. "Test") {:token-usage (mock-token-usage 100)})
      (mem/clear! memory)

      (is (= {:message-count 0 :token-count 0} (mem/stats memory)))
      (is (empty? (mem/get-messages memory))))))

(deftest test-message-filtering
  (testing "Filter messages by type"
    (let [memory (mem/create-memory)]
      (mem/add-message! memory (UserMessage. "User 1"))
      (mem/add-message! memory (SystemMessage. "System"))
      (mem/add-message! memory (UserMessage. "User 2"))
      (mem/add-message! memory (AiMessage. "AI"))

      (is (= 2 (count (mem/get-messages memory {:type UserMessage}))))
      (is (= 1 (count (mem/get-messages memory {:type SystemMessage}))))
      (is (= 1 (count (mem/get-messages memory {:type AiMessage}))))))

  (testing "Filter messages by limit"
    (let [memory (mem/create-memory)]
      (dotimes [i 5]
        (mem/add-message! memory (UserMessage. (str "Message " i))))

      (is (= 3 (count (mem/get-messages memory {:limit 3}))))))

  (testing "Filter messages by from-index"
    (let [memory (mem/create-memory)]
      (dotimes [i 5]
        (mem/add-message! memory (UserMessage. (str "Message " i))))

      (let [messages (mem/get-messages memory {:from-index 2})]
        (is (= 3 (count messages)))))))

(deftest test-auto-reset-by-message-count
  (testing "Auto-reset at message threshold"
    (let [ctx-msg (SystemMessage. "Context")
          memory (-> (mem/create-memory {:max-messages 10})
                     (mem/with-auto-reset {:max-messages 10
                                           :reset-threshold 0.85
                                           :context [ctx-msg]}))]
      ;; Add 9 messages (90% of 10)
      (dotimes [i 9]
        (mem/add-message! memory (UserMessage. (str "Message " i))))

      ;; Should have reset and restored context
      (let [stats (mem/stats memory)]
        (is (< (:message-count stats) 9))
        (is (= "Context" (.text (first (mem/get-messages memory)))))))))

(deftest test-auto-reset-by-token-count
  (testing "Auto-reset at token threshold"
    (let [memory (-> (mem/create-memory)
                     (mem/with-auto-reset {:max-tokens 1000
                                           :reset-threshold 0.85}))]
      ;; Add messages totaling 900 tokens (90% of 1000)
      (dotimes [i 9]
        (mem/add-message! memory (UserMessage. "Test") {:token-usage (mock-token-usage 100)}))

      ;; Should have reset
      (let [stats (mem/stats memory)]
        (is (< (:message-count stats) 9))
        (is (< (:token-count stats) 900))))))

(deftest test-stateless-mode
  (testing "Stateless mode clears before each session"
    (let [ctx-msg (SystemMessage. "Context")
          memory (-> (mem/create-memory)
                     (mem/with-stateless-mode {:context [ctx-msg]}))]

      ;; First session
      (mem/add-message! memory (UserMessage. "Question 1"))
      (is (= 2 (:message-count (mem/stats memory))))  ; context + question

      ;; Clear to start new session
      (mem/clear! memory)

      ;; Next add should restore context
      (mem/add-message! memory (UserMessage. "Question 2"))
      (is (= 2 (:message-count (mem/stats memory))))  ; context + question
      (is (= "Context" (.text (first (mem/get-messages memory))))))))

(deftest test-composition
  (testing "All strategies compose"
    (let [memory (-> (mem/create-memory {:max-messages 100})
                     (mem/with-auto-reset {:max-messages 100
                                           :reset-threshold 0.85})
                     (mem/with-stateless-mode {:context []}))]
      (is (satisfies? core/ChatMemory memory))
      (mem/add-message! memory (UserMessage. "Test"))
      (is (= 1 (:message-count (mem/stats memory)))))))

(deftest test-threading-first-config
  (testing "Config helpers support threading"
    (let [config (-> {}
                     (mem/with-max-messages 50)
                     (mem/with-context [(SystemMessage. "Ctx")]))]
      (is (= 50 (:max-messages config)))
      (is (= 1 (count (:context config))))))

  (testing "Create memory from threaded config"
    (let [memory (-> {}
                     (mem/with-max-messages 50)
                     mem/create-memory)]
      (is (satisfies? core/ChatMemory memory)))))

(deftest test-backward-compatibility
  (testing "Works with assistant.clj style calls"
    (let [memory (mem/create-memory {:max-messages 10})]
      (mem/add-message! memory (UserMessage. "Test"))
      (is (= 1 (count (mem/get-messages memory))))
      (mem/clear! memory)
      (is (= 0 (count (mem/get-messages memory)))))))

(deftest test-defmemory-macro
  (testing "defmemory creates simple memory"
    (mem/defmemory simple-test-memory
      :max-messages 50)
    (is (satisfies? core/ChatMemory simple-test-memory))
    (mem/add-message! simple-test-memory (UserMessage. "Test"))
    (is (= 1 (:message-count (mem/stats simple-test-memory)))))

  (testing "defmemory with auto-reset"
    (mem/defmemory auto-reset-test-memory
      :max-messages 10
      :auto-reset {:threshold 0.85})
    (is (satisfies? core/ChatMemory auto-reset-test-memory)))

  (testing "defmemory with stateless"
    (mem/defmemory stateless-test-memory
      :max-messages 100
      :stateless {:context [(SystemMessage. "Ctx")]})
    (is (satisfies? core/ChatMemory stateless-test-memory)))

  (testing "defmemory with both strategies"
    (mem/defmemory full-test-memory
      :max-messages 100
      :auto-reset {:threshold 0.9}
      :stateless {:context []})
    (is (satisfies? core/ChatMemory full-test-memory))))

(deftest test-edge-cases
  (testing "Empty memory operations"
    (let [memory (mem/create-memory)]
      (is (= 0 (:message-count (mem/stats memory))))
      (is (empty? (mem/get-messages memory)))
      (mem/clear! memory)
      (is (= 0 (:message-count (mem/stats memory))))))

  (testing "Add nil metadata"
    (let [memory (mem/create-memory)]
      (mem/add-message! memory (UserMessage. "Test") nil)
      (is (= 1 (:message-count (mem/stats memory))))
      (is (= 0 (:token-count (mem/stats memory))))))

  (testing "Add message with empty token-usage metadata"
    (let [memory (mem/create-memory)]
      (mem/add-message! memory (UserMessage. "Test") {})
      (is (= 1 (:message-count (mem/stats memory))))
      (is (= 0 (:token-count (mem/stats memory))))))

  (testing "Filter with no matches"
    (let [memory (mem/create-memory)]
      (mem/add-message! memory (UserMessage. "Test"))
      (is (empty? (mem/get-messages memory {:type SystemMessage})))))

  (testing "Filter with limit larger than size"
    (let [memory (mem/create-memory)]
      (mem/add-message! memory (UserMessage. "Test"))
      (is (= 1 (count (mem/get-messages memory {:limit 1000}))))))

  (testing "Filter with from-index beyond size"
    (let [memory (mem/create-memory)]
      (mem/add-message! memory (UserMessage. "Test"))
      (is (empty? (mem/get-messages memory {:from-index 100})))))

  (testing "Very small max-messages (edge: 1)"
    (let [memory (mem/create-memory {:max-messages 1})]
      (mem/add-message! memory (UserMessage. "First"))
      (mem/add-message! memory (UserMessage. "Second"))
      (is (= 1 (:message-count (mem/stats memory))))
      (is (= "Second" (.singleText (first (mem/get-messages memory)))))))

  (testing "Auto-reset with no context"
    (let [memory (-> (mem/create-memory {:max-messages 5})
                     (mem/with-auto-reset {:max-messages 5
                                           :reset-threshold 0.8}))]
      (dotimes [i 5]
        (mem/add-message! memory (UserMessage. (str "Msg " i))))
      (is (< (:message-count (mem/stats memory)) 5))))

  (testing "Auto-reset with empty context"
    (let [memory (-> (mem/create-memory {:max-messages 5})
                     (mem/with-auto-reset {:max-messages 5
                                           :reset-threshold 0.8
                                           :context []}))]
      (dotimes [i 5]
        (mem/add-message! memory (UserMessage. "Test")))
      (is (>= (:message-count (mem/stats memory)) 0))))

  (testing "Stateless with empty context"
    (let [memory (-> (mem/create-memory)
                     (mem/with-stateless-mode {:context []}))]
      (mem/add-message! memory (UserMessage. "Test"))
      (is (= 1 (:message-count (mem/stats memory))))))

  (testing "Multiple clears in sequence"
    (let [memory (mem/create-memory)]
      (mem/add-message! memory (UserMessage. "Test"))
      (mem/clear! memory)
      (mem/clear! memory)
      (mem/clear! memory)
      (is (= 0 (:message-count (mem/stats memory))))))

  (testing "Stats after clear"
    (let [memory (mem/create-memory)]
      (mem/add-message! memory (UserMessage. "Test") {:token-usage (mock-token-usage 100)})
      (mem/clear! memory)
      (let [stats (mem/stats memory)]
        (is (= 0 (:message-count stats)))
        (is (= 0 (:token-count stats)))))))
