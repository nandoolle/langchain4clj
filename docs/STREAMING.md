---
layout: default
title: Streaming Responses
---

# Streaming Responses

Receive tokens in real-time as they're generated for better UX and interactive applications.

## Overview

Streaming allows you to receive tokens incrementally as the LLM generates them, rather than waiting for the complete response. This enables:

- Real-time display of responses (ChatGPT-style interface)
- Better perceived performance
- Early cancellation if needed
- Progress indicators
- Interactive applications

## Provider Support

| Provider | Streaming Support |
|----------|-------------------|
| OpenAI | ✅ Full support |
| Anthropic | ✅ Full support |
| Ollama | ✅ Full support |
| Google AI Gemini | ⚠️ Coming in v1.9.0+ |
| Vertex AI Gemini | ⚠️ Coming in v1.9.0+ |

## Quick Start

```clojure
(require '[langchain4clj.streaming :as streaming])
(require '[langchain4clj.core :as llm])

;; Create a streaming model
(def model (streaming/create-streaming-model
             {:provider :openai
              :api-key (System/getenv "OPENAI_API_KEY")
              :model "gpt-4o-mini"}))

;; Stream with callbacks
(streaming/stream-chat model "Explain AI in simple terms"
  {:on-token (fn [token] 
               (print token) 
               (flush))
   :on-complete (fn [response]
                  (println "\nDone!"))
   :on-error (fn [error]
               (println "Error:" (.getMessage error)))})
```

## Creating a Streaming Model

### Basic Configuration

```clojure
(def model (streaming/create-streaming-model
             {:provider :openai
              :api-key "your-api-key"
              :model "gpt-4o-mini"}))
```

### Provider-Specific Options

**OpenAI:**
```clojure
(def openai-stream (streaming/create-streaming-model
                     {:provider :openai
                      :api-key "sk-..."
                      :model "gpt-4o"
                      :temperature 0.7
                      :max-tokens 1000
                      :top-p 0.9}))
```

**Anthropic:**
```clojure
(def claude-stream (streaming/create-streaming-model
                     {:provider :anthropic
                      :api-key "sk-ant-..."
                      :model "claude-3-5-sonnet-20241022"
                      :temperature 0.8
                      :max-tokens 2000
                      :top-k 40}))
```

**Ollama:**
```clojure
(def ollama-stream (streaming/create-streaming-model
                     {:provider :ollama
                      :model "llama3.1"
                      :base-url "http://localhost:11434"
                      :temperature 0.7}))
```

## Streaming API

### Callbacks

The `stream-chat` function accepts three callbacks:

#### `:on-token` (Required)

Called for each token as it arrives:

```clojure
{:on-token (fn [token]
             ;; token is a String
             (print token)
             (flush))}
```

#### `:on-complete` (Optional)

Called when streaming finishes successfully:

```clojure
{:on-complete (fn [response]
                ;; response is a ChatResponse object
                (println "\nTotal tokens:" 
                         (-> response .tokenUsage .totalTokenCount)))}
```

#### `:on-error` (Optional)

Called if an error occurs:

```clojure
{:on-error (fn [error]
             ;; error is an Exception
             (println "Error:" (.getMessage error)))}
```

### Complete Example

```clojure
(streaming/stream-chat model "Write a haiku about coding"
  {:on-token (fn [token]
               (print token)
               (flush))
   :on-complete (fn [response]
                  (println "\n--- Metadata ---")
                  (let [usage (.tokenUsage response)]
                    (println "Prompt tokens:" (.inputTokenCount usage))
                    (println "Completion tokens:" (.outputTokenCount usage))
                    (println "Total tokens:" (.totalTokenCount usage))))
   :on-error (fn [error]
               (println "\nStreaming failed:" (.getMessage error)))})
```

## Common Patterns

### Accumulating Text

