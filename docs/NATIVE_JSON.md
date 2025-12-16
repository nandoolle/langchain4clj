---
layout: default
title: Native JSON Mode
---

# Native JSON Mode

Force LLMs to return guaranteed valid JSON using native provider support.

## Provider Support

| Provider | JSON Mode Support |
|----------|-------------------|
| OpenAI | Full support |
| Google AI Gemini | Supported |
| Mistral AI | Supported |
| Ollama | Supported |
| Anthropic Claude | Not supported (use Structured Output) |

## Quick Start

```clojure
(require '[langchain4clj.core :as llm])
(require '[clojure.data.json :as json])
(import 'dev.langchain4j.model.chat.request.ResponseFormat)

(def model (llm/create-model {:provider :openai
                              :api-key (System/getenv "OPENAI_API_KEY")}))

(def response (llm/chat model "Return user data as JSON"
                {:response-format ResponseFormat/JSON}))

(def data (json/read-str response :key-fn keyword))
;; => {:name "John Doe" :age 30 :email "john@example.com"}
```

## Usage Methods

```clojure
;; Method 1: Direct ResponseFormat
(llm/chat model "List 3 programming languages"
  {:response-format ResponseFormat/JSON})

;; Method 2: with-json-mode helper
(llm/chat model "Return user data"
  (llm/with-json-mode {:temperature 0.7}))

;; Method 3: Threading style
(-> {:temperature 0.7}
    llm/with-json-mode
    (as-> opts (llm/chat model "Return data" opts)))
```

## Parsing Responses

```clojure
;; Using clojure.data.json
(require '[clojure.data.json :as json])
(def data (json/read-str response :key-fn keyword))

;; Using cheshire
(require '[cheshire.core :as json])
(def data (json/parse-string response true))

;; With error handling
(try
  (json/read-str response :key-fn keyword)
  (catch Exception e
    (println "JSON parsing failed:" (.getMessage e))
    nil))
```

## Prompt Engineering

```clojure
;; Specify the schema in prompt
(llm/chat model 
  "Return JSON with fields: name (string), age (integer), active (boolean)"
  {:response-format ResponseFormat/JSON})

;; Request arrays
(llm/chat model
  "Return JSON array of 3 programming languages with name and year fields"
  {:response-format ResponseFormat/JSON})

;; Nested structures
(llm/chat model
  "Return JSON with user object containing: name, contact (with email and phone)"
  {:response-format ResponseFormat/JSON})
```

## Common Use Cases

### Configuration Generation

```clojure
(def config-json
  (llm/chat model
    "Generate a database configuration with host, port, database name"
    {:response-format ResponseFormat/JSON}))

(def config (json/read-str config-json :key-fn keyword))
```

### Data Transformation

```clojure
(defn csv-to-json [csv-data]
  (llm/chat model
    (str "Convert this CSV to JSON: " csv-data)
    {:response-format ResponseFormat/JSON}))
```

## Combining with Other Features

```clojure
;; JSON Mode + Temperature
(llm/chat model "Generate creative product names"
  {:response-format ResponseFormat/JSON
   :temperature 1.0})

;; JSON Mode + System Messages
(llm/chat model "Return user data"
  {:response-format ResponseFormat/JSON
   :system-message "You are a data generator. Always use realistic values."})
```

## When to Use

**Use JSON Mode when:**
- You need guaranteed valid JSON
- Working with supported providers (OpenAI, Gemini, Ollama)
- Simple JSON structures

**Use Structured Output instead when:**
- Need schema validation
- Provider doesn't support JSON mode (Anthropic)
- Need retry logic or EDN output

## Related

- [Structured Output](STRUCTURED_OUTPUT.md) - Schema validation and retry logic
- [Core Chat](CORE_CHAT.md) - Basic chat functionality
