---
layout: default
title: Structured Output
---

# Structured Output

Get guaranteed structured responses in JSON or EDN format with schema validation and automatic retry logic.

## Overview

Structured Output provides multiple strategies to ensure LLMs return data in a specific format:

1. **Native JSON Mode** - Provider-guaranteed valid JSON (OpenAI, Gemini)
2. **Tool-Based Output** - Function calling to structure output
3. **Validated Prompting** - Iterative validation with retries

The library automatically selects the best strategy based on provider capabilities, or you can choose explicitly.

## Quick Start

```clojure
(require '[langchain4clj.structured :as structured])

;; Define a schema
(def user-schema
  {:name :string
   :age :int
   :email :string
   :active :boolean})

;; Get structured output (auto-selects best strategy)
(def user (structured/structured-output model
            "Generate a fictional user profile"
            {:schema user-schema}))

;; Returns Clojure data
user
;; => {:name "Alice Smith"
;;     :age 28
;;     :email "alice@example.com"
;;     :active true}
```

## Schema Definition

### Simple Types

```clojure
{:name :string
 :age :int
 :price :float
 :active :boolean}
```

### Collections

```clojure
{:tags [:vector :string]
 :scores [:vector :int]
 :metadata [:map :string :any]}
```

### Nested Objects

```clojure
{:user {:name :string
        :email :string}
 :address {:street :string
          :city :string
          :zip :int}}
```

### Complex Schema

```clojure
(def product-schema
  {:id :int
   :name :string
   :price :float
   :tags [:vector :string]
   :inventory {:warehouse :string
               :quantity :int
               :last-updated :string}
   :reviews [:vector {:rating :int
                      :comment :string
                      :verified :boolean}]})
```

## Output Formats

### EDN (Default)

Returns Clojure data structures:

```clojure
(structured/structured-output model prompt
  {:schema schema
   :output-format :edn})  ;; default

;; => {:name "John" :age 30}  (Clojure map)
```

### JSON String

Returns JSON string:

```clojure
(structured/structured-output model prompt
  {:schema schema
   :output-format :json})

;; => "{\"name\":\"John\",\"age\":30}"  (JSON string)
```

### JSON (Alias)

Same as `:json`:

```clojure
(structured/structured-output model prompt
  {:schema schema
   :output-format :json-str})

;; => "{\"name\":\"John\",\"age\":30}"
```

## Strategies

### Auto Strategy (Recommended)

Let the library choose the best approach:

```clojure
(structured/structured-output model prompt
  {:schema schema})  ;; Automatically picks best strategy
```

**Selection Logic:**
1. If provider supports JSON mode → use JSON mode
2. Else if model supports tools → use tool-based
3. Else → use validated prompting

### JSON Mode Strategy

Uses native provider JSON support (OpenAI, Gemini):

```clojure
(structured/structured-output model prompt
  {:schema schema
   :strategy :json-mode})
```

**Pros:**
- Fast
- 100% valid JSON
- Low complexity

**Cons:**
- Requires provider support
- No automatic schema validation

### Tool-Based Strategy

Uses function calling to structure output:

```clojure
(structured/structured-output model prompt
  {:schema schema
   :strategy :tools})
```

**Pros:**
- Works with all tool-capable providers
- Schema-aware
- Reliable

**Cons:**
- Slightly slower
- Requires tool support

### Validation Strategy

Prompts for JSON, validates, retries on failure:

```clojure
(structured/structured-output model prompt
  {:schema schema
   :strategy :validation
   :max-retries 3})
```

**Pros:**
- Works with any provider
- Validates schema
- Can retry

**Cons:**
- Slowest
- May fail after max retries
- Uses more tokens

## Convenience Functions

### EDN Mode

Get EDN output directly:

```clojure
(structured/chat-edn-mode model prompt
  :schema {:name :string :age :int})

;; => {:name "Alice" :age 30}  (EDN map)
```

### JSON Mode

Get JSON output directly:

```clojure
(structured/chat-json-mode model prompt
  :schema {:name :string :age :int}
  :output-format :json)

;; => "{\"name\":\"Alice\",\"age\":30}"
```

### Tool-Based Output

Explicitly use tools:

```clojure
(structured/chat-with-output-tool model prompt schema
  :output-format :edn)
```

### Validation Output

Explicitly use validation:

```clojure
(structured/chat-with-validation model prompt schema
  :max-retries 3
  :output-format :edn)
```

## Reusable Structured Types

### defstructured Macro

