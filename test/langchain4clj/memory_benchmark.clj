(ns langchain4clj.memory-benchmark
  "Performance benchmarks for memory operations."
  (:require [clojure.test :refer [deftest testing]]
            [langchain4clj.memory :as mem])
  (:import [dev.langchain4j.data.message UserMessage]
           [dev.langchain4j.model.output TokenUsage]))

(defn- mock-token-usage [tokens]
  (TokenUsage. (int (quot tokens 2)) (int (quot tokens 2))))

(defn benchmark [label f]
  (let [start (System/nanoTime)
        result (f)
        end (System/nanoTime)
        duration-ms (/ (- end start) 1000000.0)]
    (println (format "%s: %.2f ms" label duration-ms))
    result))

(deftest ^:benchmark benchmark-basic-operations
  (testing "Benchmark: add-message performance"
    (let [memory (mem/create-memory {:max-messages 1000})]
      (benchmark "Add 1000 messages"
        (fn []
          (dotimes [i 1000]
            (mem/add-message! memory (UserMessage. (str "Message " i))))))))

  (testing "Benchmark: get-messages performance"
    (let [memory (mem/create-memory {:max-messages 1000})]
      (dotimes [i 1000]
        (mem/add-message! memory (UserMessage. (str "Message " i))))
      (benchmark "Get all 1000 messages"
        (fn [] (mem/get-messages memory)))))

  (testing "Benchmark: stats performance"
    (let [memory (mem/create-memory {:max-messages 1000})]
      (dotimes [_ 1000]
        (mem/add-message! memory (UserMessage. "Test")))
      (benchmark "Get stats (1000 messages)"
        (fn []
          (dotimes [_ 1000]
            (mem/stats memory)))))))

(deftest ^:benchmark benchmark-token-tracking
  (testing "Benchmark: token tracking overhead"
    (let [memory (mem/create-memory {:max-messages 1000})]
      (benchmark "Add 1000 messages with token tracking"
        (fn []
          (dotimes [_ 1000]
            (mem/add-message! memory
                              (UserMessage. "Test")
                              {:token-usage (mock-token-usage 100)})))))))

(deftest ^:benchmark benchmark-filtering
  (testing "Benchmark: message filtering"
    (let [memory (mem/create-memory {:max-messages 1000})]
      (dotimes [i 1000]
        (mem/add-message! memory (UserMessage. (str "Message " i))))

      (benchmark "Filter by type (1000 messages)"
        (fn [] (mem/get-messages memory {:type UserMessage})))

      (benchmark "Filter with limit (1000 messages)"
        (fn [] (mem/get-messages memory {:limit 100})))

      (benchmark "Filter with from-index (1000 messages)"
        (fn [] (mem/get-messages memory {:from-index 500}))))))

(deftest ^:benchmark benchmark-auto-reset
  (testing "Benchmark: auto-reset trigger"
    (let [memory (-> (mem/create-memory {:max-messages 100})
                     (mem/with-auto-reset {:reset-threshold 0.85}))]
      (benchmark "Trigger auto-reset (100 messages, 85% threshold)"
        (fn []
          (dotimes [_ 100]
            (mem/add-message! memory (UserMessage. "Test"))))))))

(deftest ^:benchmark benchmark-composition
  (testing "Benchmark: fully composed memory"
    (let [memory (-> (mem/create-memory {:max-messages 1000})
                     (mem/with-auto-reset {:reset-threshold 0.9}))]
      (benchmark "Add to composed memory (1000 messages)"
        (fn []
          (dotimes [_ 1000]
            (mem/add-message! memory (UserMessage. "Test"))))))))

(comment
  ;; Run benchmarks manually
  (benchmark-basic-operations)
  (benchmark-token-tracking)
  (benchmark-filtering)
  (benchmark-auto-reset)
  (benchmark-composition)

  ;; Expected performance characteristics:
  ;; - add-message: < 1ms per message
  ;; - get-messages: < 10ms for 1000 messages
  ;; - stats: < 0.01ms per call
  ;; - filtering: < 20ms for 1000 messages
  ;; - auto-reset: < 100ms for reset + restore
  )
