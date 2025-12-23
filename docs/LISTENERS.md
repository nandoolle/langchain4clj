---
layout: default
title: Chat Listeners
---

# Chat Listeners

Observability system for monitoring LLM requests, responses, and errors.

## Quick Start

```clojure
(require '[langchain4clj.listeners :as listeners])
(require '[langchain4clj.core :as llm])

;; Create a token tracker
(def stats (atom {}))
(def tracker (listeners/token-tracking-listener stats))

;; Create model with listener
(def model
  (llm/create-model
    {:provider :openai
     :api-key (System/getenv "OPENAI_API_KEY")
     :listeners [tracker]}))

;; After some requests...
@stats
;; => {:input-tokens 150 :output-tokens 80 :total-tokens 230 :request-count 3 ...}
```

## Creating Custom Listeners

Use `create-listener` with handler functions for each event type:

```clojure
(def my-listener
  (listeners/create-listener
    {:on-request  (fn [ctx]
                    (println "Sending" (count (get-in ctx [:request :messages])) "messages"))
     :on-response (fn [ctx]
                    (println "Tokens:" (get-in ctx [:response-metadata :token-usage])))
     :on-error    (fn [ctx]
                    (println "Error:" (get-in ctx [:error :error-message])))}))
```

### Request Context

The `on-request` handler receives:

```clojure
{:request {:messages [{:message-type :user :contents [...]}
                      {:message-type :ai :text "..."}]
           :parameters {:model-name "gpt-4"
                        :temperature 0.7
                        :max-output-tokens 1000}}
 :provider :openai
 :attributes {}
 :raw-context <ChatModelRequestContext>}
```

### Response Context

The `on-response` handler receives:

```clojure
{:ai-message {:message-type :ai
              :text "Response text..."}
 :response-metadata {:response-id "chatcmpl-..."
                     :model-name "gpt-4"
                     :finish-reason :stop
                     :token-usage {:input-tokens 50
                                   :output-tokens 30
                                   :total-tokens 80}}
 :request {...}
 :provider :openai
 :attributes {}
 :raw-context <ChatModelResponseContext>}
```

### Error Context

The `on-error` handler receives:

```clojure
{:error {:error-message "Rate limit exceeded"
         :error-type "dev.langchain4j.exception.RateLimitException"
         :error-cause nil}
 :request {...}
 :provider :openai
 :attributes {}
 :raw-context <ChatModelErrorContext>}
```

## Pre-built Listeners

### Logging Listener

Automatic logging with configurable levels:

```clojure
;; Default levels: request=debug, response=info, error=error
(def logger (listeners/logging-listener))

;; Custom levels
(def verbose-logger
  (listeners/logging-listener
    {:request :info
     :response :debug
     :error :warn}))
```

### Token Tracking Listener

Accumulates token usage statistics:

```clojure
(def stats (atom {}))
(def tracker (listeners/token-tracking-listener stats))

;; After requests...
@stats
;; => {:input-tokens 1500
;;     :output-tokens 800
;;     :total-tokens 2300
;;     :request-count 5
;;     :last-request {:model "gpt-4"
;;                    :tokens {:input-tokens 300 :output-tokens 150 :total-tokens 450}
;;                    :timestamp 1703123456789}
;;     :by-model {"gpt-4" {:input-tokens 1500 :output-tokens 800 :total-tokens 2300 :count 5}}}
```

### Message Capturing Listener

Records all request/response pairs:

```clojure
(let [[listener messages] (listeners/message-capturing-listener)]
  ;; Add listener to model...
  
  ;; After requests...
  @messages)
;; => [{:request {...}
;;      :response {:ai-message {...} :metadata {...}}
;;      :provider :openai
;;      :timestamp 1703123456789
;;      :completed-at 1703123457890}]
```

## Composing Listeners

Combine multiple listeners:

```clojure
(def stats (atom {}))

(def combined
  (listeners/compose-listeners
    (listeners/logging-listener)
    (listeners/token-tracking-listener stats)
    my-custom-listener))

;; Use with model
(def model
  (llm/create-model
    {:provider :openai
     :api-key "..."
     :listeners [combined]}))
```

## Adding Listeners to Models

### Via Configuration Map

```clojure
(def model
  (llm/create-model
    {:provider :openai
     :api-key "..."
     :listeners [logger tracker]}))
```

### Using with-listeners Helper

```clojure
(def model
  (-> {:provider :openai :api-key "..."}
      (listeners/with-listeners [logger tracker])
      llm/create-model))
```

## Common Patterns

### Cost Tracking

```clojure
(def cost-tracker (atom {:total-cost 0.0}))

(def cost-listener
  (listeners/create-listener
    {:on-response
     (fn [ctx]
       (let [tokens (get-in ctx [:response-metadata :token-usage])
             model (get-in ctx [:response-metadata :model-name])
             ;; Example pricing (adjust to actual rates)
             input-cost (/ (:input-tokens tokens) 1000000.0)
             output-cost (/ (:output-tokens tokens) 250000.0)]
         (swap! cost-tracker update :total-cost + input-cost output-cost)))}))
```

### Request Timing

```clojure
(def timing-stats (atom []))

(def timing-listener
  (let [start-times (atom {})]
    (listeners/create-listener
      {:on-request
       (fn [ctx]
         (swap! start-times assoc (System/identityHashCode ctx) (System/currentTimeMillis)))
       
       :on-response
       (fn [ctx]
         (let [start (get @start-times (System/identityHashCode (:raw-context ctx)))
               duration (- (System/currentTimeMillis) start)]
           (swap! timing-stats conj {:model (get-in ctx [:response-metadata :model-name])
                                     :duration-ms duration})))})))
```

### Conversation History Export

```clojure
(let [[listener messages] (listeners/message-capturing-listener)]
  ;; ... use model with listener ...
  
  ;; Export to EDN file
  (spit "conversation.edn" (pr-str @messages))
  
  ;; Export to JSON
  (require '[clojure.data.json :as json])
  (spit "conversation.json" (json/write-str @messages)))
```

## Provider Support

Listeners work with all providers:

| Provider | Request | Response | Error |
|----------|---------|----------|-------|
| OpenAI | Yes | Yes | Yes |
| Anthropic | Yes | Yes | Yes |
| Google AI Gemini | Yes | Yes | Yes |
| Vertex AI Gemini | Yes | Yes | Yes |
| Mistral | Yes | Yes | Yes |
| Ollama | Yes | Yes | Yes |

## API Reference

| Function | Description |
|----------|-------------|
| `create-listener` | Create listener from handler functions |
| `logging-listener` | Pre-built logging listener |
| `token-tracking-listener` | Track token usage in an atom |
| `message-capturing-listener` | Capture all request/response pairs |
| `compose-listeners` | Combine multiple listeners |
| `with-listeners` | Add listeners to config map |
| `listeners->java-list` | Convert to Java List for builders |

## Related

- [Core Chat](CORE_CHAT.md) - Model creation with listeners
- [Assistant](ASSISTANT.md) - Assistant system with observability
- [Message Serialization](MESSAGES.md) - Converting messages to EDN/JSON
