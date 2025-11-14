---
layout: default
title: Home
---

# LangChain4Clj

A Clojure wrapper for [LangChain4j](https://github.com/langchain4j/langchain4j) - Build powerful AI applications with the simplicity and elegance of Clojure.

## Quick Start

Add to your `deps.edn`:

```clojure
{:deps {io.github.nandoolle/langchain4clj {:mvn/version "1.0.3"}}}
```

Or to your Leiningen `project.clj`:

```clojure
[io.github.nandoolle/langchain4clj "1.0.3"]
```

## Simple Example

```clojure
(require '[langchain4clj.chat :as chat])

(def model (chat/create-chat-model 
             {:model-name "gpt-4"
              :api-key (System/getenv "OPENAI_API_KEY")}))

(chat/chat model "What is the capital of France?")
;; => "The capital of France is Paris."
```

## Core Features

Explore the fundamental capabilities of LangChain4Clj:

- **[Streaming Responses](STREAMING.html)** - Real-time streaming with callbacks and error handling
- **[Image Generation](IMAGE.html)** - DALL-E 2/3 integration with quality and style controls
- **[Native JSON Mode](NATIVE_JSON.html)** - Provider-guaranteed valid JSON output
- **[Structured Output](STRUCTURED_OUTPUT.html)** - Schema-validated responses with multiple strategies

## Advanced Features

Build sophisticated AI applications:

- **[Tools & Function Calling](TOOLS.html)** - Extend LLM capabilities with custom functions
- **[Assistant System](ASSISTANT.html)** - Memory management and autonomous tool execution
- **[Multi-Agent Systems](AGENTS.html)** - Orchestrate multiple agents for complex workflows
- **[RAG (Document Processing)](RAG.html)** - Load, parse, and split documents for retrieval-augmented generation
- **[Resilience & Failover](RESILIENCE.html)** - Automatic retries and provider fallback strategies

## Why LangChain4Clj?

- **Idiomatic Clojure** - Leverages Clojure's strengths: immutability, data-first design, and composability
- **Comprehensive** - Full access to LangChain4j's powerful features
- **Type-Safe** - Malli and Schema support for request/response validation
- **Production-Ready** - Built-in resilience, failover, and error handling
- **Well-Documented** - Extensive guides and examples

## Provider Support

LangChain4Clj supports all major AI providers:

- **OpenAI** (GPT-4, GPT-3.5, DALL-E)
- **Anthropic** (Claude 3.5 Sonnet, Claude 3 Opus/Haiku)
- **Google** (Gemini Pro/Flash)
- **Azure OpenAI**
- **Ollama** (Local models)
- And many more...

## Getting Help

- **[GitHub Repository](https://github.com/nandoolle/langchain4clj)** - Source code, issues, and contributions
- **[Changelog](https://github.com/nandoolle/langchain4clj/blob/main/CHANGELOG.md)** - Version history and updates
- **[Contributing](https://github.com/nandoolle/langchain4clj/blob/main/CONTRIBUTING.md)** - Guidelines for contributors

## License

Copyright © 2024 Fernando Olle

Distributed under the Apache License 2.0.