Define reusable structured output types:

```clojure
(structured/defstructured Person
  {:name :string
   :age :int
   :email :string
   :interests [:vector :string]})
```

**This creates two functions:**

```clojure
;; get-person - Returns EDN
(get-person model "Generate a fictional person")
;; => {:name "Bob" :age 35 :email "bob@example.com" 
;;     :interests ["reading" "coding"]}

;; get-person-json - Returns JSON
(get-person-json model "Generate a fictional person")
;; => "{\"name\":\"Bob\",\"age\":35,...}"
```

### Multiple Types

```clojure
(structured/defstructured User
  {:username :string
   :email :string
   :role :string})

(structured/defstructured Product
  {:id :int
   :name :string
   :price :float
   :tags [:vector :string]})

(structured/defstructured Order
  {:order-id :string
   :user User  ;; Reference to User schema
   :products [:vector Product]
   :total :float})

;; Use them
(get-user model "Create an admin user")
(get-product model "Create a laptop product")
(get-order model "Create an order for the user")
```

## Common Use Cases

### Extract Structured Data from Text

```clojure
(def resume-schema
  {:name :string
   :email :string
   :phone :string
   :skills [:vector :string]
   :experience [:vector {:company :string
                         :role :string
                         :years :int}]})

(def resume-text "John Doe, john@example.com, 555-1234...")

(structured/structured-output model
  (str "Extract information from this resume: " resume-text)
  {:schema resume-schema})
```

### Generate Test Data

```clojure
(structured/defstructured TestUser
  {:id :int
   :username :string
   :email :string
   :created-at :string})

;; Generate multiple test users
(repeatedly 5 #(get-test-user model "Generate a random user"))
```

### API Response Formatting

```clojure
(def api-response-schema
  {:status :string
   :data {:users [:vector {:id :int :name :string}]}
   :meta {:page :int :total :int}})

(structured/structured-output model
  "Generate an API response with 3 users"
  {:schema api-response-schema})
```

### Configuration Generation

```clojure
(def config-schema
  {:database {:host :string
              :port :int
              :name :string}
   :cache {:enabled :boolean
           :ttl :int}
   :logging {:level :string
             :format :string}})

(structured/structured-output model
  "Generate a production database configuration"
  {:schema config-schema})
```

### Data Transformation

```clojure
(def csv-data "name,age,city\nAlice,30,NYC\nBob,25,SF")

(structured/structured-output model
  (str "Convert this CSV to structured data: " csv-data)
  {:schema {:users [:vector {:name :string
                             :age :int
                             :city :string}]}})
```

## Validation

### Schema Validation

Automatically validates against schema:

```clojure
(structured/structured-output model prompt
  {:schema {:age :int}
   :validate? true})  ;; default

;; If LLM returns {:age "thirty"}, validation fails and retries
```

### Disable Validation

```clojure
(structured/structured-output model prompt
  {:schema schema
   :validate? false})  ;; Skip validation
```

### Custom Validation

```clojure
(require '[clojure.spec.alpha :as s])

(s/def ::email (s/and string? #(re-matches #".+@.+" %)))
(s/def ::age (s/and int? #(> % 0) #(< % 150)))
(s/def ::user (s/keys :req-un [::email ::age]))

(defn generate-valid-user []
  (let [user (structured/structured-output model "Generate a user"
               {:schema {:email :string :age :int}})]
    (if (s/valid? ::user user)
      user
      (throw (ex-info "Invalid user" {:user user})))))
```

## Error Handling

### Max Retries Exceeded

```clojure
(try
  (structured/structured-output model prompt
    {:schema complex-schema
     :strategy :validation
     :max-retries 3})
  (catch Exception e
    (println "Failed to generate valid output after retries")
    (use-fallback-data)))
```

### Invalid Schema

```clojure
(try
  (structured/structured-output model prompt
    {:schema {:invalid-type :unknown}})
  (catch Exception e
    (println "Schema error:" (.getMessage e))))
```

### Provider Not Supported

```clojure
;; If using :json-mode with unsupported provider
(try
  (structured/structured-output anthropic-model prompt
    {:schema schema
     :strategy :json-mode})  ;; Anthropic doesn't support JSON mode
  (catch Exception e
    ;; Falls back to other strategy or fails
    (println "Strategy not supported")))
```

## Type Conversion Utilities

### EDN ↔ JSON Conversion

