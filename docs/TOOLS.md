---
layout: default
title: Tools & Function Calling
---

# Tools & Function Calling

Create tools that AI models can call to interact with external systems.

## Quick Start

```clojure
(require '[langchain4clj.tools :as tools]
         '[langchain4clj.assistant :as assistant])

(tools/deftool get-weather
  "Fetches current weather for a location"
  {:location string?}
  [{:keys [location]}]
  (str "Weather in " location ": 72°F, sunny"))

(def my-assistant
  (assistant/create-assistant
    {:model model
     :tools [get-weather]}))

(my-assistant "What's the weather in Tokyo?")
;; => "The weather in Tokyo is currently 72°F and sunny."
```

## deftool Macro

The recommended way to create tools:

```clojure
(tools/deftool tool-name
  "Description shown to the AI"
  {:param-name type-predicate}
  [destructured-args]
  body...)
```

### Examples

```clojure
;; Simple tool
(tools/deftool calculate
  "Performs basic arithmetic operations"
  {:expression string?}
  [{:keys [expression]}]
  (eval (read-string expression)))

;; Multiple parameters
(tools/deftool compare-numbers
  "Compares two numbers"
  {:x number? :y number?}
  [{:keys [x y]}]
  (cond
    (> x y) (str x " is greater than " y)
    (< x y) (str x " is less than " y)
    :else (str x " equals " y)))

;; Optional parameters with defaults
(tools/deftool format-number
  "Formats a number with specified precision"
  {:value number? :precision int?}
  [{:keys [value precision] :or {precision 2}}]
  (format (str "%." precision "f") (double value)))

;; Custom predicates
(defn valid-email? [s]
  (and (string? s) (re-matches #".+@.+\..+" s)))

(tools/deftool register-user
  "Registers a new user"
  {:email valid-email? :age int?}
  [{:keys [email age]}]
  (str "Registered " email " (age: " age ")"))
```

## create-tool Function

For programmatic or dynamic tool creation:

```clojure
(require '[clojure.spec.alpha :as s])

(s/def ::location string?)
(s/def ::units #{"celsius" "fahrenheit"})
(s/def ::weather-params (s/keys :req-un [::location] :opt-un [::units]))

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
     :fn (fn [{:keys [location units]}] ...)}))
```

### Dynamic Tool Generation

```clojure
(defn create-api-tool [endpoint]
  (tools/create-tool
    {:name (str "call_" (name endpoint))
     :description (str "Calls the " (name endpoint) " API")
     :params-schema ::api-params
     :fn (fn [params]
           (http/post (str "https://api.example.com/" (name endpoint))
                      {:form-params params}))}))

(def api-tools (map create-api-tool [:users :posts :comments]))
```

## Parameter Normalization

Parameters are automatically normalized between Clojure's kebab-case and OpenAI's camelCase:

```clojure
(tools/deftool get-pokemon
  "Fetches Pokemon information"
  {:pokemon-name string?}
  [{:keys [pokemon-name]}]
  (fetch-pokemon pokemon-name))

;; OpenAI sends: {"pokemonName": "pikachu"}
;; You receive: {:pokemon-name "pikachu"}
```

## Common Patterns

### Tool with Side Effects

```clojure
(tools/deftool send-email
  "Sends an email"
  {:to string? :subject string? :body string?}
  [{:keys [to subject body]}]
  (try
    (email/send! {:to to :subject subject :body body})
    (str "Email sent to " to)
    (catch Exception e
      (str "Failed: " (.getMessage e)))))
```

### Tool with External API

```clojure
(tools/deftool fetch-exchange-rate
  "Fetches exchange rate between currencies"
  {:from-currency string? :to-currency string?}
  [{:keys [from-currency to-currency]}]
  (let [response (http/get "https://api.exchangerate-api.com/v4/latest/USD"
                           {:as :json})
        rates (get-in response [:body :rates])]
    (format "1 %s = %.4f %s" from-currency 
            (/ (get rates (keyword to-currency))
               (get rates (keyword from-currency)))
            to-currency)))
```

### Tool Registry

```clojure
(tools/deftool create-conversation
  "Creates a new conversation"
  {:name string?}
  [{:keys [name]}]
  (db/create-conversation name))

(tools/deftool list-conversations
  "Lists all conversations"
  {}
  [_]
  (db/list-conversations))

(tools/deftool delete-conversation
  "Deletes a conversation"
  {:id int?}
  [{:keys [id]}]
  (db/delete-conversation id))

(def conversation-tools
  [create-conversation list-conversations delete-conversation])
```

## When to Use Each API

| Use Case | API |
|----------|-----|
| Most tools (90%) | `deftool` |
| Dynamic generation | `create-tool` |
| Complex spec validation | `create-tool` |
| Simple static tools | `deftool` |

## Guidelines

**Tool descriptions matter** - The AI uses descriptions to decide when to call tools. Be specific about what the tool does and when to use it.

**Return strings** - Tools should return descriptive strings, not exceptions. The AI needs text responses to understand what happened.

**Keep tools focused** - One tool should do one thing well. Create separate tools for create/update/delete operations.

**Use descriptive parameter names** - `user-id` is better than `id`, `first-number` is better than `x`.

## Related

- [Assistant System](ASSISTANT.md) - Using tools with assistants
- [Structured Output](STRUCTURED_OUTPUT.md) - Another way to get structured data
