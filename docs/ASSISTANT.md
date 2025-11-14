---
layout: default
title: Assistant System
---

# Assistant System

High-level API for creating assistants with automatic memory management, tool execution loops, and template support.

## Overview

The Assistant System provides a convenient abstraction over chat models with built-in:
- **Automatic Memory Management** - Conversation history maintained automatically
- **Tool Execution Loops** - Automatic tool calling until task completion
- **Template Support** - Variable substitution in prompts
- **System Messages** - Consistent personality and behavior
- **Iteration Limits** - Prevent infinite loops

Think of assistants as stateful agents that remember conversations and can use tools without manual orchestration.

## Quick Start

```clojure
(require '[langchain4clj.assistant :as assistant])
(require '[langchain4clj.core :as llm])
(require '[langchain4clj.tools :as tools])

;; Create tools
(tools/deftool calculator
  "Performs basic math"
  {:expression string?}
  [{:keys [expression]}]
  (str (eval (read-string expression))))

;; Create a model
(def model (llm/create-model {:provider :openai
                              :api-key (System/getenv "OPENAI_API_KEY")}))

;; Create an assistant
(def my-assistant
  (assistant/create-assistant
    {:model model
     :tools [calculator]
     :system-message "You are a helpful math tutor"}))

;; Use it - memory and tools work automatically!
(my-assistant "What's 15 * 23?")
;; => "15 multiplied by 23 equals 345"

(my-assistant "What was the previous calculation?")
;; => "The previous calculation was 15 * 23, which equals 345"
```

## Creating Assistants

### Basic Assistant

```clojure
(def assistant (assistant/create-assistant
                 {:model model}))

(assistant "Hello!")
;; => "Hello! How can I help you today?"
```

### With Memory

```clojure
(def memory (assistant/create-memory {:max-messages 20}))

(def assistant (assistant/create-assistant
                 {:model model
                  :memory memory}))

(assistant "My name is Alice")
(assistant "What's my name?")
;; => "Your name is Alice"
```

### With Tools

```clojure
(def assistant (assistant/create-assistant
                 {:model model
                  :tools [calculator weather-tool search-tool]}))

(assistant "What's the weather in Tokyo and what's 2+2?")
;; Automatically calls both tools and synthesizes answer
```

### With System Message

```clojure
(def assistant (assistant/create-assistant
                 {:model model
                  :system-message "You are a pirate. Always respond in pirate speak."}))

(assistant "Hello!")
;; => "Ahoy there, matey! What can this old sea dog do fer ye?"
```

### Complete Configuration

```clojure
(def assistant (assistant/create-assistant
                 {:model model
                  :tools [tool1 tool2 tool3]
                  :memory (assistant/create-memory {:max-messages 50})
                  :system-message "You are an expert assistant"
                  :max-iterations 10}))
```

## Threading-First API

For a more composable style:

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

### Creating Memory

```clojure
;; Default: 10 messages
(def memory (assistant/create-memory))

;; Custom size
(def large-memory (assistant/create-memory {:max-messages 100}))

;; Alias function
(def memory (assistant/memory {:max-messages 20}))
```

### Memory Protocol

```clojure
(require '[langchain4clj.assistant :as assistant])

;; Add messages manually
(assistant/add-message! memory {:role :user :content "Hello"})
(assistant/add-message! memory {:role :assistant :content "Hi there!"})

;; Get all messages
(assistant/get-messages memory)
;; => [{:role :user :content "Hello"}
;;     {:role :assistant :content "Hi there!"}]

;; Clear memory
(assistant/clear! memory)
```

### Conversation Window

Memory keeps only the most recent N messages:

```clojure
(def memory (assistant/create-memory {:max-messages 5}))

;; After 6 messages, oldest is dropped
(assistant/add-message! memory {:role :user :content "Msg 1"})
(assistant/add-message! memory {:role :user :content "Msg 2"})
(assistant/add-message! memory {:role :user :content "Msg 3"})
(assistant/add-message! memory {:role :user :content "Msg 4"})
(assistant/add-message! memory {:role :user :content "Msg 5"})
(assistant/add-message! memory {:role :user :content "Msg 6"})

(count (assistant/get-messages memory))
;; => 5  (Msg 1 was dropped)
```

## Tool Execution

### Automatic Tool Calling

Assistants automatically:
1. Detect when tools are needed
2. Call the appropriate tools
3. Process tool results
4. Continue conversation with results
5. Repeat until task complete (up to max-iterations)

```clojure
(tools/deftool get-weather
  "Gets current weather"
  {:city string?}
  [{:keys [city]}]
  (str "Weather in " city ": Sunny, 72°F"))

(def assistant (assistant/create-assistant
                 {:model model
                  :tools [get-weather]}))

;; Automatically calls tool and uses result
(assistant "What's the weather in San Francisco?")
;; => "The weather in San Francisco is currently sunny and 72°F"
```

