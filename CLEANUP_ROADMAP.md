# Documentation & Comments Cleanup Roadmap

This roadmap addresses feedback about AI-generated patterns in our codebase and documentation.

## Problem Statement

External feedback highlighted:
- Code comments feel machine-generated rather than human-written
- Excessive separator comments (`;;===...`) throughout source files
- Overly verbose docstrings that explain obvious things
- Documentation with unnatural structure (too many tables, emojis, verbose language)

## Identified Patterns to Clean Up

### Source Code Comments

| Pattern | Example | Action |
|---------|---------|--------|
| Heavy separator lines | `;; ============================================================================` | Remove entirely |
| Section headers | `;; Core Chat Functions` | Keep only when necessary, simplify |
| Obvious comments | `;; Converts milliseconds to Java Duration` | Remove |
| Over-explained docstrings | Multi-paragraph explanations for simple functions | Trim to essentials |

### Documentation Files

| Pattern | Example | Action |
|---------|---------|--------|
| Excessive tables | Tables for 2-3 items | Convert to simple lists |
| Emoji overuse | Headers with emojis | Remove emojis |
| Verbose language | "This comprehensive module provides..." | Direct language |
| Redundant sections | "Quick Reference" + "API Reference" with same content | Consolidate |

## Files Requiring Attention

### High Priority (Source Code) - DONE

All source files have been cleaned:
- `tools.clj` - Removed separators, trimmed docstrings
- `streaming.clj` - Removed separators, simplified
- `core.clj` - Cleaned comments and docstrings
- `assistant.clj` - Removed separators, simplified
- `agents.clj` - Removed separators, trimmed docstrings
- `image.clj` - Removed separators, simplified
- `structured.clj` - Removed separators, trimmed docstrings

### Medium Priority (Documentation) - DONE

All documentation files have been cleaned:
- `CORE_CHAT.md` - Removed TOC, simplified provider reference
- `MEMORY.md` - Reduced patterns, simplified guidelines
- `STREAMING.md` - Removed emojis, Best Practices, Performance sections
- `STRUCTURED_OUTPUT.md` - Removed verbose Overview, Troubleshooting
- `ASSISTANT.md` - Removed verbose patterns, Limitations section
- `NATIVE_JSON.md` - Simplified to essentials
- `IMAGE.md` - Removed prompt tips, rate limits details
- `TOOLS.md` - Removed TOC, Migration Guide, extensive examples
- `RAG.md` - Removed Best Practices, Performance Optimization
- `AGENTS.md` - Removed verbose use cases, Error Handling
- `RESILIENCE.md` - Major reduction, kept only essential config

### Low Priority

1. **README.md** - Main entry point, keep professional
2. **docs/index.md** - Landing page

## Cleanup Guidelines

### For Code Comments

**Before:**
```clojure
;; ============================================================================
;; Core Chat Functions
;; ============================================================================

(defn create-model
  "Creates a new chat model instance with the specified configuration.
   
   This function is the primary entry point for creating chat models
   in the langchain4clj library. It supports multiple providers and
   handles all the underlying configuration automatically.
   
   Parameters:
   - config: A map containing the model configuration
   
   Returns:
   - A chat model instance ready for use"
  [config]
  ...)
```

**After:**
```clojure
(defn create-model
  "Creates a chat model from config map.
   See README for supported providers and options."
  [config]
  ...)
```

### For Documentation

**Before:**
> ## 🚀 Getting Started
> 
> This comprehensive guide will walk you through the process of setting up
> and configuring your first chat model using the langchain4clj library.
> 
> | Step | Description | Time Required |
> |------|-------------|---------------|
> | 1 | Install dependencies | 2 minutes |
> | 2 | Configure API keys | 1 minute |

**After:**
> ## Getting Started
> 
> 1. Add dependency to `deps.edn`
> 2. Set your API key
> 3. Create a model and chat

## Implementation Plan

### Phase 1: Source Code (v1.1.1) - COMPLETE

- [x] Clean `tools.clj` - remove separators, trim docstrings
- [x] Clean `streaming.clj` - remove separators, trim docstrings
- [x] Clean `core.clj` - review all comments
- [x] Clean `assistant.clj` - review all comments
- [x] Clean `agents.clj` - remove separators, trim docstrings
- [x] Clean `image.clj` - remove separators, trim docstrings
- [x] Clean `structured.clj` - remove separators, trim docstrings

### Phase 2: Documentation (v1.1.2) - COMPLETE

- [x] Review and trim `CORE_CHAT.md`
- [x] Review and trim `MEMORY.md`
- [x] Review and trim `STREAMING.md`
- [x] Review and trim other docs (STRUCTURED_OUTPUT, ASSISTANT, NATIVE_JSON, IMAGE, TOOLS, RAG, AGENTS, RESILIENCE)
- [x] Update README if needed

### Phase 3: Establish Standards (v1.2.0)

- [ ] Add CONTRIBUTING.md with comment guidelines
- [ ] Document preferred docstring style
- [ ] Review all new PRs for AI-patterns

## Comment Style Guide (Proposed)

### Do

- Write comments for "why", not "what"
- Keep docstrings to 1-2 lines for simple functions
- Use `^:private` instead of commenting about internal use

### Don't

- Add separator lines between sections
- Explain obvious operations
- Use filler words ("This function...", "The purpose of...")
- Add "Parameters:" and "Returns:" sections for simple functions

## Success Criteria

- No separator comment lines in source
- Docstrings under 3 lines for simple functions
- Documentation readable without scrolling past boilerplate
- Code speaks for itself with minimal annotation
