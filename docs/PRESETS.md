# Model Presets

langchain4clj provides a collection of pre-configured model presets that simplify model creation with sensible defaults.

## Overview

Presets are namespaced keywords that identify specific model configurations:

- `:openai/gpt-4o` - OpenAI GPT-4o
- `:anthropic/claude-sonnet-4` - Anthropic Claude Sonnet 4
- `:google/gemini-2-5-flash` - Google Gemini 2.5 Flash

## Usage

```clojure
(require '[langchain4clj.presets :as presets])
(require '[langchain4clj.core :as lc])

;; Get a preset and create a model
(def model 
  (lc/create-model 
    (presets/get-preset :openai/gpt-4o 
                        {:api-key "sk-..."})))

;; Override any preset values
(def custom-model
  (lc/create-model
    (presets/get-preset :anthropic/claude-sonnet-4
                        {:api-key "sk-ant-..."
                         :temperature 0.5
                         :max-tokens 2000})))
```

## Available Presets

### OpenAI Models

| Preset | Model | Description |
|--------|-------|-------------|
| `:openai/gpt-4o` | gpt-4o | Latest GPT-4o model |
| `:openai/gpt-4o-mini` | gpt-4o-mini | Smaller, faster GPT-4o |
| `:openai/gpt-4-1` | gpt-4.1 | GPT-4.1 |
| `:openai/gpt-4-1-mini` | gpt-4.1-mini | GPT-4.1 Mini |
| `:openai/gpt-4-1-nano` | gpt-4.1-nano | GPT-4.1 Nano |
| `:openai/o1` | o1 | OpenAI o1 reasoning model |
| `:openai/o1-mini` | o1-mini | OpenAI o1-mini reasoning model |
| `:openai/o3` | o3 | OpenAI o3 reasoning model |
| `:openai/o3-mini` | o3-mini | OpenAI o3-mini reasoning model |
| `:openai/o4-mini` | o4-mini | OpenAI o4-mini |
| `:openai/o4-mini-reasoning` | o4-mini | o4-mini with reasoning enabled |

### Anthropic Models

| Preset | Model | Description |
|--------|-------|-------------|
| `:anthropic/claude-3-5-haiku` | claude-3-5-haiku | Fast, efficient model |
| `:anthropic/claude-haiku-4-5` | claude-haiku-4-5 | Latest Haiku - fast & capable |
| `:anthropic/claude-sonnet-4` | claude-sonnet-4 | Balanced performance |
| `:anthropic/claude-sonnet-4-reasoning` | claude-sonnet-4 | With extended thinking |
| `:anthropic/claude-sonnet-4-5` | claude-sonnet-4-5 | Latest Sonnet - enhanced |
| `:anthropic/claude-sonnet-4-5-reasoning` | claude-sonnet-4-5 | With extended thinking |
| `:anthropic/claude-opus-4` | claude-opus-4 | Most capable model |
| `:anthropic/claude-opus-4-reasoning` | claude-opus-4 | With extended thinking |
| `:anthropic/claude-opus-4-1` | claude-opus-4-1 | Enhanced Opus |
| `:anthropic/claude-opus-4-1-reasoning` | claude-opus-4-1 | With extended thinking |

### Google Gemini Models

| Preset | Model | Description |
|--------|-------|-------------|
| `:google/gemini-1-5-flash` | gemini-1.5-flash | Fast Gemini 1.5 |
| `:google/gemini-1-5-pro` | gemini-1.5-pro | Capable Gemini 1.5 |
| `:google/gemini-2-5-flash` | gemini-2.5-flash | Latest Gemini 2.5 Flash |
| `:google/gemini-2-5-flash-lite` | gemini-2.5-flash-lite | Lighter Gemini 2.5 |
| `:google/gemini-2-5-pro` | gemini-2.5-pro | Most capable Gemini |
| `:google/gemini-2-5-flash-reasoning` | gemini-2.5-flash | With thinking enabled |
| `:google/gemini-2-5-pro-reasoning` | gemini-2.5-pro | With thinking enabled |

## Preset Configuration

Each preset includes sensible defaults:

### Standard Models

```clojure
{:temperature 1.0
 :max-tokens 4096
 :max-retries 3
 :timeout 60000}
```

### Reasoning Models

```clojure
{:temperature 1.0
 :max-tokens 8192
 :max-retries 3
 :timeout 120000
 :thinking {:enabled true
            :return true
            :send true
            :effort :medium}}
```

## API Reference

### `get-preset`

Get a preset configuration, optionally with overrides.

```clojure
(presets/get-preset :openai/gpt-4o)
;; => {:provider :openai :model "gpt-4o" :temperature 1.0 ...}

(presets/get-preset :openai/gpt-4o {:temperature 0.5})
;; => {:provider :openai :model "gpt-4o" :temperature 0.5 ...}
```

### `available-presets`

List all available preset keys.

```clojure
(presets/available-presets)
;; => (:openai/gpt-4o :openai/gpt-4o-mini :anthropic/claude-sonnet-4 ...)
```

### `presets-by-provider`

Group presets by provider.

```clojure
(presets/presets-by-provider)
;; => {:openai [:openai/gpt-4o :openai/gpt-4o-mini ...]
;;     :anthropic [:anthropic/claude-sonnet-4 ...]
;;     :google [:google/gemini-2-5-flash ...]}
```

### `get-provider`

Extract the provider from a preset key.

```clojure
(presets/get-provider :openai/gpt-4o)
;; => :openai
```

## Environment Variables

Presets work seamlessly with environment variable resolution:

```clojure
(lc/create-model
  (presets/get-preset :openai/gpt-4o
                      {:api-key [:env "OPENAI_API_KEY"]}))
```

See [ENV_RESOLUTION.md](ENV_RESOLUTION.md) for more details.