Collect the full text while streaming:

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
;; => {:text "1, 2, 3, 4, 5"
;;     :response #<ChatResponse...>}
```

### Progress Indicators

Show typing indicators or progress:

```clojure
(let [token-count (atom 0)]
  (streaming/stream-chat model "Explain quantum computing"
    {:on-token (fn [token]
                 (swap! token-count inc)
                 (when (zero? (mod @token-count 10))
                   (print "."))
                 (flush))
     :on-complete (fn [_]
                    (println "\nGenerated" @token-count "tokens"))}))
```

### Building Interactive UIs

For web applications with WebSockets:

```clojure
(defn stream-to-client [websocket-conn prompt]
  (streaming/stream-chat model prompt
    {:on-token (fn [token]
                 (send-to-websocket! websocket-conn {:type :token
                                                     :data token}))
     :on-complete (fn [response]
                    (send-to-websocket! websocket-conn {:type :complete
                                                        :metadata (extract-metadata response)}))
     :on-error (fn [error]
                 (send-to-websocket! websocket-conn {:type :error
                                                     :message (.getMessage error)}))}))
```

### Early Cancellation

Use futures for cancellation:

```clojure
(let [streaming-future (future
                         (streaming/stream-chat model "Long response..."
                           {:on-token (fn [token] (print token) (flush))}))]
  
  ;; Cancel after 5 seconds
  (Thread/sleep 5000)
  (future-cancel streaming-future))
```

## Extracting Metadata

Access token usage and other metadata from the response:

```clojure
(streaming/stream-chat model "Hello"
  {:on-complete (fn [response]
                  (let [usage (.tokenUsage response)
                        ai-msg (.aiMessage response)]
                    (println "Input tokens:" (.inputTokenCount usage))
                    (println "Output tokens:" (.outputTokenCount usage))
                    (println "Total tokens:" (.totalTokenCount usage))
                    (println "Model:" (.text ai-msg))
                    (println "Finish reason:" (.finishReason response))))})
```

## Error Handling

Always include error handling for production:

```clojure
(streaming/stream-chat model prompt
  {:on-token (fn [token] 
               (try
                 (process-token token)
                 (catch Exception e
                   (log/error e "Token processing failed"))))
   
   :on-error (fn [error]
               (log/error error "Streaming failed")
               (notify-user "Failed to generate response")
               (fallback-to-non-streaming))})
```

## Comparison: Streaming vs Non-Streaming

### Non-Streaming (Regular Chat)

```clojure
(def response (llm/chat model "Long explanation..."))
;; Waits for complete response
;; User sees nothing until done
;; Returns complete string
```

**Pros:**
- Simpler API
- Get complete text immediately
- Easier to work with

**Cons:**
- Perceived as slow for long responses
- No progress indication
- Can't cancel mid-generation

### Streaming

```clojure
(streaming/stream-chat model "Long explanation..."
  {:on-token (fn [token] (print token) (flush))})
;; Tokens appear immediately
;; User sees progress
;; Can cancel early
```

**Pros:**
- Better UX (feels faster)
- Real-time feedback
- Can cancel early
- Progress indicators

**Cons:**
- More complex callback handling
- Need to accumulate text yourself
- Slightly more code

## Configuration Options

Full configuration reference for streaming models:

```clojure
(streaming/create-streaming-model
  {:provider :openai              ;; Required: :openai, :anthropic, :ollama
   :api-key "your-key"            ;; Required for most providers
   :model "model-name"            ;; Optional, uses provider default
   :temperature 0.7               ;; Optional, 0.0-2.0
   :max-tokens 1000               ;; Optional, max output tokens
   :top-p 0.9                     ;; Optional, nucleus sampling
   :top-k 40                      ;; Optional (Anthropic, Ollama)
   :frequency-penalty 0.0         ;; Optional (OpenAI)
   :presence-penalty 0.0          ;; Optional (OpenAI)
   :timeout 60000                 ;; Optional, milliseconds
   :base-url "http://..."})       ;; Optional (Ollama)
```

## Best Practices

### 1. Always Flush Output

```clojure
{:on-token (fn [token]
             (print token)
             (flush))}  ;; Important for immediate display
```

### 2. Handle Errors Gracefully

```clojure
{:on-error (fn [error]
             (log/error error)
             (notify-user "Generation failed")
             (fallback-to-cached-response))}
```

### 3. Accumulate When Needed

```clojure
(let [buffer (atom [])]
  (streaming/stream-chat model prompt
    {:on-token (fn [token] 
                 (swap! buffer conj token))
     :on-complete (fn [_]
                    (process-complete-text (str/join @buffer)))}))
```

### 4. Show Progress

```clojure
(let [progress (atom 0)]
  (streaming/stream-chat model prompt
    {:on-token (fn [_]
                 (swap! progress inc)
                 (update-progress-bar @progress))
     :on-complete (fn [_]
                    (hide-progress-bar))}))
```

### 5. Timeout for Long Streams

```clojure
(def model (streaming/create-streaming-model
             {:provider :openai
              :api-key "sk-..."
              :timeout 30000}))  ;; 30 second timeout
```

## Performance Considerations

- **Network Overhead**: Streaming adds minimal overhead
- **Buffer Size**: Tokens arrive in small chunks (usually 1-3 tokens)
- **Latency**: First token typically arrives within 200-500ms
- **Throughput**: Same total time as non-streaming, but better perceived performance

## Limitations

### Google Gemini Streaming

Currently not supported in LangChain4j 1.8.0. Will be available in v1.9.0+.

Workaround for now:
```clojure
;; Use OpenAI or Anthropic for streaming
(def fallback-stream (streaming/create-streaming-model
                       {:provider :openai
                        :api-key "sk-..."}))
```

## See Also

- [Core Chat](README.md#core-chat) - Non-streaming chat
- [Resilience & Failover](RESILIENCE.md) - Error handling and retries
- [Assistant System](ASSISTANT.md) - Streaming with assistants
