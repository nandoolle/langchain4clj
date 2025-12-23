(ns langchain4clj.constants
  "Default configuration constants.")

(def default-temperature 0.7)

(def default-timeout-ms 60000)

(def default-max-attempts 3)

(def default-failure-threshold 5)

(def default-success-threshold 3)

(def default-circuit-breaker-timeout-ms 60000)

(def default-retry-delay-ms 1000)

(def default-max-retries 2)

;; Memory constants
(def default-max-messages 100)

(def default-reset-threshold 0.85)

(def default-max-tokens 16000)
