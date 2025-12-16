---
layout: default
title: Memory Patterns
---

# Memory Patterns

Managing conversation history in langchain4clj.

## Quick Start

```clojure
(require '[langchain4clj.assistant :as assistant]
         '[langchain4clj.core :as llm])

(def model (llm/create-model {:provider :openai
                               :api-key (System/getenv "OPENAI_API_KEY")}))

(def my-memory (assistant/memory {:max-messages 20}))

(def my-assistant
  (-> {:model model}
      assistant/assistant
      (assistant/with-memory my-memory)
      assistant/build-assistant))

(my-assistant "My name is Alice")
;; => "Nice to meet you, Alice!"

(my-assistant "What's my name?")
;; => "Your name is Alice."
```

## ChatMemory Protocol

```clojure
(defprotocol ChatMemory
  (add-message! [this message])
  (get-messages [this])
  (clear! [this]))
```

## Creating Memory

```clojure
;; Default (10 messages)
(def memory (assistant/memory {}))

;; Custom limit
(def memory (assistant/memory {:max-messages 50}))
```

When memory exceeds the limit, oldest messages are removed automatically.

## Using with Assistants

```clojure
(def my-assistant
  (-> {:model model}
      assistant/assistant
      (assistant/with-memory (assistant/memory {:max-messages 30}))
      (assistant/with-system-message "You are helpful.")
      assistant/build-assistant))

;; Clear memory
(my-assistant "Start fresh" {:clear-memory? true})
```

## Common Patterns

### Sliding Window (Default)

```clojure
(def memory (assistant/memory {:max-messages 20}))
```

### Per-Session Memory

```clojure
(defn create-conversation []
  (let [memory (assistant/memory {:max-messages 50})]
    (-> {:model model}
        assistant/assistant
        (assistant/with-memory memory)
        assistant/build-assistant)))

(def conv-1 (create-conversation))
(def conv-2 (create-conversation))
```

### Shared Memory

```clojure
(def shared-memory (assistant/memory {:max-messages 100}))

(def assistant-1
  (-> {:model model}
      assistant/assistant
      (assistant/with-memory shared-memory)
      assistant/build-assistant))

(def assistant-2
  (-> {:model model}
      assistant/assistant
      (assistant/with-memory shared-memory)
      assistant/build-assistant))
```

### Stateless

```clojure
(defn ask [question]
  (my-assistant question {:clear-memory? true}))
```

## Manual Management

```clojure
(import '[dev.langchain4j.data.message UserMessage AiMessage SystemMessage])

(def history (atom []))

(defn chat [user-input]
  (swap! history conj (UserMessage. user-input))
  (let [messages (into [(SystemMessage. "You are helpful.")] @history)
        response (llm/chat model messages {})]
    (swap! history conj (AiMessage. (.text (.aiMessage response))))
    (.text (.aiMessage response))))
```

## Guidelines

Choose memory size based on use case:
- Q&A bots: 10 messages
- Customer support: 30 messages
- Long conversations: 50+ messages

Token limits per model:
- GPT-4o: 128K tokens
- Claude 3.5: 200K tokens
- Gemini 1.5: 1M tokens

## Related

- [Core Chat](CORE_CHAT.md) - Basic chat
- [Assistant](ASSISTANT.md) - High-level abstractions
- [Streaming](STREAMING.md) - Real-time output