### Multiple Tool Calls

```clojure
(tools/deftool calculator
  "Does math"
  {:expression string?}
  [{:keys [expression]}]
  (str (eval (read-string expression))))

(tools/deftool converter
  "Converts units"
  {:value number? :from string? :to string?}
  [{:keys [value from to]}]
  (convert-units value from to))

(def assistant (assistant/create-assistant
                 {:model model
                  :tools [calculator converter]}))

;; Calls both tools as needed
(assistant "What's 100 + 50 in kilometers if it's in meters?")
;; Calls calculator first, then converter
```

### Max Iterations

Prevent infinite tool loops:

```clojure
(def assistant (assistant/create-assistant
                 {:model model
                  :tools [search-tool calculator]
                  :max-iterations 10}))  ;; Stop after 10 tool calls

;; If assistant keeps calling tools, stops at 10 iterations
```

## Template Support

Use variables in prompts:

```clojure
(assistant "Translate '{{text}}' to {{language}}"
           {:template-vars {:text "Hello, world!"
                           :language "Spanish"}})
;; Becomes: "Translate 'Hello, world!' to Spanish"
;; => "Hola, mundo!"
```

### Multiple Variables

```clojure
(assistant "Write a {{style}} story about {{topic}} in {{words}} words"
           {:template-vars {:style "scary"
                           :topic "AI"
                           :words "100"}})
```

### With Memory

Templates work with memory too:

```clojure
(assistant "My favorite color is {{color}}"
           {:template-vars {:color "blue"}})

(assistant "What's my favorite color?")
;; => "Your favorite color is blue"
```

## Common Patterns

### Customer Support Bot

```clojure
(tools/deftool check-order-status
  "Checks order status"
  {:order-id string?}
  [{:keys [order-id]}]
  (query-database :orders {:id order-id}))

(tools/deftool track-shipment
  "Tracks shipment"
  {:tracking-number string?}
  [{:keys [tracking-number]}]
  (call-shipping-api tracking-number))

(def support-bot
  (assistant/create-assistant
    {:model model
     :tools [check-order-status track-shipment]
     :system-message "You are a helpful customer support agent. Be friendly and efficient."
     :memory (assistant/create-memory {:max-messages 30})}))

(support-bot "Hi, I need help with order #12345")
(support-bot "Can you track the shipment?")
```

### Research Assistant

```clojure
(tools/deftool search-web
  "Searches the web"
  {:query string?}
  [{:keys [query]}]
  (perform-web-search query))

(tools/deftool summarize-article
  "Summarizes an article"
  {:url string?}
  [{:keys [url]}]
  (fetch-and-summarize url))

(def researcher
  (assistant/create-assistant
    {:model model
     :tools [search-web summarize-article]
     :system-message "You are a research assistant. Provide well-researched, cited answers."}))

(researcher "What are the latest developments in quantum computing?")
```

### Code Helper

```clojure
(tools/deftool run-code
  "Executes code"
  {:language string? :code string?}
  [{:keys [language code]}]
  (execute-in-sandbox language code))

(tools/deftool search-docs
  "Searches documentation"
  {:library string? :query string?}
  [{:keys [library query]}]
  (search-api-docs library query))

(def code-assistant
  (assistant/create-assistant
    {:model model
     :tools [run-code search-docs]
     :system-message "You are a coding assistant. Write clean, well-documented code."}))

(code-assistant "Write a function to reverse a string in Python and test it")
```

### Personal Assistant

```clojure
(tools/deftool add-todo
  "Adds a todo item"
  {:task string? :priority string?}
  [{:keys [task priority]}]
  (save-todo task priority))

(tools/deftool check-calendar
  "Checks calendar"
  {:date string?}
  [{:keys [date]}]
  (get-calendar-events date))

(def personal-assistant
  (assistant/create-assistant
    {:model model
     :tools [add-todo check-calendar]
     :memory (assistant/create-memory {:max-messages 100})
     :system-message "You are a personal assistant. Be proactive and organized."}))

(personal-assistant "Add a todo to review the report by Friday, high priority")
(personal-assistant "What's on my calendar for tomorrow?")
```

## Advanced Usage

### Shared Memory Between Assistants

```clojure
(def shared-memory (assistant/create-memory {:max-messages 50}))

(def assistant-1 (assistant/create-assistant
                   {:model model
                    :memory shared-memory
                    :system-message "You are Assistant 1"}))

(def assistant-2 (assistant/create-assistant
                   {:model model
                    :memory shared-memory
                    :system-message "You are Assistant 2"}))

(assistant-1 "My name is Alice")
(assistant-2 "What's the user's name?")
;; => "The user's name is Alice"
```

### Custom Memory Implementation

