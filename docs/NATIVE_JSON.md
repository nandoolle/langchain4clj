---
layout: default
title: Native JSON Mode
---

# Native JSON Mode

Force LLMs to return guaranteed valid JSON output using native provider support.

## Overview

Native JSON Mode is a feature where supported LLM providers guarantee that the response will be valid JSON. This is different from prompt engineering - the provider enforces JSON at the model level, ensuring 100% reliability.

## Provider Support

| Provider | JSON Mode Support | Reliability |
|----------|-------------------|-------------|
| OpenAI | ✅ Full support | 100% guaranteed |
| Google AI Gemini | ✅ Supported | 100% guaranteed |
| Mistral AI | ✅ Supported | 100% guaranteed |
| Ollama | ✅ Supported | 100% guaranteed |
| Anthropic Claude | ❌ Not supported | Use Structured Output instead |
| Vertex AI | ❌ Not supported | Use Structured Output instead |

**Note**: When JSON mode is not supported by a provider, the option is silently ignored - the request still succeeds but may not return valid JSON.

## Quick Start

```clojure
(require '[langchain4clj.core :as llm])
(require '[clojure.data.json :as json])
(import 'dev.langchain4j.model.chat.request.ResponseFormat)

;; Create a model
(def model (llm/create-model {:provider :openai
                              :api-key (System/getenv "OPENAI_API_KEY")}))

;; Request JSON response
(def response (llm/chat model "Return user data as JSON"
                {:response-format ResponseFormat/JSON}))

;; Parse the JSON
(def data (json/read-str response :key-fn keyword))
;; => {:name "John Doe" :age 30 :email "john@example.com"}
```

## Three Ways to Use JSON Mode

### Method 1: Direct ResponseFormat

```clojure
(import 'dev.langchain4j.model.chat.request.ResponseFormat)

(llm/chat model "List 3 programming languages"
  {:response-format ResponseFormat/JSON})
;; => "{\"languages\":[\"Python\",\"JavaScript\",\"Clojure\"]}"
```

### Method 2: with-json-mode Helper

```clojure
(llm/chat model "Return user data"
  (llm/with-json-mode {:temperature 0.7}))
;; => "{\"name\":\"Alice\",\"age\":28,\"city\":\"NYC\"}"
```

### Method 3: Threading-First Style

```clojure
(-> {:temperature 0.7
     :max-tokens 500}
    llm/with-json-mode
    (as-> opts (llm/chat model "Return data" opts)))
```

## Complete Example

```clojure
(require '[langchain4clj.core :as llm])
(require '[clojure.data.json :as json])
(import 'dev.langchain4j.model.chat.request.ResponseFormat)

(def model (llm/create-model {:provider :openai
                              :api-key (System/getenv "OPENAI_API_KEY")
                              :model "gpt-4o-mini"}))

;; Request structured data
(def response (llm/chat model 
                "Generate a fictional user profile with name, age, email, and hobbies"
                {:response-format ResponseFormat/JSON}))

;; Parse and use
(def user (json/read-str response :key-fn keyword))

(println "Name:" (:name user))
(println "Age:" (:age user))
(println "Email:" (:email user))
(println "Hobbies:" (str/join ", " (:hobbies user)))
```

## Parsing JSON Responses

### Using clojure.data.json

```clojure
(require '[clojure.data.json :as json])

;; Parse with keyword keys
(def data (json/read-str response :key-fn keyword))
;; => {:name "John" :age 30}

;; Parse with string keys
(def data (json/read-str response))
;; => {"name" "John" "age" 30}
```

### Using cheshire

```clojure
(require '[cheshire.core :as json])

(def data (json/parse-string response true))
;; => {:name "John" :age 30}
```

### Error Handling

```clojure
(try
  (def response (llm/chat model "Return JSON" 
                  {:response-format ResponseFormat/JSON}))
  (json/read-str response :key-fn keyword)
  (catch Exception e
    (println "JSON parsing failed:" (.getMessage e))
    (println "Raw response:" response)
    nil))
```

## Prompt Engineering for JSON Mode

### Specify the Schema in Prompt

While JSON is guaranteed, you should still describe the structure:

```clojure
(llm/chat model 
  "Return a JSON object with these fields: name (string), age (integer), active (boolean)"
  {:response-format ResponseFormat/JSON})
```

### Request Specific Structures

