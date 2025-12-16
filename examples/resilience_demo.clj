(ns examples.resilience-demo
  "Demonstration of Provider Failover and Circuit Breaker features.
  
  This example shows:
  1. Basic failover (Phase 1)
  2. Circuit breaker protection (Phase 2)
  3. Real-world production scenarios
  4. Monitoring and troubleshooting"
  (:require [langchain4clj.core :as llm]
            [langchain4clj.resilience :as resilience]))

;; =============================================================================
;; Example 1: Basic Failover (Phase 1)
;; =============================================================================

(defn example-1-basic-failover []
  (println "\n=== Example 1: Basic Failover ===\n")

  ;; Create model with fallback chain
  (def basic-model
    (resilience/create-resilient-model
     {:primary (llm/create-model {:provider :openai
                                  :api-key (System/getenv "OPENAI_API_KEY")})
      :fallbacks [(llm/create-model {:provider :anthropic
                                     :api-key (System/getenv "ANTHROPIC_API_KEY")})
                  (llm/create-model {:provider :ollama})]
      :max-retries 2
      :retry-delay-ms 1000}))

  (println "Chat request: 'Hello, how are you?'")
  (println "Response:" (llm/chat basic-model "Hello, how are you?"))
  (println "\n✅ Request successful - used available provider"))

;; =============================================================================
;; Example 2: Circuit Breaker Protection (Phase 2)
;; =============================================================================

(defn example-2-circuit-breaker []
  (println "\n=== Example 2: Circuit Breaker Protection ===\n")

  (def production-model
    (resilience/create-resilient-model
     {:primary (llm/create-model {:provider :openai
                                  :api-key (System/getenv "OPENAI_API_KEY")})
      :fallbacks [(llm/create-model {:provider :anthropic
                                     :api-key (System/getenv "ANTHROPIC_API_KEY")})
                  (llm/create-model {:provider :ollama})]
      :max-retries 2
      :retry-delay-ms 1000
      :circuit-breaker? true
      :failure-threshold 5
      :success-threshold 2
      :timeout-ms 60000}))

  (println "Making 3 successful requests...")
  (dotimes [i 3]
    (println (format "Request %d: %s" (inc i)
                     (llm/chat production-model "Say hello"))))

  (println "\n✅ All requests successful with circuit breaker protection"))

;; =============================================================================
;; Example 3: Simulating Provider Failures
;; =============================================================================

(defn example-3-simulate-failures []
  (println "\n=== Example 3: Simulating Provider Failures ===\n")

  ;; Create model with invalid primary (will fail)
  (def failing-model
    (resilience/create-resilient-model
     {:primary (llm/create-model {:provider :openai
                                  :api-key "invalid-key"})
      :fallbacks [(llm/create-model {:provider :ollama})]
      :max-retries 1
      :retry-delay-ms 500
      :circuit-breaker? true
      :failure-threshold 2
      :timeout-ms 10000}))

  (println "Making requests with invalid primary API key...")
  (println "Expected: Primary fails → Circuit opens → Uses Ollama\n")

  (dotimes [i 3]
    (try
      (println (format "Request %d: %s" (inc i)
                       (llm/chat failing-model "Count to 3")))
      (catch Exception e
        (println (format "Request %d failed: %s" (inc i) (.getMessage e))))))

  (println "\n✅ Demonstrated automatic failover to backup provider"))

;; =============================================================================
;; Example 4: Cost Optimization Strategy
;; =============================================================================

(defn example-4-cost-optimization []
  (println "\n=== Example 4: Cost Optimization Strategy ===\n")

  (def cost-optimized-model
    (resilience/create-resilient-model
     {:primary (llm/create-model {:provider :openai
                                  :api-key (System/getenv "OPENAI_API_KEY")
                                  :model "gpt-4o-mini"}) ;; Cheapest
      :fallbacks [(llm/create-model {:provider :anthropic
                                     :api-key (System/getenv "ANTHROPIC_API_KEY")
                                     :model "claude-3-5-sonnet-20241022"}) ;; Expensive
                  (llm/create-model {:provider :ollama})] ;; Free
      :circuit-breaker? true}))

  (println "Cost optimization strategy:")
  (println "1. Primary: gpt-4o-mini ($0.15/1M tokens)")
  (println "2. Fallback 1: claude-3-5-sonnet ($3/1M tokens)")
  (println "3. Fallback 2: Ollama (Free, local)\n")

  (println "Making cost-optimized request...")
  (println "Response:" (llm/chat cost-optimized-model "What is 2+2?"))

  (println "\n✅ Used cheapest available provider"))

;; =============================================================================
;; Example 5: Production Chatbot with Full Features
;; =============================================================================

(defn example-5-production-chatbot []
  (println "\n=== Example 5: Production Chatbot ===\n")

  (def chatbot-model
    (resilience/create-resilient-model
     {:primary (llm/create-model {:provider :openai
                                  :api-key (System/getenv "OPENAI_API_KEY")
                                  :model "gpt-4o-mini"})
      :fallbacks [(llm/create-model {:provider :anthropic
                                     :api-key (System/getenv "ANTHROPIC_API_KEY")})
                  (llm/create-model {:provider :ollama})]
      :max-retries 2
      :retry-delay-ms 1000
      :circuit-breaker? true
      :failure-threshold 5
      :success-threshold 2
      :timeout-ms 60000}))

  (defn handle-customer-query [query]
    (try
      (llm/chat chatbot-model query
                {:system-message "You are a helpful customer service assistant. Be concise."
                 :temperature 0.7
                 :max-tokens 200})
      (catch Exception e
        (println "Error:" (.getMessage e))
        "Sorry, I'm temporarily unavailable. Please try again in a moment.")))

  (println "Simulating customer queries...")
  (let [queries ["What are your business hours?"
                 "How can I track my order?"
                 "What is your return policy?"]]
    (doseq [query queries]
      (println (format "\nQ: %s" query))
      (println (format "A: %s" (handle-customer-query query)))))

  (println "\n✅ Production chatbot with full resilience"))

