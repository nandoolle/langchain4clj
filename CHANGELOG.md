# Changelog

All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

## [1.6.0] - 2025-12-24

### Added

- **New OpenAI Model Presets** - Added 4 new OpenAI model presets for GPT-5 and O3 Pro
  - `:openai/gpt-5` - GPT-5 with thinking enabled (gpt-5-2025-08-07)
  - `:openai/gpt-5-mini` - GPT-5 Mini with thinking enabled
  - `:openai/gpt-5-nano` - GPT-5 Nano with thinking enabled
  - `:openai/o3-pro` - O3 Pro reasoning model (o3-pro-2025-06-10)

### Changed

- Total model presets now: 32 (was 28)

## [1.4.0] - 2025-12-23

### Added

- **New Model Presets** - Added 5 new Anthropic model presets for latest Claude models
  - `:anthropic/claude-haiku-4-5` - Claude Haiku 4.5 (CLAUDE_HAIKU_4_5_20251001)
  - `:anthropic/claude-sonnet-4-5` - Claude Sonnet 4.5 (CLAUDE_SONNET_4_5_20250929)
  - `:anthropic/claude-sonnet-4-5-reasoning` - Claude Sonnet 4.5 with extended thinking
  - `:anthropic/claude-opus-4-1` - Claude Opus 4.1 (CLAUDE_OPUS_4_1_20250805)
  - `:anthropic/claude-opus-4-1-reasoning` - Claude Opus 4.1 with extended thinking

### Changed

- **LangChain4j Upgrade** - Updated from 1.8.0 to 1.9.1
  - Core modules: `langchain4j-core`, `langchain4j`, `langchain4j-open-ai`, `langchain4j-anthropic`, `langchain4j-mistral-ai`, `langchain4j-google-ai-gemini`, `langchain4j-ollama` now at 1.9.1
  - Beta modules: `langchain4j-vertex-ai-gemini` and `langchain4j-document-parser-apache-tika` now at 1.9.1-beta17

### Added

- **Tool Registration Helpers** (`langchain4clj.tools.helpers`) - Bridge JSON Schema tool definitions with LangChain4j
  - `create-tool-spec` - Create ToolSpecification from JSON Schema EDN
  - `create-tool-executor` - Create ToolExecutor from Clojure functions
  - `create-safe-executor` - Error-handling executor wrapper
  - `deftool-from-schema` - Define complete tool (spec + executor) from JSON Schema
  - `tools->map` - Create ToolSpecification->ToolExecutor map for AiServices
  - `tools->specs` - Extract just specifications from definitions
  - `find-executor` - Lookup executor by tool name
  - Automatic JSON argument parsing to Clojure maps
  - Automatic result serialization (strings, JSON encoding)
  - Safe mode with customizable error handlers
- **AiServices Bridge** (`langchain4clj.tools/tools->aiservices`) - Convert `deftool` outputs to AiServices format

### Added

- **JSON Schema Converter** - Convert JSON Schema EDN to LangChain4j JsonSchema
  - `edn->json-schema` multimethod supporting all JSON Schema types
  - `json-string->json-schema` for parsing JSON strings
  - `schema-for-tool` convenience function for tool parameters
  - `any-type-schema` for dynamic value types
  - Support for: string, number, integer, boolean, array, object, enum, mixed types

### Added

- **Model Presets** - Pre-configured model settings for quick setup
  - 23 presets across OpenAI, Anthropic, and Google providers
  - Reasoning model presets with thinking enabled (o1, o3, claude-*-reasoning, gemini-*-reasoning)
  - `get-preset` function with optional overrides
  - `available-presets` and `presets-by-provider` for discovery
- **Environment Variable Resolution** - Secure configuration with `[:env "VAR"]` pattern
  - `resolve-env-refs` for manual resolution
  - `create-model` now automatically resolves env refs
  - `*env-overrides*` dynamic var for testing
  - Recursive resolution in nested maps and collections
- **Provider-Specific Options** - Full access to provider-specific builder options
  - Anthropic: `cache-system-messages`, `cache-tools`, `version`, `beta`
  - OpenAI: `organization-id`, `project-id`, `strict-tools`, `parallel-tool-calls`, `store`, `metadata`, `service-tier`
  - Google Gemini: `allow-code-execution`, `include-code-execution-output`, `response-logprobs`, `safety-settings`
- **Extended Builder Options** - Common options now available across all providers
  - `base-url` for custom endpoints
  - `top-p`, `top-k`, `seed` for fine-grained sampling control
  - `frequency-penalty`, `presence-penalty` for response variation
  - `stop-sequences` for controlled generation

### Changed

- `create-model` now automatically resolves `[:env "VAR"]` patterns in config

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

Optional namespaces (`tools.malli`, `tools.schema`, `tools.helpers`) won't have API docs on cljdoc since their dependencies are optional.
These features are fully documented in the TOOLS.md and TOOLS_HELPERS.md guides instead.

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