```clojure
(llm/chat model
  "Return JSON array of 3 programming languages with name and year fields"
  {:response-format ResponseFormat/JSON})
;; => "[{\"name\":\"Python\",\"year\":1991},{\"name\":\"JavaScript\",\"year\":1995}...]"
```

### Complex Nested Structures

```clojure
(llm/chat model
  "Return JSON with user object containing: name, contact (with email and phone), and addresses array"
  {:response-format ResponseFormat/JSON})
```

## Common Use Cases

### Configuration Generation

```clojure
(def config-json
  (llm/chat model
    "Generate a database configuration with host, port, database name, and connection pool settings"
    {:response-format ResponseFormat/JSON}))

(def config (json/read-str config-json :key-fn keyword))
;; => {:host "localhost" :port 5432 :database "myapp" 
;;     :pool {:min-size 5 :max-size 20}}
```

### API Response Formatting

```clojure
(defn format-as-api-response [data-description]
  (let [response (llm/chat model
                   (str "Format this as an API response: " data-description)
                   {:response-format ResponseFormat/JSON})]
    (json/read-str response :key-fn keyword)))

(format-as-api-response "User list with id, name, email")
;; => {:users [{:id 1 :name "Alice" :email "alice@example.com"} ...]}
```

### Data Transformation

```clojure
(defn csv-to-json [csv-data]
  (llm/chat model
    (str "Convert this CSV to JSON: " csv-data)
    {:response-format ResponseFormat/JSON}))
```

### Schema Validation

```clojure
(require '[clojure.spec.alpha :as s])

(s/def ::name string?)
(s/def ::age pos-int?)
(s/def ::email string?)
(s/def ::user (s/keys :req-un [::name ::age ::email]))

(defn generate-user []
  (let [response (llm/chat model "Generate a user profile"
                   {:response-format ResponseFormat/JSON})
        user (json/read-str response :key-fn keyword)]
    (if (s/valid? ::user user)
      user
      (throw (ex-info "Invalid user data" {:data user})))))
```

## Combining with Other Features

### JSON Mode + Temperature Control

```clojure
(llm/chat model "Generate creative product names"
  {:response-format ResponseFormat/JSON
   :temperature 1.0
   :max-tokens 200})
```

### JSON Mode + System Messages

```clojure
(llm/chat model "Return user data"
  {:response-format ResponseFormat/JSON
   :system-message "You are a data generator. Always use realistic values."
   :temperature 0.7})
```

### JSON Mode + Streaming (Not Recommended)

JSON mode works with streaming but accumulating the complete JSON is required:

```clojure
(require '[langchain4clj.streaming :as streaming])

(let [accumulated (atom "")]
  (streaming/stream-chat model "Return user data as JSON"
    {:on-token (fn [token] (swap! accumulated str token))
     :on-complete (fn [_]
                    (let [user (json/read-str @accumulated :key-fn keyword)]
                      (println "User:" user)))})
```

**Note**: For structured output with streaming, consider using [Structured Output](STRUCTURED_OUTPUT.md) instead.

## When to Use JSON Mode

### ✅ Use JSON Mode When:

- You need guaranteed valid JSON
- Working with supported providers (OpenAI, Gemini, Ollama)
- Simple JSON structures
- Don't need complex validation
- Quick prototyping

### ❌ Use Structured Output Instead When:

- Need schema validation
- Complex nested structures
- Provider doesn't support JSON mode (Anthropic)
- Need retry logic
- Want type coercion
- Need EDN output

See [Structured Output](STRUCTURED_OUTPUT.md) for advanced use cases.

## Limitations

### 1. No Schema Enforcement

JSON mode guarantees valid JSON syntax, but not a specific schema:

```clojure
;; You ask for this structure
(llm/chat model "Return JSON with name and age"
  {:response-format ResponseFormat/JSON})

;; But might get this (still valid JSON!)
;; => "{\"user_name\":\"John\",\"age_years\":30}"
```

**Solution**: Use [Structured Output](STRUCTURED_OUTPUT.md) for schema enforcement.

### 2. Provider-Specific

Not all providers support it. Check compatibility:

```clojure
(defn supports-json-mode? [provider]
  (#{:openai :google-ai-gemini :ollama} provider))

(if (supports-json-mode? :anthropic)
  (llm/chat model prompt {:response-format ResponseFormat/JSON})
  (llm/chat model (str prompt "\nReturn valid JSON")))
```