;; =============================================================================
;; Example 6: Monitoring Circuit Breaker State
;; =============================================================================

(defn example-6-monitoring []
  (println "\n=== Example 6: Monitoring Circuit Breaker ===\n")

  (println "Watch for these log messages:")
  (println "- [CircuitBreaker] Closed → Open (threshold reached)")
  (println "- [CircuitBreaker] Open → Half-Open (timeout elapsed)")
  (println "- [CircuitBreaker] Half-Open → Closed")
  (println "- [CircuitBreaker] Half-Open → Open (test failed)\n")

  (println "These logs appear in stdout when circuit breaker changes state.")
  (println "You should monitor these in your logging system (ELK, Splunk, etc.)")

  (println "\n✅ Circuit breaker logs help track provider health"))

;; =============================================================================
;; Example 7: Testing Circuit Breaker Recovery
;; =============================================================================

(defn example-7-recovery-testing []
  (println "\n=== Example 7: Testing Circuit Breaker Recovery ===\n")

  (def test-model
    (resilience/create-resilient-model
     {:primary (llm/create-model {:provider :openai
                                  :api-key "invalid"}) ;; Will fail
      :fallbacks [(llm/create-model {:provider :ollama})]
      :circuit-breaker? true
      :failure-threshold 2
      :success-threshold 1
      :timeout-ms 5000})) ;; Short timeout for demo

  (println "Step 1: Trigger circuit breaker to open (2 failures)...")
  (dotimes [i 2]
    (try
      (llm/chat test-model "test")
      (catch Exception e
        (println (format "  Failure %d: Using fallback" (inc i))))))

  (println "\nStep 2: Circuit is now OPEN - primary is skipped")
  (println "  Making request (should use Ollama immediately)...")
  (println "  Response:" (llm/chat test-model "Say 'fallback'"))

  (println "\nStep 3: Wait for timeout (5 seconds)...")
  (Thread/sleep 6000)

  (println "\nStep 4: Circuit transitions to HALF-OPEN")
  (println "  Next request will test primary again...")
  (println "  (Still fails, so back to OPEN and uses Ollama)")
  (println "  Response:" (llm/chat test-model "Say 'testing'"))

  (println "\n✅ Demonstrated circuit breaker state transitions"))

;; =============================================================================
;; Example 8: Integration with Other Features
;; =============================================================================

(defn example-8-integration []
  (println "\n=== Example 8: Integration with Other Features ===\n")

  (def integrated-model
    (resilience/create-resilient-model
     {:primary (llm/create-model {:provider :openai
                                  :api-key (System/getenv "OPENAI_API_KEY")})
      :fallbacks [(llm/create-model {:provider :ollama})]
      :circuit-breaker? true}))

  (println "Resilience works with:")
  (println "1. JSON Mode")
  (println "2. Tools/Function Calling")
  (println "3. Streaming")
  (println "4. All chat options\n")

  ;; Example with JSON mode
  (try
    (require '[dev.langchain4j.model.chat.request ResponseFormat])
    (println "Making request with JSON mode...")
    (let [response (llm/chat integrated-model "Return {\"message\": \"hello\"}"
                             {:response-format ResponseFormat/JSON})]
      (println "JSON Response:" (-> response .aiMessage .text)))
    (catch Exception e
      (println "JSON mode example skipped (optional dependency)")))

  (println "\nResilience is compatible with all features"))

;; =============================================================================
;; Main Demo Runner
;; =============================================================================

(defn -main []
  (println "╔════════════════════════════════════════════════════════╗")
  (println "║   LangChain4Clj - Resilience & Circuit Breaker Demo   ║")
  (println "╚════════════════════════════════════════════════════════╝")

  (println "\nThis demo requires:")
  (println "- OPENAI_API_KEY environment variable (optional)")
  (println "- ANTHROPIC_API_KEY environment variable (optional)")
  (println "- Ollama running locally (recommended)\n")

  (println "Running examples...\n")
  (println "=" (apply str (repeat 60 "=")))

  ;; Run examples
  (try
    (when (System/getenv "OPENAI_API_KEY")
      (example-1-basic-failover)
      (example-2-circuit-breaker)
      (example-4-cost-optimization)
      (example-5-production-chatbot))

    (example-3-simulate-failures)
    (example-6-monitoring)
    (example-7-recovery-testing)
    (example-8-integration)

    (catch Exception e
      (println "\nDemo error:" (.getMessage e))
      (println "Some examples may require API keys or Ollama")))

  (println "\n" (apply str (repeat 60 "=")))
  (println "\nDemo complete!")
  (println "\nFor full documentation, see: docs/RESILIENCE.md"))

(comment
  ;; Run the demo
  (-main)

  ;; Run individual examples
  (example-1-basic-failover)
  (example-2-circuit-breaker)
  (example-3-simulate-failures)
  (example-4-cost-optimization)
  (example-5-production-chatbot)
  (example-6-monitoring)
  (example-7-recovery-testing)
  (example-8-integration))

