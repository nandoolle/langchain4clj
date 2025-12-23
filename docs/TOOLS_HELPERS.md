# Tool Registration Helpers

The `langchain4clj.tools.helpers` namespace provides helpers for registering tools using JSON Schema for parameter specifications. This is useful when integrating with external systems that define tools using JSON Schema format.

## When to Use This

Use this namespace when:
- Integrating with external systems that use JSON Schema (MCP servers, OpenAPI specs)
- Receiving tool definitions from external APIs in JSON Schema format
- Using LangChain4j AiServices directly instead of the langchain4clj assistant

**For most use cases**, prefer `deftool` from `langchain4clj.tools` which provides a more idiomatic Clojure experience with predicate-based parameter definitions.

## Quick Start

```clojure
(require '[langchain4clj.tools.helpers :as helpers])

;; Define a simple tool with JSON Schema
(def weather-tool
  (helpers/deftool-from-schema
    {:name "get_weather"
     :description "Get current weather for a location"
     :parameters {:type :object
                  :properties {:location {:type :string
                                          :description "City name"}
                               :units {:enum ["celsius" "fahrenheit"]}}
                  :required [:location]}
     :fn (fn [{:keys [location units]}]
           (str "Weather in " location ": 22°" (or units "C")))}))

;; Use the tool
(:spec weather-tool)     ;; => ToolSpecification
(:executor weather-tool) ;; => ToolExecutor
```

## Comparison: `deftool` vs `helpers`

| Feature | `deftool` (recommended) | `helpers` |
|---------|------------------------|-----------|
| Schema format | Clojure predicates `{:x string?}` | JSON Schema `{:type :string}` |
| Use case | Clojure-first development | External JSON Schema integration |
| Works with assistant | ✅ Direct support | ✅ Via `tools->map` |
| Syntax | Macro (defn-like) | Functions |

```clojure
;; deftool (recommended for most cases)
(deftool get-weather
  "Get weather for a location"
  {:location string?, :units string?}
  [{:keys [location units]}]
  (str "Weather in " location ": 22°" (or units "C")))

;; helpers (for JSON Schema integration)
(def weather-tool
  (helpers/deftool-from-schema
    {:name "get_weather"
     :description "Get weather for a location"
     :parameters {:type :object
                  :properties {:location {:type :string}
                               :units {:type :string}}
                  :required [:location]}
     :fn (fn [{:keys [location units]}]
           (str "Weather in " location ": 22°" (or units "C")))}))
```

## Core Functions

### `create-tool-spec`

Creates a LangChain4j `ToolSpecification` from a Clojure map.

```clojure
(def spec
  (helpers/create-tool-spec
    {:name "search"
     :description "Search the web"
     :parameters {:type :object
                  :properties {:query {:type :string}
                               :limit {:type :integer}}
                  :required [:query]}}))
```

**Options:**
- `:name` - Tool name (string, required)
- `:description` - Tool description (string, required)
- `:parameters` - JSON Schema EDN for parameters (optional)

### `create-tool-executor`

Creates a LangChain4j `ToolExecutor` from a Clojure function.

```clojure
(def executor
  (helpers/create-tool-executor
    (fn [{:keys [query limit]}]
      {:results (search-web query (or limit 10))
       :total 100})))
```

**Features:**
- Automatically parses JSON arguments to Clojure maps with keyword keys
- Converts return values to strings (JSON encodes complex values)
- Handles nil by returning "null"

**Options (second argument):**
- `:parse-args?` - If true (default), parse JSON args to EDN map

### `create-safe-executor`

Like `create-tool-executor`, but catches exceptions and returns error messages.

```clojure
(def safe-executor
  (helpers/create-safe-executor
    (fn [{:keys [url]}]
      (fetch-url url))
    {:on-error (fn [e]
                 (str "Failed: " (.getMessage e)))}))
```

**Options:**
- `:on-error` - Function called with exception, returns error message string
- `:parse-args?` - Same as `create-tool-executor`

### `deftool-from-schema`

Convenience function to create both specification and executor together.

```clojure
(def calculator
  (helpers/deftool-from-schema
    {:name "calculate"
     :description "Perform arithmetic"
     :parameters {:type :object
                  :properties {:operation {:enum ["add" "subtract" "multiply" "divide"]}
                               :a {:type :number}
                               :b {:type :number}}
                  :required [:operation :a :b]}
     :fn (fn [{:keys [operation a b]}]
           (case operation
             "add" (+ a b)
             "subtract" (- a b)
             "multiply" (* a b)
             "divide" (/ a b)))}))

;; Returns:
;; {:name "calculate"
;;  :spec #<ToolSpecification>
;;  :executor #<ToolExecutor>}
```

