---
layout: default
title: Resilience & Failover
---

# Provider Failover & Circuit Breaker

Automatic failover between LLM providers with circuit breaker protection.

## Quick Start

```clojure
(require '[langchain4clj.core :as llm]
         '[langchain4clj.resilience :as resilience])

;; Basic failover
(def model
  (resilience/create-resilient-model
    {:primary (llm/create-model {:provider :openai :api-key "..."})
     :fallbacks [(llm/create-model {:provider :anthropic :api-key "..."})
                 (llm/create-model {:provider :ollama})]}))

(llm/chat model "Hello!")
;; Tries: OpenAI → Anthropic → Ollama

;; With circuit breaker
(def production-model
  (resilience/create-resilient-model
    {:primary (llm/create-model {:provider :openai :api-key "..."})
     :fallbacks [(llm/create-model {:provider :anthropic :api-key "..."})]
     :max-retries 2
     :retry-delay-ms 1000
     :circuit-breaker? true
     :failure-threshold 5
     :success-threshold 2
     :timeout-ms 60000}))
```

## Error Classification

| Error Type | Examples | Action |
|------------|----------|--------|
| Retryable | 429 Rate limit, 503, timeout | Retry on same provider |
| Recoverable | 401 Unauthorized, 404, connection error | Try next provider |
| Non-Recoverable | 400 Bad request, quota exceeded | Throw to user |

## Circuit Breaker States

```
Closed (Normal) → Too many failures → Open (Blocking)
                                          ↓
                                     Timeout elapsed
                                          ↓
                                    Half-Open (Testing)
                                          ↓
                              Enough successes → Closed
```

- **Closed**: Requests pass through, failures are counted
- **Open**: Provider disabled, requests skip to fallback
- **Half-Open**: Testing recovery with limited requests

## Configuration

```clojure
(resilience/create-resilient-model
  {:primary model                    ;; Required
   :fallbacks [model1 model2]        ;; Optional
   
   ;; Retry
   :max-retries 2                    ;; Default: 2
   :retry-delay-ms 1000              ;; Default: 1000ms
   
   ;; Circuit Breaker
   :circuit-breaker? false           ;; Default: false
   :failure-threshold 5              ;; Failures before opening
   :success-threshold 2              ;; Successes before closing
   :timeout-ms 60000})               ;; Time before half-open
```

## Common Patterns

### Cost Optimization

```clojure
(def model
  (resilience/create-resilient-model
    {:primary (llm/create-model {:provider :openai :model "gpt-4o-mini"})
     :fallbacks [(llm/create-model {:provider :anthropic :model "claude-3-5-sonnet-20241022"})
                 (llm/create-model {:provider :ollama})]}))
;; Use cheap model first, expensive as fallback
```

### Production Setup

```clojure
(def chatbot-model
  (resilience/create-resilient-model
    {:primary (llm/create-model {:provider :openai :api-key (System/getenv "OPENAI_KEY")})
     :fallbacks [(llm/create-model {:provider :anthropic :api-key (System/getenv "ANTHROPIC_KEY")})]
     :max-retries 2
     :circuit-breaker? true
     :failure-threshold 5
     :timeout-ms 60000}))

(defn handle-query [query]
  (try
    (llm/chat chatbot-model query)
    (catch Exception e
      (log/error e "Chatbot failed")
      "Sorry, I'm temporarily unavailable.")))
```

### With Streaming

```clojure
(def streaming-model
  (resilience/create-resilient-model
    {:primary (streaming/create-streaming-model {:provider :openai :api-key "..."})
     :fallbacks [(streaming/create-streaming-model {:provider :anthropic :api-key "..."})]
     :circuit-breaker? true}))
```

## Guidelines

- Use independent providers as fallbacks (not multiple OpenAI keys)
- Set `failure-threshold` high enough to avoid false positives
- Monitor circuit breaker logs for provider health
- Handle "All providers failed" exception gracefully

## Related

- [Core Chat](CORE_CHAT.md) - Basic chat functionality
- [Streaming](STREAMING.md) - Streaming with failover
