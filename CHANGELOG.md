# Changelog

All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

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

[1.0.4]: https://github.com/nandoolle/langchain4clj/releases/tag/v1.0.4
[1.0.3]: https://github.com/nandoolle/langchain4clj/releases/tag/v1.0.3
