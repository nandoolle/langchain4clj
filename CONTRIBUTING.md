# Contributing to LangChain4Clj

Thank you for your interest in contributing to LangChain4Clj! This document provides guidelines and standards for contributing to the project.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [Development Setup](#development-setup)
- [Code Quality Standards](#code-quality-standards)
- [Testing Guidelines](#testing-guidelines)
- [Pull Request Process](#pull-request-process)
- [Project Structure](#project-structure)

---

## Code of Conduct

This project adheres to a code of conduct that all contributors are expected to follow. Please be respectful, inclusive, and constructive in all interactions.

---

## Getting Started

### Prerequisites

- **Clojure CLI** (version 1.11.1 or higher)
- **Java** (version 11 or higher)
- **clj-kondo** (for linting)
- **Git**

### Fork and Clone

```bash
# Fork the repository on GitHub, then clone your fork
git clone https://github.com/YOUR_USERNAME/langchain4clj.git
cd langchain4clj

# Add upstream remote
git remote add upstream https://github.com/langchain4clj.git
```

---

## Development Setup

### Running Tests

```bash
# Run all tests
clojure -M:test

# Run specific namespace
clojure -M:test -n langchain4clj.core-test

# Run with all schema libraries
clojure -M:test:with-all-schemas
```

### REPL Development

```bash
# Start nREPL server
clojure -M:nrepl

# Or use your editor's REPL integration
```

### Linting

```bash
# Lint all source and test files
clj-kondo --lint src test

# Lint with auto-fix (where possible)
clj-kondo --lint src test --config '{:output {:pattern "::{{level}} {{message}}"}}'
```

---

## Code Quality Standards

All contributions must meet these quality standards before being merged.

### Before Committing

**Mandatory Checks:**

1. **All tests pass**
   ```bash
   clojure -M:test
   ```
   Expected: `0 failures, 0 errors`

2. **No linting errors**
   ```bash
   clj-kondo --lint src test
   ```
   Expected: 0 errors (warnings are acceptable with justification)

3. **Code is formatted**
   - Follow Clojure style conventions
   - Use consistent indentation (2 spaces)
   - No trailing whitespace

### Import Guidelines

**Do:**
- Remove unused imports immediately
- Group imports logically (Java first, then Clojure libs)
- Use specific imports for clarity

**Don't:**
- Leave unused imports
- Use wildcard imports (`import *`)
- Import classes you don't use

**Example:**
```clojure
;; BAD: Unused imports
(:import [dev.langchain4j.model.chat ChatModel ChatRequest]
         [dev.langchain4j.data.message AiMessage]) ;; AiMessage not used

;; GOOD: Only what's needed
(:import [dev.langchain4j.model.chat ChatModel]
         [dev.langchain4j.model.chat.request ChatRequest])
```

### Binding Guidelines

**Unused bindings** should be prefixed with `_` to show intent:

```clojure
;; BAD: Unused binding without indication
(defn process [{:keys [name age email]}]
  (str "Hello " name))  ;; age and email unused

;; GOOD: Indicate intentionally unused
(defn process [{:keys [name _age _email]}]
  (str "Hello " name))
```

**Destructuring** should only include what's used:

```clojure
;; BAD: Destructure everything
(let [{:keys [a b c d e]} config]
  (+ a b))  ;; c, d, e unused

;; GOOD: Only what's needed
(let [{:keys [a b]} config]
  (+ a b))
```

### Function Guidelines

**Public functions** must have:
- Docstring explaining purpose
- Parameter descriptions
- Return value description
- Usage examples (if complex)

**Example:**
```clojure
(defn create-model
  "Creates a chat model from configuration.
  
  Parameters:
  - config: Map with :provider, :api-key, and optional settings
  
  Returns:
  - ChatModel instance configured with the specified provider
  
  Example:
  (create-model {:provider :openai
                 :api-key \"sk-...\"
                 :model \"gpt-4\"})"
  [config]
  (build-model config))
```

### Namespace Guidelines

**Namespace declarations** should:
- Have descriptive docstring
- Group requires logically
- Use consistent aliasing (`as m`, `as str`, etc.)
- Remove unused requires immediately

**Example:**
```clojure
(ns langchain4clj.core
  "Core wrapper functions for LangChain4j.
   
   Provides idiomatic Clojure interface for chat models,
   supporting OpenAI, Anthropic, Google Gemini, and Ollama."
  (:require [langchain4clj.macros :as macros])
  (:import [dev.langchain4j.model.chat ChatModel]
           [dev.langchain4j.model.openai OpenAiChatModel]
           [java.time Duration]))
```

### TODO and FIXME Guidelines

**Don't leave TODOs in code.** Instead:

1. **Create a GitHub issue** for the task
2. **Reference the issue** in comments if needed
3. **Remove the TODO** from code

```clojure
;; BAD: TODO without tracking
;; TODO: Add support for streaming

;; GOOD: Issue reference
;; Streaming support tracked in #42
```

### Error Handling

**Always handle errors gracefully:**

```clojure
;; GOOD: Descriptive error messages
(when-not api-key
  (throw (IllegalArgumentException. 
          ":api-key is required for OpenAI provider")))

;; GOOD: Validate inputs
(defn chat [model message]
  {:pre [(some? model) (string? message)]}
  (.chat model message))
```

---

## Testing Guidelines

### Test Organization

Tests should mirror the source structure:

```
src/langchain4clj/core.clj
→ test/langchain4clj/core_test.clj
```

### Test Quality

**Every test must:**
- Have clear `testing` descriptions
- Test one thing
- Be independent (no shared state)
- Be deterministic (no flaky tests)

**Example:**
```clojure
(deftest test-create-model
  (testing "Creates OpenAI model with defaults"
    (let [model (core/create-model {:provider :openai 
                                    :api-key "test"})]
      (is (some? model))
      (is (instance? OpenAiChatModel model))))
  
  (testing "Throws on missing API key"
    (is (thrown? IllegalArgumentException
                 (core/create-model {:provider :openai})))))
```

### Test Coverage

**Aim for:**
- All public functions tested
- Happy path + error cases
- Edge cases (nil, empty, large inputs)

---

## Pull Request Process

### 1. Create a Feature Branch

```bash
git checkout -b feature/your-feature-name
# or
git checkout -b fix/your-bug-fix
```

### 2. Make Changes

- Write code following the standards above
- Add tests for new functionality
- Update documentation if needed

### 3. Validate Locally

```bash
# Run tests
clojure -M:test

# Run linter
clj-kondo --lint src test

# Check that you can build
clojure -T:build clean
clojure -T:build jar
```

### 4. Commit with Clear Messages

```bash
# GOOD: Clear, imperative commit messages
git commit -m "Add streaming support for Anthropic models"
git commit -m "Fix memory leak in chat memory"
git commit -m "Update README with new examples"

# BAD: Vague messages
git commit -m "Fix stuff"
git commit -m "WIP"
```

### 5. Push and Create PR

```bash
git push origin feature/your-feature-name
```

Then create a Pull Request on GitHub:

> **Important:** All PRs should target the `preview` branch, not `main`.
> The `preview` branch is used for integration and testing before releases.
> Changes are merged to `main` only when preparing a new release.

PR should include:
- **Clear title** describing the change
- **Description** explaining what and why
- **Link to issue** if applicable
- **Screenshots** if UI-related

### 6. PR Checklist

Before requesting review, ensure:

- [ ] All tests pass (`clojure -M:test`)
- [ ] No linting errors (`clj-kondo --lint src test`)
- [ ] Documentation updated if needed
- [ ] CHANGELOG.md updated (for user-facing changes)
- [ ] Commit messages are clear
- [ ] No unnecessary changes (formatting, whitespace)

---

## Project Structure

```
langchain4clj/
├── src/
│   └── nandoolle/
│       └── langchain4clj/
│           ├── core.clj           # Core model creation
│           ├── streaming.clj      # Streaming support
│           ├── structured.clj     # Structured output
│           ├── tools.clj          # Tool/function calling
│           ├── agents.clj         # Agent support
│           ├── assistant.clj      # High-level assistant API
│           ├── resilience.clj     # Failover and retries
│           ├── macros.clj         # Internal macros
│           ├── rag/
│           │   └── document.clj   # RAG document support
│           └── tools/
│               ├── protocols.clj  # Tool protocols
│               ├── spec.clj       # Clojure Spec support
│               ├── schema.clj     # Plumatic Schema support
│               └── malli.clj      # Malli support
├── test/
│   └── nandoolle/
│       └── langchain4clj/
│           └── *_test.clj         # Tests mirror src structure
├── examples/
│   └── *.clj                      # Usage examples
├── docs/
│   ├── README.md                  # Main documentation
│   └── development/
│       └── *.md                   # Development guides
├── deps.edn                       # Dependencies
├── CHANGELOG.md                   # User-facing changes
└── CONTRIBUTING.md                # This file
```

---

## Architecture Principles

### 1. Idiomatic Clojure

- **Use threading macros** (`->`, `->>`) for readability
- **Prefer pure functions** over stateful code
- **Use protocols** for extensibility
- **Leverage multimethods** for dispatch

### 2. Java Interop

- **Wrap all Java APIs** with Clojure functions
- **Convert Java objects** to Clojure data structures
- **Use type hints** to avoid reflection
- **Handle Java exceptions** and convert to Clojure idioms

### 3. Backwards Compatibility

- **Don't break public APIs** without major version bump
- **Deprecate before removing** (at least one minor version)
- **Document breaking changes** clearly

---

## Getting Help

- **Documentation**: Check [docs/README.md](docs/README.md)
- **Examples**: See [examples/](examples/) directory
- **Issues**: Search existing [GitHub Issues](https://github.com/langchain4clj/issues)
- **Discussions**: Ask questions in [GitHub Discussions](https://github.com/langchain4clj/discussions)

---

## License

By contributing to LangChain4Clj, you agree that your contributions will be licensed under the same license as the project.

---

## Recognition

Contributors are recognized in:
- Git history
- CHANGELOG.md (for significant contributions)
- GitHub contributors page

Thank you for contributing! 
