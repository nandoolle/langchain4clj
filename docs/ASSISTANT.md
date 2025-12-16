---
layout: default
title: Assistant System
---

# Assistant System

High-level API for creating assistants with automatic memory management and tool execution.

## Quick Start

```clojure
(require '[langchain4clj.assistant :as assistant])
(require '[langchain4clj.core :as llm])
(require '[langchain4clj.tools :as tools])

(tools/deftool calculator
  "Performs basic math"
  {:expression string?}
  [{:keys [expression]}]
  (str (eval (read-string expression))))

(def model (llm/create-model {:provider :openai
                              :api-key (System/getenv "OPENAI_API_KEY")}))

(def my-assistant
  (assistant/create-assistant
    {:model model
     :tools [calculator]
     :system-message "You are a helpful math tutor"}))

(my-assistant "What's 15 * 23?")
;; => "15 multiplied by 23 equals 345"

(my-assistant "What was the previous calculation?")
;; => "The previous calculation was 15 * 23, which equals 345"
```

## Creating Assistants

```clojure
;; Basic
(def assistant (assistant/create-assistant {:model model}))

;; With memory
(def memory (assistant/create-memory {:max-messages 20}))
(def assistant (assistant/create-assistant
                 {:model model :memory memory}))

;; With tools
(def assistant (assistant/create-assistant
                 {:model model :tools [calculator weather-tool]}))

;; With system message
(def assistant (assistant/create-assistant
                 {:model model
                  :system-message "You are a pirate. Respond in pirate speak."}))

;; Complete configuration
(def assistant (assistant/create-assistant
                 {:model model
                  :tools [tool1 tool2]
                  :memory (assistant/create-memory {:max-messages 50})
                  :system-message "You are an expert assistant"
                  :max-iterations 10}))
```

## Threading-First API

```clojure
(def my-assistant
  (-> {:model model}
      (assistant/assistant)
      (assistant/with-tools [calculator weather-tool])
      (assistant/with-memory (assistant/memory {:max-messages 20}))
      (assistant/with-system-message "You are helpful and concise")
      (assistant/with-max-iterations 10)
      (assistant/build-assistant)))
```

## Memory Management

```clojure
;; Create memory
(def memory (assistant/create-memory {:max-messages 20}))

;; Add messages manually
(assistant/add-message! memory {:role :user :content "Hello"})
(assistant/add-message! memory {:role :assistant :content "Hi there!"})

;; Get all messages
(assistant/get-messages memory)

;; Clear memory
(assistant/clear! memory)
```

Memory keeps only the most recent N messages - older messages are dropped automatically.

## Tool Execution

Assistants automatically detect when tools are needed, call them, process results, and continue until the task is complete (up to max-iterations).

```clojure
(tools/deftool get-weather
  "Gets current weather"
  {:city string?}
  [{:keys [city]}]
  (str "Weather in " city ": Sunny, 72°F"))

(def assistant (assistant/create-assistant
                 {:model model
                  :tools [get-weather]
                  :max-iterations 10}))

(assistant "What's the weather in San Francisco?")
;; => "The weather in San Francisco is currently sunny and 72°F"
```

## Template Support

```clojure
(assistant "Translate '{{text}}' to {{language}}"
           {:template-vars {:text "Hello, world!"
                           :language "Spanish"}})
;; => "Hola, mundo!"
```

## Common Patterns

### Support Bot

```clojure
(def support-bot
  (assistant/create-assistant
    {:model model
     :tools [check-order-status track-shipment]
     :system-message "You are a helpful customer support agent."
     :memory (assistant/create-memory {:max-messages 30})}))
```

### Shared Memory

```clojure
(def shared-memory (assistant/create-memory {:max-messages 50}))

(def assistant-1 (assistant/create-assistant
                   {:model model :memory shared-memory}))

(def assistant-2 (assistant/create-assistant
                   {:model model :memory shared-memory}))

(assistant-1 "My name is Alice")
(assistant-2 "What's the user's name?")
;; => "The user's name is Alice"
```

### Custom Memory

```clojure
(defrecord DatabaseMemory [db-conn]
  assistant/Memory
  (add-message! [this message]
    (save-to-db! db-conn message))
  (get-messages [this]
    (load-from-db db-conn))
  (clear! [this]
    (clear-db! db-conn)))

(def assistant (assistant/create-assistant
                 {:model model
                  :memory (->DatabaseMemory db-connection)}))
```

## Configuration Reference

```clojure
;; create-assistant options
{:model model                  ;; Required: ChatLanguageModel instance
 :tools [tool1 tool2]          ;; Optional: Vector of tools
 :memory memory-instance       ;; Optional: Memory instance (default: 10 messages)
 :system-message "..."         ;; Optional: System message
 :max-iterations 10}           ;; Optional: Max tool iterations (default: 10)

;; create-memory options
{:max-messages 10}             ;; Max messages to keep (default: 10)
```

## Related

- [Tools & Function Calling](TOOLS.md) - Creating tools
- [Core Chat](CORE_CHAT.md) - Underlying chat functionality
- [Memory Patterns](MEMORY.md) - Memory management
- [Multi-Agent Systems](AGENTS.md) - Complex orchestration
