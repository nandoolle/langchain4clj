---
layout: default
title: Resilience & Failover
---

# 🛡️ Provider Failover & Circuit Breaker

Complete guide to building production-ready LLM applications with automatic failover and circuit breaker protection.

## 📚 Table of Contents

1. [Overview](#overview)
2. [Quick Start](#quick-start)
3. [Phase 1: Basic Failover](#phase-1-basic-failover)
4. [Phase 2: Circuit Breaker](#phase-2-circuit-breaker)
5. [Error Classification](#error-classification)
6. [Configuration Reference](#configuration-reference)
7. [Best Practices](#best-practices)
8. [Production Examples](#production-examples)
9. [Monitoring & Logging](#monitoring--logging)
10. [Troubleshooting](#troubleshooting)

---

## Overview

The resilience system provides automatic failover between LLM providers with intelligent error handling and circuit breaker protection. This ensures your application stays available even when individual providers fail.

### Key Features

- ✅ **Automatic Retry** - Retry transient errors (rate limits, timeouts)
- ✅ **Provider Chain** - Fallback to backup providers on failure
- ✅ **Circuit Breaker** - Prevent cascading failures in production
- ✅ **Error Classification** - Smart handling of different error types
- ✅ **Zero Breaking Changes** - Works with all existing features
- ✅ **Production Ready** - Battle-tested patterns

### When to Use

**Use basic failover (Phase 1) when:**
- You want simple retry + fallback
- You have backup providers configured
- You need cost optimization (cheap fallbacks)
- You want to avoid vendor lock-in

**Add circuit breaker (Phase 2) when:**
- You're running in production
- You need to prevent cascading failures
- You want automatic recovery
- You need to protect against provider outages

---

## Quick Start

### Installation

The resilience system is included in langchain4clj. No additional dependencies needed.

```clojure
(require '[langchain4clj.core :as llm]
         '[langchain4clj.resilience :as resilience])
```

### Basic Usage (Phase 1)

```clojure
;; Create resilient model with fallbacks
(def model
  (resilience/create-resilient-model
    {:primary (llm/create-model {:provider :openai :api-key "..."})
     :fallbacks [(llm/create-model {:provider :anthropic :api-key "..."})
                 (llm/create-model {:provider :ollama})]}))

;; Use like any other model
(llm/chat model "Hello!")
;; Tries: OpenAI → Anthropic → Ollama
```

### Production Usage (Phase 2)

```clojure
;; Add circuit breaker for production
(def production-model
  (resilience/create-resilient-model
    {:primary (llm/create-model {:provider :openai :api-key "..."})
     :fallbacks [(llm/create-model {:provider :anthropic :api-key "..."})
                 (llm/create-model {:provider :ollama})]
     :max-retries 2
     :retry-delay-ms 1000
     :circuit-breaker? true
     :failure-threshold 5
     :success-threshold 2
     :timeout-ms 60000}))

(llm/chat production-model "Hello!")
;; Automatic failover with circuit breaker protection!
```

---

## Phase 1: Basic Failover

Phase 1 provides automatic retry and fallback between providers.

### How It Works

```
Request → Retry Logic → Provider Chain → Response

1. Try primary provider with retries
2. If fails after retries → try first fallback
3. If fails → try next fallback
4. Repeat until success or all providers exhausted
```

### Error Handling

The system classifies errors into three categories:

#### 1. Retryable Errors
**Action:** Retry on the SAME provider

Examples:
- `429 Rate Limit` - Provider is rate limiting
- `503 Service Unavailable` - Temporary unavailability
- `timeout` - Network timeout

```clojure
;; Configure retries
{:max-retries 3           ;; Try up to 3 times
 :retry-delay-ms 1000}    ;; Wait 1s between retries
```

#### 2. Recoverable Errors
**Action:** Skip to NEXT provider

Examples:
- `401 Unauthorized` - Invalid API key
- `404 Not Found` - Model not found
- `connection error` - Network connectivity issues

```clojure
;; These errors trigger immediate fallback
;; No retries on same provider
```

#### 3. Non-Recoverable Errors
**Action:** Throw immediately to user

Examples:
- `400 Bad Request` - User's input is invalid
- `quota exceeded` - Account quota exhausted

```clojure
;; These are user's responsibility to fix
;; No fallback attempted
```

### Configuration

```clojure
(resilience/create-resilient-model
  {:primary model1
   :fallbacks [model2 model3]
   :max-retries 2           ;; Default: 2
   :retry-delay-ms 1000})   ;; Default: 1000ms
```

### Examples

#### Example 1: Rate Limit Handling

```clojure
(def model
  (resilience/create-resilient-model
    {:primary openai-model
     :max-retries 3
     :retry-delay-ms 2000}))  ;; Wait 2s on rate limits

;; OpenAI hits rate limit → retries 3 times → success
(llm/chat model "Hello!")
```

#### Example 2: API Key Fallback

```clojure
(def model
  (resilience/create-resilient-model
    {:primary openai-model          ;; Invalid key
     :fallbacks [anthropic-model    ;; Valid key
                 ollama-model]}))    ;; Local, no key needed

;; OpenAI fails (401) → skips to Anthropic → success
(llm/chat model "Hello!")
```

#### Example 3: Cost Optimization

```clojure
(def model
  (resilience/create-resilient-model
    {:primary (llm/create-model {:provider :openai 
                                  :model "gpt-4o-mini"})  ;; Cheap
     :fallbacks [(llm/create-model {:provider :anthropic
                                     :model "claude-3-5-sonnet-20241022"})  ;; Expensive
                 (llm/create-model {:provider :ollama})]}))  ;; Free

;; Use cheap model first, fall back to expensive only if needed
```

---

## Phase 2: Circuit Breaker

Phase 2 adds circuit breaker pattern for production resilience.

### What is a Circuit Breaker?

A circuit breaker prevents your application from repeatedly trying a failing service. It "opens" after too many failures, giving the service time to recover.

**Analogy:** Like an electrical circuit breaker that trips to prevent damage.

### State Machine

```
       ┌──────────┐
       │  Closed  │◄────────────────┐
       │ (Normal) │                 │
       └────┬─────┘                 │
            │                       │
            │ Too many failures     │ Enough successes
            ▼                       │
       ┌──────────┐                 │
       │   Open   │                 │
       │(Blocking)│                 │
       └────┬─────┘                 │
            │                       │
            │ Timeout elapsed       │
            ▼                       │
       ┌──────────┐                 │
       │Half-Open │─────────────────┘
       │ (Testing)│
       └──────────┘
```

### States Explained

#### Closed State (Normal Operation)
- All requests pass through
- Failures are counted
- If failures ≥ `failure-threshold` → transition to Open

**Example:**
```clojure
;; OpenAI is working fine
(llm/chat model "Hello")  ;; ✅ Goes to OpenAI
(llm/chat model "Hi")     ;; ✅ Goes to OpenAI
```

#### Open State (Blocking)
- Provider is temporarily disabled
- Requests skip to next provider immediately
- After `timeout-ms` → transition to Half-Open

**Example:**
```clojure
;; OpenAI failed 5 times → circuit opens
(llm/chat model "Hello")  ;; ⚠️ Skips OpenAI → tries Anthropic
(llm/chat model "Hi")     ;; ⚠️ Skips OpenAI → tries Anthropic

;; Logs: [CircuitBreaker] Closed → Open (threshold reached)
```

#### Half-Open State (Testing Recovery)
- Limited requests allowed to test recovery
- If `success-threshold` successes → back to Closed
- If any failure → back to Open

**Example:**
```clojure
;; After 60s, circuit tries OpenAI again
(llm/chat model "Test 1")  ;; 🔄 Tries OpenAI (success!)
(llm/chat model "Test 2")  ;; 🔄 Tries OpenAI (success!)
;; Circuit closes!

;; Logs: [CircuitBreaker] Open → Half-Open (timeout elapsed)
;;       [CircuitBreaker] Half-Open → Closed
```

### Configuration

```clojure
(resilience/create-resilient-model
  {:primary model
   :fallbacks [...]
   
   ;; Circuit Breaker Configuration
   :circuit-breaker? true      ;; Enable CB (default: false)
   :failure-threshold 5        ;; Failures before opening (default: 5)
   :success-threshold 2        ;; Successes before closing (default: 2)
   :timeout-ms 60000})         ;; Time in open before half-open (default: 60s)
```

### Per-Provider Circuit Breakers

Each provider has its own independent circuit breaker:

```clojure
(def model
  (resilience/create-resilient-model
    {:primary openai-model
     :fallbacks [anthropic-model ollama-model]
     :circuit-breaker? true}))

;; Scenario:
;; 1. OpenAI circuit opens → uses Anthropic
;; 2. Anthropic circuit opens → uses Ollama
;; 3. After timeout, circuits test recovery independently
```

### Logging

Circuit breaker logs all state transitions:

```
[CircuitBreaker] Closed → Open (threshold reached)
[CircuitBreaker] Open → Half-Open (timeout elapsed)
[CircuitBreaker] Half-Open → Closed
[CircuitBreaker] Half-Open → Open (test failed)
```

**Note:** Logs go to stdout. Configure your logging framework accordingly.

---

## Error Classification

Understanding how errors are classified helps you configure the system properly.

### Classification Logic

```clojure
;; Retryable (429, 503, timeout)
(retryable-error? exception)
→ Retry on same provider

;; Recoverable (401, 404, connection)
(recoverable-error? exception)
→ Try next provider

;; Non-recoverable (400, quota)
(non-recoverable-error? exception)
→ Throw to user
```

### Error Examples

| Error Type | HTTP Code | Example | Action |
|------------|-----------|---------|--------|
| **Retryable** | 429 | Rate limit exceeded | Retry + delay |
| **Retryable** | 503 | Service unavailable | Retry + delay |
| **Retryable** | - | Connection timeout | Retry + delay |
| **Recoverable** | 401 | Invalid API key | Next provider |
| **Recoverable** | 403 | Forbidden | Next provider |
| **Recoverable** | 404 | Model not found | Next provider |
| **Non-Recoverable** | 400 | Bad request | Throw immediately |
| **Non-Recoverable** | - | Quota exceeded | Throw immediately |

### Custom Error Handling

Error classification is hardcoded but you can wrap the resilient model:

```clojure
(defn my-resilient-chat [model message]
  (try
    (llm/chat model message)
    (catch Exception e
      (if (my-custom-check? e)
        (my-custom-handler e)
        (throw e)))))
```

---

## Configuration Reference

### Complete Configuration

```clojure
(resilience/create-resilient-model
  {;; Required
   :primary model                    ;; Primary provider (ChatModel)
   
   ;; Optional
   :fallbacks [model1 model2]        ;; Fallback providers (default: [])
   
   ;; Retry Configuration (Phase 1)
   :max-retries 2                    ;; Max retries per provider (default: 2)
   :retry-delay-ms 1000              ;; Delay between retries (default: 1000ms)
   
   ;; Circuit Breaker (Phase 2)
   :circuit-breaker? false           ;; Enable circuit breaker (default: false)
   :failure-threshold 5              ;; Failures before opening (default: 5)
   :success-threshold 2              ;; Successes before closing (default: 2)
   :timeout-ms 60000})               ;; Open duration before half-open (default: 60s)
```

### Validation Rules

```clojure
;; These will throw AssertionError
{:primary nil}                       ;; ❌ primary is required
{:max-retries -1}                    ;; ❌ must be >= 0
{:retry-delay-ms 0}                  ;; ❌ must be > 0
{:failure-threshold 0}               ;; ❌ must be > 0
{:success-threshold 0}               ;; ❌ must be > 0
{:timeout-ms 0}                      ;; ❌ must be > 0
```

### Defaults Explained

| Parameter | Default | Reason |
|-----------|---------|--------|
| `max-retries` | 2 | Balance between resilience and latency |
| `retry-delay-ms` | 1000 | 1s is reasonable for rate limits |
| `circuit-breaker?` | false | Backward compatibility |
| `failure-threshold` | 5 | Not too sensitive, not too slow |
| `success-threshold` | 2 | Quick recovery validation |
| `timeout-ms` | 60000 | 1 minute is typical recovery time |

---

## Best Practices

### 1. Choose Appropriate Fallbacks

**Good:**
```clojure
{:primary openai-model              ;; Fast, cheap
 :fallbacks [anthropic-model        ;; Fast, expensive
             ollama-model]}         ;; Slow, free
```

**Bad:**
```clojure
{:primary openai-model
 :fallbacks [another-openai-model]} ;; Same provider!
```

**Why:** Fallbacks should be truly independent providers.

### 2. Set Realistic Thresholds

**Development:**
```clojure
{:circuit-breaker? false            ;; Simple retry is enough
 :max-retries 1}                    ;; Fail fast during dev
```

**Production:**
```clojure
{:circuit-breaker? true
 :failure-threshold 5               ;; Not too sensitive
 :timeout-ms 60000                  ;; Allow recovery time
 :max-retries 2}
```

### 3. Monitor Your Logs

Enable circuit breaker logging in production:

```clojure
;; Watch for these patterns:
;; - Frequent Open → Half-Open → Open cycles = provider unstable
;; - Circuit stays Open = provider is down
;; - No circuit events = system is healthy
```

### 4. Handle All Providers Failing

```clojure
(try
  (llm/chat resilient-model "Hello")
  (catch Exception e
    (if (= "All providers failed or unavailable" (.getMessage e))
      (notify-ops-team! "All LLM providers are down!")
      (throw e))))
```

### 5. Test Your Configuration

```clojure
;; Test with real failures
(def test-model
  (resilience/create-resilient-model
    {:primary (llm/create-model {:provider :openai :api-key "invalid"})
     :fallbacks [(llm/create-model {:provider :ollama})]
     :circuit-breaker? true
     :failure-threshold 2
     :timeout-ms 5000}))  ;; Short timeout for testing

;; Should fail to Ollama
(llm/chat test-model "Test")

;; Wait 5s, circuit should try half-open
(Thread/sleep 6000)
(llm/chat test-model "Test again")
```

### 6. Cost Optimization

```clojure
;; Use expensive models as fallback only
(def cost-optimized
  (resilience/create-resilient-model
    {:primary (llm/create-model {:provider :openai 
                                  :model "gpt-4o-mini"})     ;; $0.15/1M tokens
     :fallbacks [(llm/create-model {:provider :anthropic
                                     :model "claude-3-5-sonnet-20241022"})  ;; $3/1M tokens
                 (llm/create-model {:provider :ollama})]}))   ;; Free
```

---

## Production Examples

### Example 1: E-commerce Chatbot

```clojure
(ns myapp.chatbot
  (:require [langchain4clj.core :as llm]
            [langchain4clj.resilience :as resilience]))

(def chatbot-model
  (resilience/create-resilient-model
    {:primary (llm/create-model {:provider :openai
                                  :api-key (System/getenv "OPENAI_KEY")
                                  :model "gpt-4o-mini"})
     :fallbacks [(llm/create-model {:provider :anthropic
                                     :api-key (System/getenv "ANTHROPIC_KEY")
                                     :model "claude-3-5-sonnet-20241022"})]
     :max-retries 2
     :retry-delay-ms 1000
     :circuit-breaker? true
     :failure-threshold 5
     :success-threshold 2
     :timeout-ms 60000}))

(defn handle-customer-query [query]
  (try
    (llm/chat chatbot-model query
      {:system-message "You are a helpful e-commerce assistant."
       :temperature 0.7})
    (catch Exception e
      (log/error e "Chatbot failed")
      "Sorry, I'm temporarily unavailable. Please try again.")))
```

### Example 2: Content Generation Pipeline

```clojure
(ns myapp.content
  (:require [langchain4clj.core :as llm]
            [langchain4clj.resilience :as resilience]
            [langchain4clj.streaming :as streaming]))

(def content-model
  (resilience/create-resilient-model
    {:primary (llm/create-model {:provider :anthropic
                                  :api-key (System/getenv "ANTHROPIC_KEY")
                                  :model "claude-3-5-sonnet-20241022"})
     :fallbacks [(llm/create-model {:provider :openai
                                     :api-key (System/getenv "OPENAI_KEY")
                                     :model "gpt-4"})]
     :circuit-breaker? true
     :failure-threshold 3        ;; More sensitive for batch jobs
     :timeout-ms 120000}))       ;; 2 minutes for content generation

(defn generate-article [topic]
  (llm/chat content-model
    (str "Write a 500-word article about: " topic)
    {:temperature 0.8
     :max-tokens 2000}))

;; Batch processing with resilience
(defn generate-articles [topics]
  (mapv generate-article topics))
```

### Example 3: AI-Powered API with Tools

```clojure
(ns myapp.api
  (:require [langchain4clj.core :as llm]
            [langchain4clj.resilience :as resilience]
            [langchain4clj.tools :as tools]))

(def weather-tool
  (tools/create-tool
    {:name "get_weather"
     :description "Get weather for a location"
     :params-schema {:location :string}
     :fn (fn [{:keys [location]}]
           (call-weather-api location))}))

(def api-model
  (resilience/create-resilient-model
    {:primary (llm/create-model {:provider :openai
                                  :api-key (System/getenv "OPENAI_KEY")
                                  :model "gpt-4o-mini"})
     :fallbacks [(llm/create-model {:provider :ollama
                                     :model "llama3.1"})]  ;; Free backup!
     :max-retries 3
     :retry-delay-ms 500
     :circuit-breaker? true
     :failure-threshold 10        ;; Higher threshold for API
     :timeout-ms 30000}))         ;; Quick recovery for API

(defn handle-request [user-query]
  (llm/chat api-model user-query
    {:tools [(tools/to-spec weather-tool)]
     :temperature 0.3}))          ;; Lower temp for API responses
```

### Example 4: Multi-Region Deployment

```clojure
(ns myapp.distributed
  (:require [langchain4clj.core :as llm]
            [langchain4clj.resilience :as resilience]))

;; Region-aware configuration
(defn create-regional-model [region]
  (resilience/create-resilient-model
    {:primary (case region
                :us (llm/create-model {:provider :openai
                                       :api-key (System/getenv "OPENAI_US_KEY")})
                :eu (llm/create-model {:provider :anthropic
                                       :api-key (System/getenv "ANTHROPIC_EU_KEY")}))
     :fallbacks [(llm/create-model {:provider :ollama})]  ;; Universal fallback
     :circuit-breaker? true
     :failure-threshold 5
     :timeout-ms 60000}))

(def us-model (create-regional-model :us))
(def eu-model (create-regional-model :eu))
```

---

## Monitoring & Logging

### Circuit Breaker Logs

The system logs all circuit breaker state transitions to stdout:

```clojure
;; Example log output
[CircuitBreaker] Closed → Open (threshold reached)
[CircuitBreaker] Open → Half-Open (timeout elapsed)
[CircuitBreaker] Half-Open → Closed
[CircuitBreaker] Half-Open → Open (test failed)
```

### Recommended Monitoring

#### 1. Log Analysis

```clojure
;; Count circuit breaker events
grep "\[CircuitBreaker\]" app.log | wc -l

;; Find which provider is failing
grep "Closed → Open" app.log
```

#### 2. Custom Metrics

```clojure
(defn instrumented-resilient-model [config]
  (let [model (resilience/create-resilient-model config)
        metrics (atom {:success 0 :failure 0 :fallback 0})]
    
    (reify ChatModel
      (^String chat [_ ^String message]
        (try
          (let [result (llm/chat model message)]
            (swap! metrics update :success inc)
            result)
          (catch Exception e
            (swap! metrics update :failure inc)
            (throw e)))))))
```

#### 3. Health Checks

```clojure
(defn health-check [model]
  (try
    (llm/chat model "ping")
    {:status :healthy}
    (catch Exception e
      {:status :unhealthy
       :error (.getMessage e)})))

;; Periodic health check
(schedule-every 60000  ;; Every minute
  #(let [health (health-check production-model)]
     (when (= :unhealthy (:status health))
       (alert! "LLM model unhealthy" health))))
```

### Integration with Observability Tools

#### Prometheus Metrics

```clojure
(ns myapp.metrics
  (:require [iapetos.core :as prometheus]))

(def registry
  (-> (prometheus/collector-registry)
      (prometheus/register
        (prometheus/counter :llm/requests :help "Total LLM requests")
        (prometheus/counter :llm/failures :help "Total LLM failures")
        (prometheus/counter :llm/fallbacks :help "Total fallback attempts")
        (prometheus/histogram :llm/latency :help "LLM request latency"))))

(defn monitored-chat [model message]
  (prometheus/inc registry :llm/requests)
  (let [start (System/currentTimeMillis)]
    (try
      (let [result (llm/chat model message)]
        (prometheus/observe registry :llm/latency 
          (- (System/currentTimeMillis) start))
        result)
      (catch Exception e
        (prometheus/inc registry :llm/failures)
        (throw e)))))
```

---

## Troubleshooting

### Problem: All Providers Failing

**Symptom:**
```
Exception: All providers failed or unavailable
```

**Possible Causes:**
1. All API keys are invalid
2. Network connectivity issues
3. All circuit breakers are open

**Solution:**
```clojure
;; Check each provider individually
(llm/chat primary-model "test")     ;; Test primary
(llm/chat fallback1-model "test")   ;; Test fallback 1

;; Verify API keys
(System/getenv "OPENAI_API_KEY")

;; Check network
(curl -v https://api.openai.com/v1/models)

;; Wait for circuit breakers to recover
(Thread/sleep 60000)
(llm/chat resilient-model "test again")
```

### Problem: Circuit Opens Too Frequently

**Symptom:**
```
[CircuitBreaker] Closed → Open (threshold reached)
[CircuitBreaker] Closed → Open (threshold reached)  ;; Too often!
```

**Solution:**
```clojure
;; Increase failure threshold
{:failure-threshold 10}  ;; Instead of 5

;; Or increase retry attempts
{:max-retries 3}         ;; Give provider more chances
```

### Problem: Circuit Stays Open

**Symptom:**
```
[CircuitBreaker] Closed → Open (threshold reached)
;; ... no Half-Open transition for long time
```

**Solution:**
```clojure
;; Provider is down - reduce timeout for faster detection
{:timeout-ms 30000}      ;; Try recovery after 30s instead of 60s

;; Or remove that provider from chain
{:fallbacks [other-providers]}  ;; Skip the failing provider
```

### Problem: High Latency

**Symptom:**
```
;; Requests take too long
```

**Possible Causes:**
1. Too many retries
2. Long retry delays
3. Many fallbacks being tried

**Solution:**
```clojure
;; Reduce retries for faster failure
{:max-retries 1           ;; Only retry once
 :retry-delay-ms 500}     ;; Shorter delay

;; Or use circuit breaker to skip failing providers
{:circuit-breaker? true}
```

### Problem: Unexpected Provider Used

**Symptom:**
```
;; Always using fallback, never primary
```

**Solution:**
```clojure
;; Check circuit breaker state
;; Primary might be open
(Thread/sleep 60000)      ;; Wait for timeout
(llm/chat model "test")   ;; Should try primary again

;; Or check primary provider
(llm/chat primary-model "test directly")
```

### Debug Mode

```clojure
;; Add custom logging
(defn debug-resilient-model [config]
  (let [model (resilience/create-resilient-model config)]
    (reify ChatModel
      (^String chat [_ ^String message]
        (println "DEBUG: Attempting chat with message:" message)
        (try
          (let [result (llm/chat model message)]
            (println "DEBUG: Success!")
            result)
          (catch Exception e
            (println "DEBUG: Failed with:" (.getMessage e))
            (throw e)))))))
```

---

## Advanced Topics

### Combining with Streaming

```clojure
(require '[langchain4clj.streaming :as streaming])

;; Create resilient streaming model
(def streaming-model
  (resilience/create-resilient-model
    {:primary (streaming/create-streaming-model {:provider :openai :api-key "..."})
     :fallbacks [(streaming/create-streaming-model {:provider :anthropic :api-key "..."})]
     :circuit-breaker? true}))

;; Stream with automatic failover
(streaming/stream-chat streaming-model "Tell me a story"
  {:on-token (fn [token] (print token) (flush))
   :on-complete (fn [_] (println "\nDone!"))})
```

### Combining with Tools

```clojure
(require '[langchain4clj.tools :as tools])

(def resilient-tool-model
  (resilience/create-resilient-model
    {:primary openai-model
     :fallbacks [anthropic-model]
     :circuit-breaker? true}))

;; Tools work with failover
(llm/chat resilient-tool-model "What's the weather in Tokyo?"
  {:tools [(tools/to-spec weather-tool)]})
```

### Combining with JSON Mode

```clojure
(require '[dev.langchain4j.model.chat.request ResponseFormat])

(def resilient-json-model
  (resilience/create-resilient-model
    {:primary openai-model
     :fallbacks [anthropic-model]
     :circuit-breaker? true}))

;; JSON mode with failover
(llm/chat resilient-json-model "Return user data"
  {:response-format ResponseFormat/JSON})
```

---

## FAQ

### Q: Does circuit breaker work per-request or per-model instance?

**A:** Per-model instance. Each resilient model has its own circuit breakers. If you create multiple instances, they have independent circuits.

```clojure
(def model1 (resilience/create-resilient-model config))
(def model2 (resilience/create-resilient-model config))
;; model1 and model2 have separate circuit breakers
```

### Q: What happens if all circuit breakers are open?

**A:** The system throws `"All providers failed or unavailable"`. You should catch this and handle gracefully (return cached response, show error message, etc).

### Q: Can I disable circuit breaker at runtime?

**A:** No, but you can create two models and switch between them:

```clojure
(def with-cb (resilience/create-resilient-model 
               {:circuit-breaker? true ...}))
(def without-cb (resilience/create-resilient-model 
                  {:circuit-breaker? false ...}))

;; Use conditionally
(def current-model (if production? with-cb without-cb))
```

### Q: How does circuit breaker interact with retries?

**A:** Retries happen BEFORE circuit breaker state is updated. So:
1. Request is allowed by circuit
2. Provider is tried with retries
3. If still fails after retries → circuit breaker records failure

### Q: Is the system thread-safe?

**A:** Yes! Circuit breaker state uses Clojure atoms which are thread-safe. Multiple threads can safely use the same resilient model.

### Q: What's the performance overhead?

**A:** Minimal:
- Without circuit breaker: ~0 overhead (just function calls)
- With circuit breaker: ~1-2 atom dereferences per request (~nanoseconds)

### Q: Can I use exponential backoff?

**A:** Not yet. Currently only fixed delay is supported. Exponential backoff is planned for a future release.

---

## Version History

- **Phase 1** (Feb 2025): Basic failover with retry
  - 240 LOC production code
  - 17 tests, 19 assertions
  
- **Phase 2** (Feb 2025): Circuit breaker
  - +120 LOC production code
  - +10 tests, +28 assertions
  - Total: 151 tests, 391 assertions

---

## Contributing

Found a bug or have a feature request? Open an issue on GitHub!

Want to improve this documentation? PRs welcome!

---

## License

Copyright © 2024 Fernando Olle

Distributed under the Eclipse Public License version 2.0.
