---
layout: default
title: Structured Output
---

# Structured Output

Get structured responses in JSON or EDN format with schema validation.

## Quick Start

```clojure
(require '[langchain4clj.structured :as structured])

(def user-schema
  {:name :string
   :age :int
   :email :string
   :active :boolean})

(def user (structured/structured-output model
            "Generate a fictional user profile"
            {:schema user-schema}))
;; => {:name "Alice Smith" :age 28 :email "alice@example.com" :active true}
```

## Schema Definition

```clojure
;; Simple types
{:name :string
 :age :int
 :price :float
 :active :boolean}

;; Collections
{:tags [:vector :string]
 :scores [:vector :int]
 :metadata [:map :string :any]}

;; Nested objects
{:user {:name :string :email :string}
 :address {:street :string :city :string :zip :int}}

;; Complex schema
(def product-schema
  {:id :int
   :name :string
   :price :float
   :tags [:vector :string]
   :inventory {:warehouse :string :quantity :int}
   :reviews [:vector {:rating :int :comment :string}]})
```

## Output Formats

```clojure
;; EDN (default) - returns Clojure data
(structured/structured-output model prompt
  {:schema schema :output-format :edn})
;; => {:name "John" :age 30}

;; JSON string
(structured/structured-output model prompt
  {:schema schema :output-format :json})
;; => "{\"name\":\"John\",\"age\":30}"
```

## Strategies

```clojure
;; Auto (recommended) - library picks best approach
(structured/structured-output model prompt {:schema schema})

;; JSON Mode - native provider support (OpenAI, Gemini)
(structured/structured-output model prompt
  {:schema schema :strategy :json-mode})

;; Tool-Based - uses function calling
(structured/structured-output model prompt
  {:schema schema :strategy :tools})

;; Validation - prompts for JSON, validates, retries on failure
(structured/structured-output model prompt
  {:schema schema :strategy :validation :max-retries 3})
```

## Convenience Functions

```clojure
;; EDN mode
(structured/chat-edn-mode model prompt
  :schema {:name :string :age :int})

;; JSON mode
(structured/chat-json-mode model prompt
  :schema {:name :string :age :int}
  :output-format :json)

;; Tool-based
(structured/chat-with-output-tool model prompt schema
  :output-format :edn)

;; With validation
(structured/chat-with-validation model prompt schema
  :max-retries 3
  :output-format :edn)
```

## defstructured Macro

Define reusable structured output types:

```clojure
(structured/defstructured Person
  {:name :string
   :age :int
   :email :string
   :interests [:vector :string]})

;; Creates two functions:
(get-person model "Generate a fictional person")
;; => {:name "Bob" :age 35 :email "bob@example.com" :interests ["reading"]}

(get-person-json model "Generate a fictional person")
;; => "{\"name\":\"Bob\",\"age\":35,...}"
```

## Common Use Cases

### Extract Data from Text

```clojure
(def resume-schema
  {:name :string
   :email :string
   :skills [:vector :string]
   :experience [:vector {:company :string :role :string :years :int}]})

(structured/structured-output model
  (str "Extract information from this resume: " resume-text)
  {:schema resume-schema})
```

### Generate Test Data

```clojure
(structured/defstructured TestUser
  {:id :int :username :string :email :string})

(repeatedly 5 #(get-test-user model "Generate a random user"))
```

### Data Transformation

```clojure
(structured/structured-output model
  (str "Convert this CSV to structured data: " csv-data)
  {:schema {:users [:vector {:name :string :age :int :city :string}]}})
```

## Validation

```clojure
;; Automatic validation (default)
(structured/structured-output model prompt
  {:schema {:age :int} :validate? true})

;; Disable validation
(structured/structured-output model prompt
  {:schema schema :validate? false})
```

## Type Conversion

```clojure
;; EDN to JSON string
(structured/edn->json-str {:name "Alice" :age 30})
;; => "{\"name\":\"Alice\",\"age\":30}"

;; JSON string to EDN
(structured/json-str->edn "{\"name\":\"Alice\",\"age\":30}")
;; => {:name "Alice" :age 30}
```

## Configuration Reference

```clojure
(structured/structured-output model prompt
  {:schema schema          ;; Required
   :strategy :auto         ;; :auto, :json-mode, :tools, :validation
   :output-format :edn     ;; :edn, :json, :json-str
   :validate? true         ;; Validate against schema
   :max-retries 3          ;; For validation strategy
   :temperature 0.7})
```

## Related

- [Native JSON Mode](NATIVE_JSON.md) - Simple JSON responses
- [Tools & Function Calling](TOOLS.md) - Tool-based approach
- [Core Chat](CORE_CHAT.md) - Basic chat functionality
