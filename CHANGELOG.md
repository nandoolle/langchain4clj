# Changelog

All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

## [1.3.0] - 2025-12-22

### Added
- **Chat Listeners** - Observability system for monitoring LLM interactions
  - `create-listener` for custom event handlers (on-request, on-response, on-error)
  - `logging-listener` for automatic request/response logging
  - `token-tracking-listener` for token usage statistics
  - `message-capturing-listener` for conversation history capture
  - `compose-listeners` to combine multiple listeners
  - Full EDN conversion of Java objects for Clojure-friendly data
- **Thinking/Reasoning Modes** - Extended thinking support for complex reasoning
  - OpenAI: o1/o3 models with `reasoning_effort` (low, medium, high)
  - Anthropic: Claude with `extended_thinking` and budget tokens
  - Gemini: `thinking_config` with budget tokens
  - Configurable via `:thinking` option in chat requests
- **Message Serialization** - Convert messages between Java, EDN, and JSON
  - `message->edn` / `messages->edn` for Java to EDN conversion
  - `edn->message` / `edn->messages` for EDN to Java conversion
  - `message->json` / `messages->json` for Java to JSON (LangChain4j format)
  - `json->message` / `json->messages` for JSON to Java
  - `parse-tool-arguments` for extracting tool call arguments
- **Dynamic System Message** - System message now accepts a function
  - Function receives `{:user-input ... :template-vars ...}` context
  - Enables dynamic prompts based on user input or runtime state

### Changed
- Listeners integrate with all model builders via `:listeners` option

## [1.2.0] - 2025-12-18

### Added
- Token tracking in memory using LangChain4j's TokenUsage
- Auto-reset strategy to clear memory at configurable thresholds
- Stateless mode for session isolation with context preservation
- `defmemory` macro for declarative memory configuration
- Composable memory strategies via decorator pattern

### Fixed
- Assistant duplicating AI messages when system-message is set

## [1.1.0] - 2025-11-28

### Added
- Mistral AI provider support - Added Mistral to the list of supported LLM providers
- Mistral model creation via `create-model` with `:provider :mistral`
- `mistral-model` helper function for convenient Mistral model creation
- Mistral support for agent

## [1.0.4] - 2025-11-14

### Fixed
- Added `docs/cljdoc.edn` configuration with documentation tree structure
- Core API documentation will build successfully on cljdoc
- Optional schema features (malli/schema) are documented in prose guides to avoid dependency conflicts

### Note
Optional namespaces (`tools.malli`, `tools.schema`) won't have API docs on cljdoc since their dependencies are optional.
These features are fully documented in the TOOLS.md guide instead.

## [1.0.3] - 2025-11-14

### Initial Release

LangChain4Clj - A pure Clojure wrapper for LangChain4j that provides idiomatic, unopinionated, and data-driven access to Large Language Models.

#### Core Features

**Multiple LLM Provider Support**

- OpenAI (GPT-4, GPT-4o, GPT-3.5-turbo)
- Anthropic (Claude 3.5 Sonnet, Claude 3 Opus)
- Google AI Gemini (Direct API)
- Vertex AI Gemini (Google Cloud)
- Ollama (Local models - Llama, Mistral, Gemma, CodeLlama)

**Chat Interface**

- Simple chat with string responses
- Advanced chat with full ChatResponse metadata
- System messages and temperature control
- Token limits and sampling parameters
- Native JSON mode for guaranteed valid JSON output

**Image Generation**

- DALL-E 3 support with HD quality and multiple sizes
- DALL-E 2 support for faster, cheaper generation
- Style control (vivid vs natural)
- Base64 encoding support
- Revised prompts from DALL-E 3

**Streaming Responses**

- Real-time token streaming
- Callback-based API (on-token, on-complete, on-error)
- Support for all chat providers
- No external dependencies (pure callbacks)

**Tool/Function Calling**

- `deftool` macro for simple tool definitions
- `create-tool` function for programmatic creation
- Unified support for Clojure Spec, Plumatic Schema, and Malli
- Automatic kebab-case to camelCase parameter normalization
- Schema validation for tool parameters

**Assistant System**

- Memory management with configurable history
- Automatic tool execution loops
- Template system for prompts
- Stateful conversations

**Structured Output**

- Automatic parsing with schema validation
- Retry logic for malformed responses
- Support for complex nested structures
- Integration with Spec, Schema, and Malli

**Multi-Agent Orchestration**

- Sequential agent chains
- Parallel agent execution
- Collaborative agent workflows
- Role-based agent configuration

**Provider Failover & Resilience**

- Automatic retry on rate limits and timeouts
- Fallback to alternative providers
- Circuit breaker for production environments
- Intelligent error classification
- Configurable thresholds and delays

**RAG (Retrieval-Augmented Generation)**

- Document parsing with Apache Tika
- Support for PDF, DOCX, TXT, HTML, and more
- Text extraction and chunking

#### API Design

**Data-Driven Configuration**

- 100% Clojure maps for all configuration
- Threading-first macro support
- Composable helper functions
- Sensible defaults

**Idiomatic Clojure**

- kebab-case naming throughout
- Pure functions
- No hidden state
- REPL-friendly

**Zero Opinion**

- No built-in prompts or behaviors
- No framework lock-in
- Direct access to LangChain4j features
- Users maintain full control

#### Documentation

- Comprehensive README with examples
- Installation guide
- Quick start guide
- Advanced usage examples
- Contributing guidelines

#### Testing

- 194 unit tests
- 521 assertions
- 0 failures, 0 errors
- Integration tests for real API calls

[1.1.0]: https://github.com/nandoolle/langchain4clj/releases/tag/v1.1.0
[1.0.4]: https://github.com/nandoolle/langchain4clj/releases/tag/v1.0.4
[1.0.3]: https://github.com/nandoolle/langchain4clj/releases/tag/v1.0.3