### 3. Prompt Still Matters

You must still describe what you want in the prompt:

```clojure
;; ❌ Bad: Vague prompt
(llm/chat model "Tell me about users"
  {:response-format ResponseFormat/JSON})
;; => Might get any JSON structure

;; ✅ Good: Specific prompt
(llm/chat model "Return JSON array of users with name and email fields"
  {:response-format ResponseFormat/JSON})
```

## Best Practices

### 1. Always Specify the Structure

```clojure
(llm/chat model
  "Return JSON object with exact fields: name (string), age (integer), active (boolean)"
  {:response-format ResponseFormat/JSON})
```

### 2. Handle Parsing Errors

Even with guaranteed valid JSON, always handle parse errors:

```clojure
(defn safe-json-parse [json-str]
  (try
    (json/read-str json-str :key-fn keyword)
    (catch Exception e
      (log/error e "JSON parsing failed")
      nil)))
```

### 3. Validate After Parsing

```clojure
(defn generate-and-validate [model prompt schema-validator]
  (let [response (llm/chat model prompt 
                   {:response-format ResponseFormat/JSON})
        data (json/read-str response :key-fn keyword)]
    (if (schema-validator data)
      data
      (throw (ex-info "Schema validation failed" {:data data})))))
```

### 4. Use Descriptive Prompts

```clojure
;; ❌ Vague
(llm/chat model "User info" {:response-format ResponseFormat/JSON})

;; ✅ Specific
(llm/chat model 
  "Generate JSON with fields: firstName, lastName, age (number), email, isActive (boolean)"
  {:response-format ResponseFormat/JSON})
```

### 5. Combine with Temperature for Control

```clojure
;; Deterministic output
(llm/chat model prompt
  {:response-format ResponseFormat/JSON
   :temperature 0.0})

;; Creative output
(llm/chat model prompt
  {:response-format ResponseFormat/JSON
   :temperature 1.0})
```

## Troubleshooting

### Issue: Getting Non-JSON Responses

**Cause**: Provider doesn't support JSON mode (silently ignored)

**Solution**: Check provider compatibility or use Structured Output

```clojure
(if (#{:openai :google-ai-gemini :ollama} provider)
  ;; Use JSON mode
  (llm/chat model prompt {:response-format ResponseFormat/JSON})
  ;; Use prompt engineering
  (llm/chat model (str prompt "\nReturn only valid JSON, no other text.")))
```

### Issue: Wrong JSON Schema

**Cause**: JSON mode doesn't enforce schema, only valid JSON

**Solution**: Be very specific in prompts or use [Structured Output](STRUCTURED_OUTPUT.md)

```clojure
(llm/chat model
  "Return JSON object with EXACTLY these fields: name (string), age (integer), email (string). No additional fields."
  {:response-format ResponseFormat/JSON})
```

### Issue: JSON Wrapped in Markdown

**Cause**: Some older models or settings

**Solution**: Extract JSON from markdown:

```clojure
(defn extract-json [response]
  (if (str/includes? response "```json")
    (-> response
        (str/split #"```json")
        second
        (str/split #"```")
        first
        str/trim)
    response))

(def cleaned (extract-json response))
(json/read-str cleaned :key-fn keyword)
```

## Performance Considerations

- **Latency**: JSON mode adds minimal overhead (~5-10ms)
- **Token Usage**: Similar to non-JSON mode
- **Reliability**: 100% valid JSON (vs ~95% with prompting)
- **Cost**: Same as regular requests

## Comparison: JSON Mode vs Other Approaches

| Approach | Valid JSON | Schema | Retry | Complexity |
|----------|------------|--------|-------|------------|
| JSON Mode | ✅ 100% | ❌ No | ❌ No | ⭐ Simple |
| Prompt Engineering | ⚠️ ~95% | ❌ No | ❌ No | ⭐ Simple |
| Structured Output | ✅ 100% | ✅ Yes | ✅ Yes | ⭐⭐ Medium |
| Tool-Based Output | ✅ 100% | ✅ Yes | ❌ No | ⭐⭐⭐ Complex |

## See Also

- [Structured Output](STRUCTURED_OUTPUT.md) - Schema validation and retry logic
- [Core Chat](README.md#core-chat) - Basic chat functionality
- [Tools & Function Calling](TOOLS.md) - Another way to get structured data