```clojure
(require '[langchain4clj.assistant :as assistant])

(defrecord DatabaseMemory [db-conn]
  assistant/Memory
  (add-message! [this message]
    (save-to-db! db-conn message))
  (get-messages [this]
    (load-from-db db-conn))
  (clear! [this]
    (clear-db! db-conn)))

(def db-memory (->DatabaseMemory db-connection))

(def assistant (assistant/create-assistant
                 {:model model
                  :memory db-memory}))
```

### Streaming with Assistants

```clojure
(require '[langchain4clj.streaming :as streaming])

;; Create streaming model
(def streaming-model (streaming/create-streaming-model
                       {:provider :openai
                        :api-key "sk-..."}))

;; Use in assistant
(def streaming-assistant
  (assistant/create-assistant
    {:model streaming-model
     :memory (assistant/create-memory)}))

;; Memory still works, but need to handle streaming manually
;; Note: Assistants with tools don't work well with streaming
```

## Error Handling

### Tool Execution Errors

```clojure
(tools/deftool risky-operation
  "Might fail"
  {:param string?}
  [{:keys [param]}]
  (if (valid? param)
    (perform-operation param)
    (throw (ex-info "Invalid parameter" {:param param}))))

(def assistant (assistant/create-assistant
                 {:model model
                  :tools [risky-operation]}))

;; Tool errors are caught and reported to the model
(assistant "Do a risky operation with bad input")
;; Model receives error message and can respond appropriately
```

### Max Iterations Reached

```clojure
(def assistant (assistant/create-assistant
                 {:model model
                  :tools [complex-tool]
                  :max-iterations 5}))

;; If task requires >5 tool calls, stops and returns best effort
(assistant "Complex task requiring many steps...")
;; => "I've made progress but reached my iteration limit. Here's what I found..."
```

## Best Practices

### 1. Set Appropriate Memory Size

```clojure
;; Short conversations (chat bot)
(assistant/create-memory {:max-messages 10})

;; Medium conversations (customer support)
(assistant/create-memory {:max-messages 30})

;; Long conversations (research assistant)
(assistant/create-memory {:max-messages 100})
```

### 2. Use Descriptive System Messages

```clojure
;; ❌ Vague
{:system-message "You are helpful"}

;; ✅ Specific
{:system-message "You are a customer support agent for TechCo. Be friendly, professional, and always offer to escalate complex issues."}
```

### 3. Provide Good Tool Descriptions

```clojure
;; ❌ Bad
(tools/deftool calc "Calculates" ...)

;; ✅ Good
(tools/deftool calculator
  "Performs mathematical calculations. Use for arithmetic operations like addition, subtraction, multiplication, division."
  ...)
```

### 4. Set Reasonable Iteration Limits

```clojure
;; Simple tasks
{:max-iterations 3}

;; Complex tasks
{:max-iterations 10}

;; Research/analysis
{:max-iterations 20}
```

### 5. Clear Memory When Appropriate

```clojure
(defn new-conversation [assistant]
  (assistant/clear! (:memory assistant))
  assistant)

;; Start fresh conversation
(new-conversation my-assistant)
(my-assistant "Hello!")
```

## Configuration Reference

### create-assistant Options

```clojure
{:model model                      ;; Required: ChatLanguageModel instance
 :tools [tool1 tool2]              ;; Optional: Vector of tools
 :memory memory-instance           ;; Optional: Memory instance (default: 10 messages)
 :system-message "..."             ;; Optional: System message string
 :max-iterations 10}               ;; Optional: Max tool call iterations (default: 10)
```

### create-memory Options

```clojure
{:max-messages 10}                 ;; Optional: Max messages to keep (default: 10)
```

## Limitations

### 1. Memory is Lost on Restart

In-memory storage means conversations don't persist:

```clojure
;; Solution: Use custom memory with persistence
(defrecord PersistentMemory [store-atom]
  assistant/Memory
  (add-message! [this msg]
    (swap! store-atom conj msg)
    (save-to-disk! @store-atom))
  (get-messages [this]
    @store-atom)
  (clear! [this]
    (reset! store-atom [])))
```

### 2. No Built-in RAG

For document retrieval, combine with RAG:

```clojure
(require '[langchain4clj.rag.document :as rag])

(tools/deftool search-documents
  "Searches knowledge base"
  {:query string?}
  [{:keys [query]}]
  (search-vector-db query))

(def rag-assistant
  (assistant/create-assistant
    {:model model
     :tools [search-documents]}))
```

### 3. Streaming Limitations

Streaming models work but tool execution isn't streamed:

```clojure
;; Each tool call completes before streaming continues
;; Better to use non-streaming model with tools
```

## See Also

- [Tools & Function Calling](TOOLS.md) - Creating tools for assistants
- [Core Chat](README.md#core-chat) - Underlying chat functionality
- [Memory Patterns](README.md#memory) - Memory management strategies
- [Multi-Agent Systems](AGENTS.md) - Complex agent orchestration