### `tools->map`

Creates a map of `ToolSpecification -> ToolExecutor` for use with AiServices.

```clojure
(def tools
  (helpers/tools->map
    [{:name "get_weather"
      :description "Get weather"
      :parameters {:type :object
                   :properties {:location {:type :string}}
                   :required [:location]}
      :fn (fn [{:keys [location]}] (get-weather location))}
     {:name "search"
      :description "Search the web"
      :parameters {:type :object
                   :properties {:query {:type :string}}
                   :required [:query]}
      :fn (fn [{:keys [query]}] (search-web query))}]))

;; Use with AiServices
(-> (AiServices/builder MyInterface)
    (.chatModel model)
    (.tools tools)
    (.build))
```

**Options (second argument):**
- `:safe?` - If true, wrap executors with error handling
- `:on-error` - Error handler function when `:safe?` is true

### `tools->specs`

Extract just the `ToolSpecification` objects from tool definitions.

```clojure
(def specs (helpers/tools->specs my-tool-definitions))
;; => [ToolSpecification1, ToolSpecification2, ...]
```

### `find-executor`

Find an executor by tool name in a tools map.

```clojure
(def executor (helpers/find-executor tools-map "get_weather"))
```

## JSON Schema Support

The parameters use standard JSON Schema syntax. All types from `langchain4clj.schema` are supported:

| Type | Example |
|------|---------|
| String | `{:type :string}` |
| Number | `{:type :number}` |
| Integer | `{:type :integer}` |
| Boolean | `{:type :boolean}` |
| Array | `{:type :array :items {:type :string}}` |
| Object | `{:type :object :properties {...}}` |
| Enum | `{:enum ["a" "b" "c"]}` |
| Nullable | `{:type [:string :null]}` |

## Using with AiServices

When you need to use LangChain4j AiServices directly instead of the langchain4clj assistant:

```clojure
(ns my-app.tools
  (:require
   [langchain4clj.tools.helpers :as helpers]
   [langchain4clj.core :as lc])
  (:import
   [dev.langchain4j.service AiServices]))

;; Define tools with JSON Schema
(def my-tools
  (helpers/tools->map
    [{:name "get_current_time"
      :description "Get the current date and time"
      :fn (fn [_] (str (java.time.Instant/now)))}
     
     {:name "add_numbers"
      :description "Add two numbers together"
      :parameters {:type :object
                   :properties {:a {:type :number :description "First number"}
                                :b {:type :number :description "Second number"}}
                   :required [:a :b]}
      :fn (fn [{:keys [a b]}] (+ a b))}
     
     {:name "search_products"
      :description "Search for products in the catalog"
      :parameters {:type :object
                   :properties {:query {:type :string}
                                :category {:enum ["electronics" "books" "clothing"]}
                                :max_price {:type :number}}
                   :required [:query]}
      :fn (fn [{:keys [query category max_price]}]
            {:products (search-catalog query category max_price)
             :total 42})}]
    {:safe? true}))  ;; Enable error handling

;; Use with a chat model via AiServices
(definterface Assistant
  (^String chat [^String message]))

(def assistant
  (-> (AiServices/builder Assistant)
      (.chatLanguageModel (lc/create-model {:provider :openai
                                            :model-name "gpt-4o"
                                            :api-key (System/getenv "OPENAI_API_KEY")}))
      (.tools my-tools)
      (.build)))

(.chat assistant "What is 2 + 3?")
;; => "2 + 3 equals 5"
```

## Bridge Function: `tools->aiservices`

If you have tools created with `deftool` and want to use them with AiServices directly, use the bridge function in `langchain4clj.tools`:

```clojure
(require '[langchain4clj.tools :refer [deftool tools->aiservices]])

;; Define tools with Clojure predicates
(deftool get-weather
  "Get weather for a location"
  {:location string?, :units string?}
  [{:keys [location units]}]
  (str "Weather in " location ": 22°" (or units "C")))

(deftool add-numbers
  "Add two numbers"
  {:a number?, :b number?}
  [{:keys [a b]}]
  (+ a b))

;; Convert for AiServices
(def tools-map (tools->aiservices get-weather add-numbers))
```

## See Also

- [Schema Converter](SCHEMA.md) - JSON Schema to LangChain4j conversion
- [Tools](../src/langchain4clj/tools.clj) - Clojure-native tool support with `deftool`
