---
layout: default
title: Streaming Responses
---

# Streaming Responses

Receive tokens in real-time as they're generated.

## Provider Support

| Provider | Streaming Support |
|----------|-------------------|
| OpenAI | Full support |
| Anthropic | Full support |
| Ollama | Full support |
| Google AI Gemini | Coming in v1.9.0+ |

## Quick Start

```clojure
(require '[langchain4clj.streaming :as streaming])

(def model (streaming/create-streaming-model
             {:provider :openai
              :api-key (System/getenv "OPENAI_API_KEY")
              :model "gpt-4o-mini"}))

(streaming/stream-chat model "Explain AI"
  {:on-token (fn [token] (print token) (flush))
   :on-complete (fn [response] (println "\nDone!"))
   :on-error (fn [error] (println "Error:" (.getMessage error)))})
```

## Creating Models

```clojure
;; OpenAI
(streaming/create-streaming-model
  {:provider :openai
   :api-key "sk-..."
   :model "gpt-4o"
   :temperature 0.7})

;; Anthropic
(streaming/create-streaming-model
  {:provider :anthropic
   :api-key "sk-ant-..."
   :model "claude-3-5-sonnet-20241022"})

;; Ollama
(streaming/create-streaming-model
  {:provider :ollama
   :model "llama3.1"
   :base-url "http://localhost:11434"})
```

## Callbacks

```clojure
(streaming/stream-chat model "Write a haiku"
  {;; Required - called for each token
   :on-token (fn [token]
               (print token)
               (flush))
   
   ;; Optional - called when complete
   :on-complete (fn [response]
                  (let [usage (.tokenUsage response)]
                    (println "Tokens:" (.totalTokenCount usage))))
   
   ;; Optional - called on error
   :on-error (fn [error]
               (println "Error:" (.getMessage error)))})
```

## Common Patterns

### Accumulating Text

```clojure
(let [accumulated (atom "")
      result (promise)]
  (streaming/stream-chat model "Count to 5"
    {:on-token (fn [token]
                 (print token)
                 (flush)
                 (swap! accumulated str token))
     :on-complete (fn [response]
                    (deliver result {:text @accumulated
                                    :response response}))})
  @result)
```

### WebSocket Streaming

```clojure
(defn stream-to-client [ws-conn prompt]
  (streaming/stream-chat model prompt
    {:on-token (fn [token]
                 (send! ws-conn {:type :token :data token}))
     :on-complete (fn [_]
                    (send! ws-conn {:type :complete}))
     :on-error (fn [error]
                 (send! ws-conn {:type :error :message (.getMessage error)}))}))
```

### Early Cancellation

```clojure
(let [f (future
          (streaming/stream-chat model "Long response..."
            {:on-token (fn [token] (print token) (flush))}))]
  (Thread/sleep 5000)
  (future-cancel f))
```

## Configuration Options

```clojure
(streaming/create-streaming-model
  {:provider :openai        ;; :openai, :anthropic, :ollama
   :api-key "your-key"
   :model "model-name"
   :temperature 0.7         ;; 0.0-2.0
   :max-tokens 1000
   :top-p 0.9
   :top-k 40                ;; Anthropic, Ollama
   :timeout 60000           ;; milliseconds
   :base-url "http://..."}) ;; Ollama
```

## Related

- [Core Chat](CORE_CHAT.md) - Non-streaming chat
- [Assistant System](ASSISTANT.md) - Streaming with assistants
