---
layout: default
title: Tools & Function Calling
---

# Tool System Guide

Complete guide to creating and using tools in LangChain4Clj v0.8.0+

## Table of Contents

1. [Overview](#overview)
2. [Quick Start](#quick-start)
3. [deftool Macro (Recommended)](#deftool-macro-recommended)
4. [create-tool Function (Programmatic)](#create-tool-function-programmatic)
5. [Parameter Normalization](#parameter-normalization)
6. [Advanced Examples](#advanced-examples)
7. [Migration Guide](#migration-guide)
8. [Best Practices](#best-practices)
9. [Troubleshooting](#troubleshooting)

---

## Overview

LangChain4Clj provides two APIs for creating tools that AI models can call:

| API | Use Case | Code Style |
|-----|----------|------------|
| **`deftool`** (macro) | 90% of use cases | Idiomatic, concise, defn-like |
| **`create-tool`** (function) | Dynamic/programmatic | Explicit, flexible, data-driven |

### Key Features (v0.8.0)

- ✅ **Schemas are mandatory** - Tools without schemas are never called by OpenAI
- ✅ **Automatic normalization** - Write kebab-case, AI uses camelCase
- ✅ **Multiple schema libraries** - Spec, Plumatic Schema, Malli all supported
- ✅ **Clean destructuring** - No manual parameter extraction needed
- ✅ **Compile-time validation** - Catch errors early

---

## Quick Start

```clojure
(require '[langchain4clj.tools :as tools]
         '[langchain4clj.assistant :as assistant])

;; 1. Define a tool
(tools/deftool get-weather
  "Fetches current weather for a location"
  {:location string?}
  [{:keys [location]}]
  (str "Weather in " location ": 72°F, sunny"))

;; 2. Use it in an assistant
(def my-assistant
  (assistant/create-assistant
    {:model model
     :tools [get-weather]}))

;; 3. Let the AI call it
(my-assistant "What's the weather in Tokyo?")
;; AI automatically calls get-weather tool!
;; => "The weather in Tokyo is currently 72°F and sunny."
```

---

## deftool Macro (Recommended)

The `deftool` macro provides a defn-like syntax for creating tools with inline schemas.

### Basic Syntax

```clojure
(deftool tool-name
  "Description shown to the AI"
  {:param-name type-predicate}
  [destructured-args]
  body...)
```

### Simple Example

```clojure
(tools/deftool calculate
  "Performs basic arithmetic operations"
  {:expression string?}
  [{:keys [expression]}]
  (eval (read-string expression)))

;; Generated tool automatically handles:
;; - Schema validation
;; - Parameter normalization (kebab-case ↔ camelCase)
;; - Error messages
```

### Multiple Parameters

```clojure
(tools/deftool compare-numbers
  "Compares two numbers and returns which is larger"
  {:x number?
   :y number?}
  [{:keys [x y]}]
  (cond
    (> x y) (str x " is greater than " y)
    (< x y) (str x " is less than " y)
    :else (str x " equals " y)))
```

### Optional Parameters

Use Clojure's `:or` destructuring for defaults:

```clojure
(tools/deftool format-number
  "Formats a number with specified precision"
  {:value number?
   :precision int?}
  [{:keys [value precision] :or {precision 2}}]
  (format (str "%." precision "f") (double value)))

;; Call with precision: (format-number {:value 3.14159 :precision 4})
;; Call without: (format-number {:value 3.14159}) => uses 2
```

### Complex Types

You can use any predicate function:

```clojure
(tools/deftool create-user
  "Creates a user with validation"
  {:username string?
   :age int?
   :email string?
   :active boolean?
   :roles vector?}
  [{:keys [username age email active roles]}]
  {:username username
   :age age
   :email email
   :active active
   :roles roles})
```

### Custom Predicates

Define your own validation predicates:

```clojure
(defn valid-email? [s]
  (and (string? s)
       (re-matches #".+@.+\..+" s)))

(defn positive-int? [n]
  (and (int? n) (pos? n)))

(tools/deftool register-user
  "Registers a new user"
  {:email valid-email?
   :age positive-int?}
  [{:keys [email age]}]
  (str "Registered " email " (age: " age ")"))
```

### When to Use deftool

✅ **Use `deftool` when:**
- Creating most tools (90% of cases)
- You want concise, readable code
- Schema is known at compile time
- You want defn-like syntax

❌ **Don't use `deftool` when:**
- Generating tools dynamically at runtime
- Need complex spec validations with custom error messages
- Integrating with existing spec/schema/malli definitions

---

## create-tool Function (Programmatic)

The `create-tool` function provides explicit, data-driven tool creation.

### Basic Syntax

```clojure
(create-tool
  {:name "tool-name"
   :description "Description shown to the AI"
   :params-schema schema-definition  ; REQUIRED!
   :fn handler-function})
```

### With Clojure Spec

```clojure
(require '[clojure.spec.alpha :as s])

;; Define specs
(s/def ::location string?)
(s/def ::units #{"celsius" "fahrenheit"})
(s/def ::weather-params (s/keys :req-un [::location]
                                :opt-un [::units]))

;; Create tool
(def get-weather
  (tools/create-tool
    {:name "get_weather"
     :description "Fetches weather for a location"
     :params-schema ::weather-params
     :fn (fn [{:keys [location units] :or {units "celsius"}}]
           (fetch-weather-data location units))}))
```

### With Plumatic Schema

```clojure
(require '[schema.core :as s])

(def WeatherParams
  {:location s/Str
   (s/optional-key :units) (s/enum "celsius" "fahrenheit")})

(def get-weather
  (tools/create-tool
    {:name "get_weather"
     :description "Fetches weather for a location"
     :params-schema WeatherParams
     :fn (fn [{:keys [location units] :or {units "celsius"}}]
           (fetch-weather-data location units))}))
```

### With Malli

```clojure
(require '[malli.core :as m])

(def WeatherParams
  [:map
   [:location :string]
   [:units {:optional true} [:enum "celsius" "fahrenheit"]]])

(def get-weather
  (tools/create-tool
    {:name "get_weather"
     :description "Fetches weather for a location"
     :params-schema WeatherParams
     :fn (fn [{:keys [location units] :or {units "celsius"}}]
           (fetch-weather-data location units))}))
```

### Dynamic Tool Generation

Create tools at runtime:

```clojure
(defn create-api-tool [endpoint]
  (tools/create-tool
    {:name (str "call_" (name endpoint))
     :description (str "Calls the " (name endpoint) " API endpoint")
     :params-schema ::api-params
     :fn (fn [params]
           (http/post (str "https://api.example.com/" (name endpoint))
                      {:form-params params}))}))

;; Generate multiple tools
(def api-tools
  (map create-api-tool [:users :posts :comments]))
```

### When to Use create-tool

✅ **Use `create-tool` when:**
- Generating tools dynamically
- Need complex spec/schema/malli validations
- Building tool factories
- Programmatic tool configuration

❌ **Don't use `create-tool` when:**
- Writing simple static tools (use `deftool` instead)

---

## Parameter Normalization

LangChain4Clj automatically normalizes parameters between Clojure's kebab-case and OpenAI's camelCase.

### How It Works

```clojure
(tools/deftool get-pokemon
  "Fetches Pokemon information"
  {:pokemon-name string?}  ; Define with kebab-case
  [{:keys [pokemon-name]}]  ; Destructure with kebab-case
  (fetch-pokemon pokemon-name))

;; When OpenAI calls this tool, it sends:
;; {"pokemonName": "pikachu"}

;; LangChain4Clj normalizes to:
;; {:pokemon-name "pikachu", "pokemonName" "pikachu"}

;; Your code sees kebab-case ✅
;; Spec validation works ✅
;; OpenAI gets what it expects ✅
```

### Nested Parameters

Normalization works recursively:

```clojure
(tools/deftool create-pokemon-team
  "Creates a Pokemon team"
  {:team-name string?
   :team-members vector?}
  [{:keys [team-name team-members]}]
  ;; team-members might contain nested maps
  ;; All keys are normalized!
  {:name team-name :members team-members})

;; AI sends:
;; {"teamName": "Champions", 
;;  "teamMembers": [{"pokemonName": "Pikachu", "pokemonLevel": 50}]}

;; You receive:
;; {:team-name "Champions"
;;  :team-members [{:pokemon-name "Pikachu" :pokemon-level 50
;;                  "pokemonName" "Pikachu" "pokemonLevel" 50}]}
```

### Benefits

- ✅ Write idiomatic Clojure (kebab-case)
- ✅ Full OpenAI compatibility (camelCase)
- ✅ Zero configuration
- ✅ Works with all schema libraries
- ✅ Handles deep nesting

---

## Advanced Examples

### Tool with Side Effects

```clojure
(tools/deftool send-email
  "Sends an email to a recipient"
  {:to string?
   :subject string?
   :body string?}
  [{:keys [to subject body]}]
  (try
    (email/send! {:to to :subject subject :body body})
    (str "Email sent to " to)
    (catch Exception e
      (str "Failed to send email: " (.getMessage e)))))
```

### Tool with External API

```clojure
(require '[clj-http.client :as http])

(tools/deftool fetch-exchange-rate
  "Fetches current exchange rate between two currencies"
  {:from-currency string?
   :to-currency string?}
  [{:keys [from-currency to-currency]}]
  (let [response (http/get "https://api.exchangerate-api.com/v4/latest/USD"
                           {:as :json})
        rates (get-in response [:body :rates])
        from-rate (get rates (keyword from-currency))
        to-rate (get rates (keyword to-currency))]
    (if (and from-rate to-rate)
      (format "1 %s = %.4f %s" from-currency (/ to-rate from-rate) to-currency)
      "Currency not found")))
```

### Tool with Database Access

```clojure
(tools/deftool query-users
  "Queries users from database"
  {:min-age int?
   :max-age int?}
  [{:keys [min-age max-age]}]
  (let [users (db/query ["SELECT * FROM users WHERE age >= ? AND age <= ?"
                         min-age max-age])]
    (if (empty? users)
      "No users found"
      (str "Found " (count users) " users: "
           (clojure.string/join ", " (map :name users))))))
```

### Tool with Validation and Coercion

```clojure
(require '[clojure.spec.alpha :as s])

;; Complex validation with spec
(s/def ::age (s/and int? #(>= % 0) #(<= % 150)))
(s/def ::email (s/and string? #(re-matches #".+@.+\..+" %)))
(s/def ::user-params (s/keys :req-un [::email ::age]))

(def create-user
  (tools/create-tool
    {:name "create_user"
     :description "Creates a new user with validation"
     :params-schema ::user-params
     :fn (fn [{:keys [email age]}]
           ;; Spec already validated age is 0-150
           ;; and email matches pattern
           (db/insert! :users {:email email :age age})
           (str "User created: " email))}))
```

### Tool Registry Pattern

```clojure
;; Define multiple related tools
(tools/deftool create-conversation
  "Creates a new conversation"
  {:name string?}
  [{:keys [name]}]
  (db/create-conversation name))

(tools/deftool get-conversations
  "Lists all conversations"
  {}  ; No parameters
  [_]
  (db/list-conversations))

(tools/deftool delete-conversation
  "Deletes a conversation by ID"
  {:id int?}
  [{:keys [id]}]
  (db/delete-conversation id))

;; Register all conversation tools
(def conversation-tools
  [create-conversation
   get-conversations
   delete-conversation])

;; Use in assistant
(def conversation-assistant
  (assistant/create-assistant
    {:model model
     :tools conversation-tools}))
```

---

## Migration Guide

### From v0.7.x Threading API

**Old API (v0.7.x):**
```clojure
(s/def ::pokemon-name string?)
(s/def ::pokemon-params (s/keys :req-un [::pokemon-name]))

(def get-pokemon
  (-> (tools/tool
       "get_pokemon"
       "Fetches Pokemon information"
       (fn [params]
         (let [pokemon-name (or (get params "pokemonName")
                                (get params :pokemon-name)
                                (get params "pokemon_name"))]
           (fetch-pokemon pokemon-name))))
      (tools/with-params-schema ::pokemon-params)))
```

**New API (v0.8.0 - Option 1: deftool):**
```clojure
(tools/deftool get-pokemon
  "Fetches Pokemon information"
  {:pokemon-name string?}
  [{:keys [pokemon-name]}]
  (fetch-pokemon pokemon-name))
```

**New API (v0.8.0 - Option 2: create-tool):**
```clojure
(s/def ::pokemon-name string?)
(s/def ::pokemon-params (s/keys :req-un [::pokemon-name]))

(def get-pokemon
  (tools/create-tool
    {:name "get_pokemon"
     :description "Fetches Pokemon information"
     :params-schema ::pokemon-params
     :fn (fn [{:keys [pokemon-name]}]
           (fetch-pokemon pokemon-name))}))
```

### Migration Steps

1. **Identify all tools using old API**
   ```bash
   grep -r "tools/tool" src/
   grep -r "with-params-schema" src/
   ```

2. **Choose migration path:**
   - Simple tools → Use `deftool`
   - Complex validations → Keep specs, use `create-tool`

3. **Update tool definitions**
   - Replace `tool` + `with-params-schema` with `deftool` or `create-tool`
   - Remove manual parameter extraction
   - Add inline schemas or ensure specs exist

4. **Test tools**
   ```clojure
   ;; Test direct execution
   (tools/execute-tool get-pokemon {:pokemon-name "pikachu"})
   
   ;; Test with assistant
   (def test-assistant
     (assistant/create-assistant
       {:model model
        :tools [get-pokemon]}))
   
   (test-assistant "Tell me about Pikachu")
   ```

### Deprecation Warnings

If you see warnings like:
```
WARNING: `tool` function is deprecated. Use `deftool` macro or `create-tool` function instead.
WARNING: `with-params-schema` is deprecated. Use `deftool` macro or `create-tool` function instead.
```

These functions still work but are discouraged. Migrate to the new API for:
- Shorter code (70% less)
- Mandatory schemas (prevents bugs)
- Automatic normalization
- Better error messages

---

## Best Practices

### 1. Always Provide Good Descriptions

```clojure
;; ❌ Bad - vague description
(tools/deftool get-data
  "Gets data"
  {:id int?}
  [{:keys [id]}]
  (fetch-data id))

;; ✅ Good - clear, specific description
(tools/deftool get-user-profile
  "Fetches complete user profile including name, email, and preferences by user ID"
  {:user-id int?}
  [{:keys [user-id]}]
  (fetch-user-profile user-id))
```

The AI uses descriptions to decide when to call tools. Be specific!

### 2. Use Descriptive Parameter Names

```clojure
;; ❌ Bad - ambiguous
(tools/deftool calculate
  "Calculates something"
  {:x number? :y number?}
  [{:keys [x y]}]
  (+ x y))

;; ✅ Good - self-documenting
(tools/deftool add-numbers
  "Adds two numbers together and returns the sum"
  {:first-number number?
   :second-number number?}
  [{:keys [first-number second-number]}]
  (+ first-number second-number))
```

### 3. Handle Errors Gracefully

```clojure
(tools/deftool fetch-user
  "Fetches user information by ID"
  {:user-id int?}
  [{:keys [user-id]}]
  (try
    (if-let [user (db/get-user user-id)]
      (format "User found: %s (%s)" (:name user) (:email user))
      (str "User with ID " user-id " not found"))
    (catch Exception e
      (str "Error fetching user: " (.getMessage e)))))
```

Return descriptive strings, not exceptions. The AI needs text responses!

### 4. Keep Tools Focused

```clojure
;; ❌ Bad - tool does too much
(tools/deftool manage-user
  "Creates, updates, or deletes users"
  {:action string? :user-id int? :data map?}
  [{:keys [action user-id data]}]
  (case action
    "create" (create-user data)
    "update" (update-user user-id data)
    "delete" (delete-user user-id)))

;; ✅ Good - separate tools with clear purposes
(tools/deftool create-user
  "Creates a new user with the provided data"
  {:name string? :email string?}
  [{:keys [name email]}]
  (create-user {:name name :email email}))

(tools/deftool update-user
  "Updates an existing user's information"
  {:user-id int? :name string? :email string?}
  [{:keys [user-id name email]}]
  (update-user user-id {:name name :email email}))

(tools/deftool delete-user
  "Permanently deletes a user by ID"
  {:user-id int?}
  [{:keys [user-id]}]
  (delete-user user-id))
```

### 5. Validate Input Types

```clojure
;; Use specific predicates
(tools/deftool schedule-meeting
  "Schedules a meeting"
  {:title string?
   :date string?  ; Better: use custom predicate for ISO date
   :duration-minutes int?
   :attendees vector?}  ; Better: (s/coll-of string?)
  [{:keys [title date duration-minutes attendees]}]
  (schedule-meeting title date duration-minutes attendees))

;; Better with custom predicates
(defn iso-date? [s]
  (and (string? s)
       (re-matches #"\d{4}-\d{2}-\d{2}" s)))

(defn email-list? [coll]
  (and (vector? coll)
       (every? #(re-matches #".+@.+\..+" %) coll)))

(tools/deftool schedule-meeting
  "Schedules a meeting for a specific date and time"
  {:title string?
   :date iso-date?
   :duration-minutes int?
   :attendees email-list?}
  [{:keys [title date duration-minutes attendees]}]
  (schedule-meeting title date duration-minutes attendees))
```

---

## Troubleshooting

### Tool Not Being Called

**Symptom:** AI responds with general knowledge instead of calling your tool.

**Causes:**
1. **Missing schema** (v0.7.x only - fixed in v0.8.0)
   ```clojure
   ;; ❌ v0.7.x - tool without schema never called
   (def broken-tool
     (tools/tool "calculate" "Math" calculate-fn))
   
   ;; ✅ v0.8.0 - schema is mandatory
   (tools/deftool calculate
     "Math"
     {:expression string?}
     [{:keys [expression]}]
     (calculate expression))
   ```

2. **Vague description**
   ```clojure
   ;; ❌ Bad - AI doesn't know when to use it
   (tools/deftool get-info
     "Gets information"
     {:id int?}
     [{:keys [id]}]
     (fetch-info id))
   
   ;; ✅ Good - clear purpose
   (tools/deftool get-user-profile
     "Retrieves complete user profile information including name, email, and account settings by user ID. Use this when the user asks about a specific user's details."
     {:user-id int?}
     [{:keys [user-id]}]
     (fetch-user-profile user-id))
   ```

3. **Wrong parameters in description**
   - Make sure parameter names match the schema

### Parameters Are Nil

**Symptom:** Tool receives `nil` for all parameters.

**Solution (v0.8.0):** This should never happen anymore! schemas are mandatory.

**If it still happens:**
```clojure
;; Check your tool definition
(tools/deftool my-tool
  "Description"
  {:param-name string?}  ; Must match destructuring!
  [{:keys [param-name]}]  ; Same name here
  (do-something param-name))
```

### Validation Errors

**Symptom:** Tool throws validation errors.

**Solution:** Check your predicate functions:

```clojure
;; Debug by testing predicates directly
(def my-predicate int?)
(my-predicate 42)     ;; => true
(my-predicate "42")   ;; => false

;; Add logging to see what you're receiving
(tools/deftool debug-tool
  "Debug tool"
  {:value int?}
  [params]  ; Don't destructure yet
  (do
    (println "Received params:" params)
    (println "Value:" (:value params))
    (println "Type:" (type (:value params)))
    (str "Debugging: " params)))
```

### Complex Spec Not Working

**Symptom:** Using complex spec with `deftool` doesn't work.

**Solution:** Use `create-tool` for complex specs:

```clojure
;; ❌ Won't work - deftool uses simple predicates
(tools/deftool my-tool
  "Description"
  {::complex-spec :spec}  ; Can't use spec keywords directly
  ...)

;; ✅ Use create-tool for complex specs
(s/def ::email (s/and string? #(re-matches #".+@.+\..+" %)))
(s/def ::age (s/and int? #(>= % 0) #(<= % 150)))
(s/def ::user-params (s/keys :req-un [::email ::age]))

(def create-user
  (tools/create-tool
    {:name "create_user"
     :description "Creates a user"
     :params-schema ::user-params
     :fn (fn [{:keys [email age]}]
           (create-user email age))}))
```

### OpenAI Error: "Invalid tool call"

**Symptom:** OpenAI rejects tool calls.

**Causes:**
1. Tool name has invalid characters
   ```clojure
   ;; ❌ Bad - special characters
   (tools/deftool my-tool!
     ...)
   
   ;; ✅ Good - alphanumeric + underscores
   (tools/deftool my_tool
     ...)
   ```

2. Description too long (keep under 1024 chars)

3. Too many parameters (keep under 20)

---

## Summary

### Quick Reference

| Task | Use This |
|------|----------|
| Simple tool with 1-3 params | `deftool` |
| Tool with optional params | `deftool` with `:or` |
| Complex validation | `create-tool` + Spec |
| Dynamic tool generation | `create-tool` |
| Tool with side effects | Either (prefer `deftool`) |
| Tools with existing specs | `create-tool` |

### Code Comparison

```clojure
;; 90% of tools - use deftool
(tools/deftool my-tool
  "Description"
  {:param type?}
  [{:keys [param]}]
  body)

;; 10% of tools - use create-tool
(tools/create-tool
  {:name "my_tool"
   :description "Description"
   :params-schema ::spec
   :fn (fn [{:keys [param]}] body)})
```

### Key Takeaways

1. ✅ **Schemas are mandatory** - Prevents silent failures
2. ✅ **Use `deftool` for most cases** - Simple, concise, idiomatic
3. ✅ **Parameter normalization is automatic** - Write kebab-case naturally
4. ✅ **Descriptions matter** - AI uses them to decide when to call tools
5. ✅ **Return strings, not exceptions** - AI needs textual responses

---

**Next Steps:**
- See [README.md](../README.md) for more examples
- Check [examples/](../examples/) for complete demos
- Read [ASSISTANT.md](ASSISTANT.md) for using tools with assistants

**Having issues?** [Open an issue on GitHub](https://github.com/langchain4clj/issues)
