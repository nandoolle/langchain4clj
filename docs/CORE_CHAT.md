---
layout: default
title: Core Chat
---

# Core Chat

Creating models and sending chat messages.

## Quick Start

```clojure
(require '[langchain4clj.core :as llm])

(def model (llm/create-model {:provider :openai
                               :api-key (System/getenv "OPENAI_API_KEY")}))

(llm/chat model "What is the capital of France?")
;; => "The capital of France is Paris."
```

## Creating Models

```clojure
;; OpenAI
(def openai (llm/create-model {:provider :openai
                                :api-key "sk-..."
                                :model "gpt-4o"}))

;; Anthropic Claude
(def claude (llm/create-model {:provider :anthropic
                                :api-key "sk-ant-..."
                                :model "claude-3-5-sonnet-20241022"}))

;; Google Gemini
(def gemini (llm/create-model {:provider :google-ai-gemini
                                :api-key "AIza..."
                                :model "gemini-1.5-flash"}))

;; Ollama (local)
(def ollama (llm/create-model {:provider :ollama
                                :model "llama3.1"}))

;; Mistral
(def mistral (llm/create-model {:provider :mistral
                                 :api-key "..."
                                 :model "mistral-medium-2508"}))
```

Or use provider-specific functions:

```clojure
(def model (llm/openai-model {:api-key "sk-..."}))
(def model (llm/anthropic-model {:api-key "sk-ant-..."}))
(def model (llm/ollama-model {:model "llama3.1"}))
```

## Chat Function

Simple chat:

```clojure
(llm/chat model "Hello!")
;; => "Hello! How can I help you today?"
```

With options:

```clojure
(llm/chat model "Write a haiku"
  {:temperature 0.9
   :max-tokens 100
   :system-message "You are a poet."})
```

With message history:

```clojure
(import '[dev.langchain4j.data.message UserMessage AiMessage])

(def history [(UserMessage. "My name is Alice")
              (AiMessage. "Nice to meet you, Alice!")
              (UserMessage. "What's my name?")])

(llm/chat model history {})
;; => "Your name is Alice."
```

### Options

| Option | Description |
|--------|-------------|
| `:temperature` | Creativity (0.0-2.0, default 0.7) |
| `:max-tokens` | Maximum output tokens |
| `:system-message` | System prompt |
| `:response-format` | Force JSON output |
| `:tools` | Tool specifications |

## Threading-First API

```clojure
(-> {:api-key "sk-..."}
    (llm/with-model "gpt-4o")
    (llm/with-temperature 0.8)
    (llm/with-timeout 30000)
    (llm/with-logging)
    llm/openai-model)
```

## JSON Mode

```clojure
(require '[dev.langchain4j.model.chat.request ResponseFormat])

(llm/chat model "Return user data"
  {:response-format ResponseFormat/JSON})
```

Supported by: OpenAI, Google AI Gemini, Ollama, Mistral.  
Not supported: Anthropic, Vertex AI.

## Provider Defaults

| Provider | Default Model |
|----------|---------------|
| OpenAI | gpt-4o-mini |
| Anthropic | claude-3-5-sonnet-20241022 |
| Google AI | gemini-1.5-flash |
| Ollama | llama3.1 |
| Mistral | mistral-medium-2508 |

## Related

- [Streaming](STREAMING.md) - Real-time output
- [Structured Output](STRUCTURED_OUTPUT.md) - Schema-validated responses
- [Memory](MEMORY.md) - Conversation management
- [Assistant](ASSISTANT.md) - High-level abstractions