```clojure
;; EDN to JSON string
(structured/edn->json-str {:name "Alice" :age 30})
;; => "{\"name\":\"Alice\",\"age\":30}"

;; JSON string to EDN
(structured/json-str->edn "{\"name\":\"Alice\",\"age\":30}")
;; => {:name "Alice" :age 30}
```

## Performance Considerations

### Strategy Performance

| Strategy | Speed | Reliability | Token Usage |
|----------|-------|-------------|-------------|
| JSON Mode | ⭐⭐⭐ Fast | ⭐⭐⭐ High | ⭐⭐⭐ Low |
| Tool-Based | ⭐⭐ Medium | ⭐⭐⭐ High | ⭐⭐ Medium |
| Validation | ⭐ Slow | ⭐⭐ Medium | ⭐ High (retries) |

### Recommendations

```clojure
;; For simple schemas with OpenAI
{:strategy :json-mode}

;; For complex schemas with any provider
{:strategy :tools}

;; For maximum compatibility
{:strategy :validation :max-retries 2}

;; Let library decide (recommended)
{}  ;; Auto-selects best strategy
```

## Best Practices

### 1. Start Simple, Add Complexity

```clojure
;; ❌ Too complex upfront
{:user {:profile {:settings {:preferences {:theme {:colors {...}}}}}}}

;; ✅ Build incrementally
{:name :string :email :string}  ;; Start here
{:name :string :email :string :settings {...}}  ;; Add as needed
```

### 2. Use Descriptive Prompts

```clojure
;; ❌ Vague
(structured/structured-output model "Generate data" {:schema schema})

;; ✅ Specific
(structured/structured-output model
  "Generate a realistic user profile for a software engineer in their 30s"
  {:schema schema})
```

### 3. Define Reusable Types

```clojure
;; ✅ Good for frequently used schemas
(structured/defstructured User {...})
(structured/defstructured Product {...})
(structured/defstructured Order {...})

;; Use everywhere
(get-user model "Generate admin")
(get-product model "Create laptop")
```

### 4. Handle Errors Gracefully

```clojure
(defn safe-structured-output [model prompt schema]
  (try
    (structured/structured-output model prompt {:schema schema})
    (catch Exception e
      (log/error e "Structured output failed")
      (generate-default-data schema))))
```

### 5. Validate Critical Data

```clojure
(require '[clojure.spec.alpha :as s])

(defn generate-and-validate [model prompt schema spec]
  (let [data (structured/structured-output model prompt {:schema schema})]
    (if (s/valid? spec data)
      data
      (throw (ex-info "Validation failed" {:data data})))))
```

## Comparison: Structured Output vs Alternatives

| Approach | Valid JSON | Schema | Retry | Use Case |
|----------|------------|--------|-------|----------|
| Structured Output | ✅ Yes | ✅ Yes | ✅ Yes | Complex structured data |
| Native JSON Mode | ✅ Yes | ❌ No | ❌ No | Simple JSON responses |
| Tool Calling | ✅ Yes | ✅ Yes | ❌ No | Function parameters |
| Prompt Engineering | ⚠️ ~95% | ❌ No | ❌ No | Quick prototypes |

## Configuration Reference

```clojure
(structured/structured-output model prompt
  {:schema schema              ;; Required: Schema definition
   :strategy :auto             ;; Optional: :auto, :json-mode, :tools, :validation
   :output-format :edn         ;; Optional: :edn, :json, :json-str
   :validate? true             ;; Optional: Validate against schema
   :max-retries 3              ;; Optional: For validation strategy
   :temperature 0.7})          ;; Optional: Model temperature
```

## Troubleshooting

### Issue: Getting Wrong Schema

**Solution**: Be more specific in prompt

```clojure
(structured/structured-output model
  "Generate user with fields: name (full name), age (adult), email (valid format)"
  {:schema {:name :string :age :int :email :string}})
```

### Issue: Validation Failures

**Solution**: Use looser types or adjust schema

```clojure
;; ❌ Too strict
{:age :int}  ;; Fails if LLM returns "30"

;; ✅ More forgiving
{:age :string}  ;; Accept string, convert later
```

### Issue: Strategy Not Working

**Solution**: Explicitly set strategy

```clojure
;; Check provider capabilities
(if (supports-json-mode? provider)
  {:strategy :json-mode}
  {:strategy :tools})
```

## See Also

- [Native JSON Mode](NATIVE_JSON.md) - Simple JSON responses
- [Tools & Function Calling](TOOLS.md) - Tool-based approach
- [Core Chat](README.md#core-chat) - Basic chat functionality
