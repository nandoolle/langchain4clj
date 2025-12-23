# JSON Schema Converter

langchain4clj provides a converter from JSON Schema (in EDN format) to LangChain4j's `JsonSchema` objects. This is useful for defining tool parameters using standard JSON Schema syntax.

## Why use this?

LangChain4j requires `JsonSchema` objects for tool parameter definitions, but building them with Java builders is verbose:

```java
// Java-style (verbose)
JsonObjectSchema.builder()
    .addProperty("name", JsonStringSchema.builder().build())
    .addProperty("age", JsonIntegerSchema.builder().build())
    .required(List.of("name"))
    .build()
```

With langchain4clj, you can use standard JSON Schema in EDN:

```clojure
(require '[langchain4clj.schema :as schema])

;; Clojure-style (concise)
(schema/edn->json-schema
  {:type :object
   :properties {:name {:type :string}
                :age {:type :integer}}
   :required [:name]})
```

## Basic Usage

### Primitive Types

```clojure
;; String
(schema/edn->json-schema {:type :string})
(schema/edn->json-schema {:type :string :description "User's name"})

;; Number (floating point)
(schema/edn->json-schema {:type :number})

;; Integer
(schema/edn->json-schema {:type :integer :description "User's age"})

;; Boolean
(schema/edn->json-schema {:type :boolean})
```

### Enums

```clojure
(schema/edn->json-schema {:enum ["low" "medium" "high"]})

(schema/edn->json-schema 
  {:enum ["celsius" "fahrenheit"]
   :description "Temperature units"})
```

### Arrays

```clojure
;; Array of strings
(schema/edn->json-schema
  {:type :array
   :items {:type :string}})

;; Array of integers with description
(schema/edn->json-schema
  {:type :array
   :items {:type :integer}
   :description "List of user IDs"})

;; Nested arrays (2D array of numbers)
(schema/edn->json-schema
  {:type :array
   :items {:type :array
           :items {:type :number}}})
```

### Objects

```clojure
;; Simple object
(schema/edn->json-schema
  {:type :object
   :properties {:name {:type :string}
                :email {:type :string}}})

;; Object with required fields
(schema/edn->json-schema
  {:type :object
   :properties {:name {:type :string}
                :email {:type :string}
                :age {:type :integer}}
   :required [:name :email]})

;; Object with description
(schema/edn->json-schema
  {:type :object
   :description "User information"
   :properties {:name {:type :string}}})

;; Nested objects
(schema/edn->json-schema
  {:type :object
   :properties {:user {:type :object
                       :properties {:name {:type :string}
                                    :address {:type :object
                                              :properties {:city {:type :string}}}}}}})
```

### Mixed/Nullable Types

```clojure
;; Nullable string (string or null)
(schema/edn->json-schema {:type [:string :null]})

;; String or number
(schema/edn->json-schema {:type [:string :number]})

;; With description
(schema/edn->json-schema 
  {:type [:string :integer]
   :description "ID can be string or integer"})
```

## Convenience Functions

### `schema-for-tool`

Creates an object schema for tool parameters with simpler syntax:

```clojure
;; All properties required by default
(schema/schema-for-tool
  {:location {:type :string :description "City name"}
   :units {:type :string :description "Temperature units"}})

;; With optional properties
(schema/schema-for-tool
  {:location {:type :string :description "City name"}
   :units {:type :string :description "Temperature units"}}
  {:optional [:units]})

;; With schema description
(schema/schema-for-tool
  {:query {:type :string}}
  {:description "Search parameters"
   :optional []})
```

### `json-string->json-schema`

Parse a JSON string and convert to LangChain4j JsonSchema:

```clojure
;; From JSON string (useful for external sources)
(schema/json-string->json-schema
  "{\"type\": \"object\", 
    \"properties\": {\"name\": {\"type\": \"string\"}},
    \"required\": [\"name\"]}")
```

### `any-type-schema`

Creates a schema that accepts any JSON type:

```clojure
;; For dynamic/unknown value types
(schema/any-type-schema)
```

## Real-World Examples

### Weather Tool

```clojure
(def weather-params
  (schema/edn->json-schema
    {:type :object
     :properties {:location {:type :string
                             :description "City name, e.g., 'San Francisco'"}
                  :units {:type :string
                          :description "Temperature units: 'celsius' or 'fahrenheit'"}}
     :required [:location]}))
```

### Search Tool with Complex Filters

```clojure
(def search-params
  (schema/edn->json-schema
    {:type :object
     :properties {:query {:type :string
                          :description "Search query"}
                  :filters {:type :object
                            :properties {:date_from {:type :string}
                                         :date_to {:type :string}
                                         :categories {:type :array
                                                      :items {:type :string}}}}
                  :limit {:type :integer
                          :description "Max results (default: 10)"}}
     :required [:query]}))
```

### MCP Tool Definition

```clojure
;; MCP tools use JSON Schema for input definitions
(def read-file-params
  (schema/edn->json-schema
    {:type :object
     :properties {:path {:type :string
                         :description "File path to read"}
                  :encoding {:type :string
                             :description "File encoding (default: utf-8)"}}
     :required [:path]}))
```

## Integration with Tools

Use with `ToolSpecification`:

```clojure
(import '[dev.langchain4j.agent.tool ToolSpecification])

(def my-tool
  (-> (ToolSpecification/builder)
      (.name "get_weather")
      (.description "Get current weather for a location")
      (.parameters (schema/edn->json-schema
                     {:type :object
                      :properties {:location {:type :string}
                                   :units {:type :string}}
                      :required [:location]}))
      (.build)))
```

## Supported JSON Schema Features

| Feature | Supported | Example |
|---------|-----------|---------|
| `type: string` | ✅ | `{:type :string}` |
| `type: number` | ✅ | `{:type :number}` |
| `type: integer` | ✅ | `{:type :integer}` |
| `type: boolean` | ✅ | `{:type :boolean}` |
| `type: array` | ✅ | `{:type :array :items {...}}` |
| `type: object` | ✅ | `{:type :object :properties {...}}` |
| `enum` | ✅ | `{:enum ["a" "b"]}` |
| `description` | ✅ | `{:type :string :description "..."}` |
| `required` | ✅ | `{:type :object :required [:name]}` |
| Mixed types | ✅ | `{:type [:string :null]}` |
| `type: null` | ⚠️ | Skipped in mixed types |

Note: `null` type is skipped because LangChain4j doesn't have a `JsonNullSchema` builder. Use mixed types like `[:string :null]` for nullable values.
