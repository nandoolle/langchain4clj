# Chat Memory

Comprehensive chat memory management with integrated token tracking and auto-reset strategies.

## Quick Start

```clojure
(require '[langchain4clj.memory :as mem]
         '[langchain4clj.assistant :as asst])

;; Create basic memory
(def memory (mem/create-memory {:max-messages 100}))

;; Use with assistant
(def my-assistant
  (asst/create-assistant
    {:model model
     :memory memory}))
```

## Core Features

### 1. Basic Memory

Sliding window memory that keeps the last N messages:

```clojure
(def memory (mem/create-memory {:max-messages 50}))

;; Add messages
(mem/add-message! memory (UserMessage. "Hello"))
(mem/add-message! memory (AiMessage. "Hi there"))

;; Get all messages
(mem/get-messages memory)

;; Get stats
(mem/stats memory)  ; => {:message-count 2 :token-count 0}

;; Clear
(mem/clear! memory)
```

### 2. Token Tracking (Integrated)

Track token usage by passing TokenUsage metadata from ChatResponse:

```clojure
(let [response (llm/chat model "Hello")]
  (mem/add-message! memory
                    (.aiMessage response)
                    {:token-usage (-> response .metadata .tokenUsage)}))

(mem/stats memory)  ; => {:message-count 1 :token-count 127}
```

### 3. Auto-Reset

Automatically reset when reaching thresholds:

```clojure
(def smart-memory
  (-> (mem/create-memory {:max-messages 100})
      (mem/with-auto-reset {:reset-threshold 0.85
                            :max-tokens 16000
                            :context [(SystemMessage. "Context")]})))
```

### 4. Stateless Mode

Clears before each session:

```clojure
(def stateless-memory
  (-> (mem/create-memory)
      (mem/with-stateless-mode {:context [(SystemMessage. "Context")]})))
```

## See Full Documentation

For complete API reference and examples, see the main README.
