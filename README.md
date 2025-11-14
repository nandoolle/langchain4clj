<p align="center">
  <img src="docs/images/logo.png" alt="LangChain4Clj Logo"/>
</p>

# LangChain4Clj

A pure Clojure wrapper for [LangChain4j](https://github.com/langchain4j/langchain4j) - idiomatic, unopinionated, and data-driven.

## Philosophy

LangChain4Clj is a **pure translation layer** - we wrap LangChain4j's functionality in idiomatic Clojure without adding opinions, prompts, or behaviors. You get the full power of LangChain4j with Clojure's simplicity.

## Features

- **Multiple LLM Providers** - OpenAI, Anthropic, Google AI Gemini, Vertex AI Gemini, Ollama
- **Image Generation** - DALL-E 3 & DALL-E 2 support with HD quality and style control
- **Provider Failover** - Automatic retry and fallback for high availability
- **Streaming Responses** - Real-time token streaming for better UX
- **Tool/Function Calling** - Unified support for Spec, Schema, and Malli
- **Assistant System** - Memory management, tool execution loops, templates
- **Structured Output** - Automatic parsing with retry logic
- **Multi-Agent Orchestration** - Sequential, parallel, and collaborative agents
- **100% Data-Driven** - Configure everything with Clojure maps
- **Idiomatic API** - Threading-first, composable, pure Clojure

## Installation

Add to your `deps.edn`:

```clojure
{:deps {io.github.nandoolle/langchain4clj {:mvn/version "1.0.4"}}}
```

With schema libraries (optional):

```clojure
;; For Plumatic Schema support
{:deps {...}
 :aliases {:with-schema {:extra-deps {prismatic/schema {:mvn/version "1.4.1"}}}}}

;; For Malli support  
{:deps {...}
 :aliases {:with-malli {:extra-deps {metosin/malli {:mvn/version "0.16.4"}}}}}
```

## Quick Start

### Basic Chat

```clojure
(require '[langchain4clj.core :as llm])

;; Create a model
(def model (llm/create-model {:provider :openai
                              :api-key (System/getenv "OPENAI_API_KEY")
                              :model "gpt-4"}))

;; Simple chat
(llm/chat model "Explain quantum computing in one sentence")
;; => "Quantum computing harnesses quantum mechanical phenomena..."

;; Chat with options
(llm/chat model "Explain quantum computing"
  {:temperature 0.3        ; Control randomness
   :max-tokens 100         ; Limit response length
   :system-message "You are a physics teacher"})
;; => Returns ChatResponse with metadata
```

### Using Different Providers

```clojure
;; OpenAI
(def openai-model
  (llm/create-model {:provider :openai
                     :api-key (System/getenv "OPENAI_API_KEY")
                     :model "gpt-4o-mini"}))

;; Anthropic Claude
(def claude-model
  (llm/create-model {:provider :anthropic
                     :api-key (System/getenv "ANTHROPIC_API_KEY")
                     :model "claude-3-5-sonnet-20241022"}))

;; Google AI Gemini (Direct API)
(def gemini-model
  (llm/create-model {:provider :google-ai-gemini
                     :api-key (System/getenv "GEMINI_API_KEY")
                     :model "gemini-1.5-flash"}))

;; Vertex AI Gemini (Google Cloud)
(def vertex-gemini-model
  (llm/create-model {:provider :vertex-ai-gemini
                     :project "your-gcp-project-id"
                     :location "us-central1"
                     :model "gemini-1.5-pro"}))

;; Ollama (Local models - no API key needed!)
(def ollama-model
  (llm/create-model {:provider :ollama
                     :model "llama3.1"}))  ; Requires Ollama running locally

;; Helper functions (alternative API)
(def gemini (llm/google-ai-gemini-model {:api-key "..."}))
(def vertex (llm/vertex-ai-gemini-model {:project "..."}))
(def ollama (llm/ollama-model {:model "mistral"}))

;; All models work the same way
(llm/chat openai-model "Hello!")
(llm/chat claude-model "Hello!")
(llm/chat gemini-model "Hello!")
(llm/chat vertex-gemini-model "Hello!")
(llm/chat ollama-model "Hello!")
```

### Advanced Chat Options

The `chat` function supports all LangChain4j ChatRequest parameters:

```clojure
(llm/chat model "Your prompt"
  {:tools [tool-spec]           ; Function calling
   :response-format ResponseFormat/JSON  ; JSON mode
   :system-message "System prompt"
   :temperature 0.7
   :max-tokens 1000
   :top-p 0.9
   :top-k 40
   :frequency-penalty 0.0
   :presence-penalty 0.0
   :stop-sequences ["STOP"]
   :model-name "gpt-4"})         ; Override model
```

### Native JSON Mode

Force the LLM to return valid JSON (supported by OpenAI, Anthropic):

```clojure
(require '[dev.langchain4j.model.chat.request ResponseFormat])

;; Option 1: Direct in chat options
(llm/chat model "Return user data as JSON"
  {:response-format ResponseFormat/JSON})
;; => ChatResponse with guaranteed valid JSON in .text

;; Option 2: Using helper function
(llm/chat model "Return user data as JSON"
  (llm/with-json-mode {:temperature 0.7}))

;; Option 3: Threading-first style
(-> {:temperature 0.7
     :max-tokens 500}
    llm/with-json-mode
    (as-> opts (llm/chat model "Return user data" opts)))

;; Parse the JSON response
(require '[clojure.data.json :as json])
(let [response (llm/chat model "Return user data" 
                 {:response-format ResponseFormat/JSON})
      json-str (-> response .aiMessage .text)]
  (json/read-str json-str :key-fn keyword))
;; => {:name "John" :age 30 :email "john@example.com"}
```

**Why use native JSON mode?**

- 100% reliable - Provider guarantees valid JSON
- No parsing errors - No need for retry logic
- Faster - No post-processing validation needed
- Simple - Just parse and use

**Tip:** For complex structured output with validation, see the `structured` namespace which builds on JSON mode.

### Streaming Responses

Receive tokens in real-time as they're generated for better UX:

```clojure
(require '[langchain4clj.streaming :as streaming])

;; Create streaming model
(def streaming-model
  (streaming/create-streaming-model
    {:provider :openai
     :api-key (System/getenv "OPENAI_API_KEY")
     :model "gpt-4o-mini"}))

;; Stream with callbacks
(streaming/stream-chat streaming-model "Explain AI in simple terms"
  {:on-token (fn [token]
               (print token)
               (flush))
   :on-complete (fn [response]
                  (println "\nDone!")
                  (println "Tokens:" (-> response .tokenUsage .totalTokenCount)))
   :on-error (fn [error]
               (println "Error:" (.getMessage error)))})

;; Accumulate while streaming
(let [accumulated (atom "")
      result (promise)]
  (streaming/stream-chat streaming-model "Count to 5"
    {:on-token (fn [token]
                 (print token)
                 (flush)
                 (swap! accumulated str token))
     :on-complete (fn [resp]
                    (deliver result {:text @accumulated
                                    :response resp}))})
  @result)
;; => {:text "1, 2, 3, 4, 5" :response #<Response...>}
```

**Why use streaming?**

- Better UX - Users see progress immediately
- Feels faster - Perceived latency is lower
- Cancellable - Can stop mid-generation
- Real-time feedback - Process tokens as they arrive

**Works with all providers**: OpenAI, Anthropic, Google AI Gemini, Vertex AI Gemini, Ollama

**Tip:** See `examples/streaming_demo.clj` for interactive CLI examples and user-side core.async integration patterns.

### Image Generation

Generate images using DALL-E 3 and DALL-E 2:

```clojure
(require '[langchain4clj.image :as image])

;; Create an image model
(def model (image/create-image-model
            {:provider :openai
             :api-key (System/getenv "OPENAI_API_KEY")
             :model "dall-e-3"
             :quality "hd"
             :size "1024x1024"}))

;; Or use convenience function
(def model (image/openai-image-model
            {:api-key (System/getenv "OPENAI_API_KEY")
             :quality "hd"}))

;; Generate image
(def result (image/generate model "A sunset over mountains"))

;; Access results
(:url result)              ;; => "https://oaidalleapiprodscus..."
(:revised-prompt result)   ;; => "A picturesque view of a vibrant sunset..."
(:base64 result)           ;; => nil (or base64 data if requested)
```

**Features:**

- DALL-E 3 - HD quality, multiple sizes (1024x1024, 1792x1024, 1024x1792)
- DALL-E 2 - Faster and cheaper alternative (512x512, 256x256, 1024x1024)
- Style control - "vivid" (hyper-real) or "natural" (subtle)
- Quality options - "standard" or "hd" for DALL-E 3
- Revised prompts - DALL-E 3 returns enhanced/safety-filtered prompts
- Base64 support - Optional base64 encoding

**Examples:**

```clojure
;; HD quality landscape
(def hd-model (image/openai-image-model
               {:api-key "sk-..."
                :quality "hd"
                :size "1792x1024"}))

;; Style variations
(def vivid-model (image/openai-image-model
                  {:api-key "sk-..."
                   :style "vivid"}))      ; More dramatic

(def natural-model (image/openai-image-model
                    {:api-key "sk-..."
                     :style "natural"}))   ; More subtle

;; DALL-E 2 (faster, cheaper)
(def dalle2 (image/create-image-model
             {:provider :openai
              :api-key "sk-..."
              :model "dall-e-2"
              :size "512x512"}))
```

**Tip:** See `examples/image_generation_demo.clj` for comprehensive examples including HD quality, batch generation, and error handling.

### Tool Calling

LangChain4Clj offers two APIs for creating tools:

#### `deftool` Macro (Recommended)

The simplest way to create tools with inline schema validation:

```clojure
(require '[langchain4clj.tools :as tools])

;; Define a tool with defn-like syntax
(tools/deftool get-pokemon
  "Fetches Pokemon information by name"
  {:pokemon-name string?}  ; Inline schema
  [{:keys [pokemon-name]}]
  (fetch-pokemon pokemon-name))

;; Multiple parameters
(tools/deftool compare-numbers
  "Compares two numbers"
  {:x number? :y number?}
  [{:keys [x y]}]
  (str x " is " (if (> x y) "greater" "less") " than " y))

;; Use in assistants - just pass the tool!
(def assistant
  (assistant/create-assistant
    {:model model
     :tools [get-pokemon compare-numbers]}))
```

**Why `deftool`?**

- Concise - 5 lines vs 15 lines with alternative approaches
- Safe - Schema is mandatory, impossible to forget
- Idiomatic - Looks like `defn`
- Simple - Inline types with predicates (`string?`, `int?`, `boolean?`)
- Automatic - Kebab-case to camelCase normalization built-in

#### `create-tool` Function (Programmatic)

For dynamic tool creation or complex schemas:

```clojure
;; Using Clojure Spec (advanced schemas)
(def calculator 
  (tools/create-tool 
    {:name "calculator"
     :description "Performs calculations"
     :params-schema ::calc-params  ; Spec keyword
     :fn #(eval (read-string (:expression %)))}))

;; Using Plumatic Schema
(def weather-tool
  (tools/create-tool
    {:name "weather" 
     :description "Gets weather"
     :params-schema {:location s/Str}  ; Schema map
     :fn get-weather}))

;; Using Malli
(def database-tool
  (tools/create-tool
    {:name "query"
     :description "Query database"  
     :params-schema [:map [:query :string]]  ; Malli vector
     :fn query-db}))
```

**When to use `create-tool`:**

- Dynamic tool generation at runtime
- Complex validation with custom predicates
- Integration with existing spec/schema/malli definitions
- Programmatic tool configuration

#### Automatic Parameter Normalization

LangChain4Clj automatically handles the naming mismatch between Clojure's kebab-case convention and OpenAI's camelCase parameters:

```clojure
;; Define your tool using idiomatic kebab-case
(s/def ::pokemon-name string?)
(s/def ::pokemon-params (s/keys :req-un [::pokemon-name]))

(def get-pokemon-tool
  (tools/create-tool
    {:name "get_pokemon"
     :description "Fetches Pokemon information by name"
     :params-schema ::pokemon-params
     :fn (fn [{:keys [pokemon-name]}]  ; Use kebab-case naturally!
           (fetch-pokemon pokemon-name))}))

;; Both calling styles work automatically:
(tools/execute-tool get-pokemon-tool {:pokemon-name "pikachu"})    ; Clojure style
(tools/execute-tool get-pokemon-tool {"pokemonName" "pikachu"})    ; OpenAI style

;; When OpenAI calls your tool, it sends {"pokemonName": "pikachu"}
;; LangChain4Clj preserves the original AND adds kebab-case versions
;; Your code sees: {:pokemon-name "pikachu", "pokemonName" "pikachu"}
;; Spec validation works with :pokemon-name
;; Your destructuring works with :pokemon-name
```

**Benefits:**

- Write idiomatic Clojure code with kebab-case
- Full compatibility with OpenAI's camelCase parameters
- Spec/Schema/Malli validation works seamlessly
- Zero configuration required
- Handles deep nesting and collections automatically

### Assistant with Memory & Tools

```clojure
(require '[langchain4clj.assistant :as assistant])

;; Create an assistant with memory and tools
(def my-assistant
  (assistant/create-assistant
    {:model model
     :tools [calculator weather-tool]
     :memory (assistant/create-memory {:max-messages 10})
     :system-message "You are a helpful assistant"}))

;; Use it - memory and tools are automatic!
(my-assistant "What's 2+2?")
;; => "2 + 2 equals 4"

(my-assistant "What's the weather in Tokyo?")
;; => "The weather in Tokyo is currently..."

;; Memory persists between calls
(my-assistant "My name is Alice")
(my-assistant "What's my name?")
;; => "Your name is Alice"
```

### Structured Output

```clojure
(require '[langchain4clj.structured :as structured])

;; Define a structured type
(structured/defstructured Recipe
  {:name :string
   :ingredients [:vector :string]
   :prep-time :int})

;; Get structured data automatically
(get-recipe model "Create a pasta recipe")
;; => {:name "Spaghetti Carbonara"
;;     :ingredients ["spaghetti" "eggs" "bacon" "cheese"]
;;     :prep-time 20}
```

## Advanced Examples

### Multi-Agent System

```clojure
(require '[langchain4clj.agents :as agents])

;; Create specialized agents
(def researcher (agents/create-agent {:model model :role "researcher"}))
(def writer (agents/create-agent {:model model :role "writer"}))
(def editor (agents/create-agent {:model model :role "editor"}))

;; Chain them together
(def blog-pipeline
  (agents/chain researcher writer editor))

(blog-pipeline "Write about quantum computing")
;; Each agent processes in sequence
```

### Provider Failover & Resilience

Build production-ready systems with automatic failover between LLM providers:

```clojure
(require '[langchain4clj.resilience :as resilience])

;; Basic failover with retry
(def resilient-model
  (resilience/create-resilient-model
    {:primary (llm/create-model {:provider :openai :api-key "..."})
     :fallbacks [(llm/create-model {:provider :anthropic :api-key "..."})
                 (llm/create-model {:provider :ollama})]
     :max-retries 2           ; Retry on rate limits/timeouts
     :retry-delay-ms 1000}))  ; 1 second between retries

;; Add circuit breaker for production
(def production-model
  (resilience/create-resilient-model
    {:primary (llm/create-model {:provider :openai :api-key "..."})
     :fallbacks [(llm/create-model {:provider :anthropic :api-key "..."})
                 (llm/create-model {:provider :ollama})]
     :max-retries 2
     :retry-delay-ms 1000
     :circuit-breaker? true    ; Enable circuit breaker
     :failure-threshold 5       ; Open after 5 failures
     :success-threshold 2       ; Close after 2 successes
     :timeout-ms 60000}))       ; Try half-open after 60s

;; Use like any other model - automatic failover on errors!
(llm/chat production-model "Explain quantum computing")
;; Tries: OpenAI (with retries + CB) -> Anthropic (with retries + CB) -> Ollama

;; Works with all features: tools, streaming, JSON mode, etc.
(llm/chat production-model "Calculate 2+2" {:tools [calculator]})
```

**Error Handling:**

- Retryable errors (429, 503, timeout) - Retry on same provider
- Recoverable errors (401, 404, connection) - Try next provider
- Non-recoverable errors (400, quota) - Throw immediately

**Circuit Breaker States:**

- Closed - Normal operation, all requests pass through
- Open - Too many failures, provider temporarily skipped
- Half-Open - Testing recovery after timeout

**Why use failover?**

- High availability - Never down due to single provider issues
- Cost optimization - Use cheaper fallbacks when primary fails
- Zero vendor lock-in - Switch providers seamlessly
- Production-ready - Handle rate limits and outages gracefully
- Circuit breaker - Prevent cascading failures in production

**Complete Guide:** See [docs/RESILIENCE.md](docs/RESILIENCE.md) for comprehensive documentation including:

- Detailed configuration reference
- Production examples
- Monitoring & troubleshooting
- Best practices
- Advanced topics (streaming, tools, JSON mode)

### Custom Tool with Validation

```clojure
;; Simple inline validation with deftool (RECOMMENDED)
(tools/deftool calculator
  "Performs mathematical calculations with optional precision"
  {:expression string?
   :precision int?}  ; Optional params supported!
  [{:keys [expression precision] :or {precision 2}}]
  (let [result (eval (read-string expression))]
    (format (str "%." precision "f") (double result))))

;; For complex validation, use Spec with create-tool
(s/def ::expression string?)
(s/def ::precision (s/and int? #(>= % 0) #(<= % 10)))  ; 0-10 decimal places
(s/def ::calc-params (s/keys :req-un [::expression] 
                             :opt-un [::precision]))

(def advanced-calc
  (tools/create-tool
    {:name "calculator"
     :params-schema ::calc-params
     :fn (fn [{:keys [expression precision] :or {precision 2}}]
           (let [result (eval (read-string expression))]
             (format (str "%." precision "f") (double result))))}))
```

## Documentation

- [Provider Failover & Circuit Breaker Guide](docs/RESILIENCE.md)
- [Full API Documentation](docs/API.md)
- [Tool System Guide](docs/TOOLS.md)
- [Assistant Tutorial](docs/ASSISTANT.md)
- [Examples](examples/)

## Roadmap

### In Progress

- RAG with document loaders and vector stores
- Token counting and cost estimation
- PgVector integration for production RAG

See [ROADMAP.md](ROADMAP.md) for full details.

## Contributing

We welcome contributions! Check out:

- [Open Issues](https://github.com/langchain4clj/issues)
- [Contributing Guide](CONTRIBUTING.md)
- [Code of Conduct](CODE_OF_CONDUCT.md)

## Acknowledgments

- [LangChain4j](https://github.com/langchain4j/langchain4j) - The fantastic Java library we're wrapping
- Clojure community for feedback and ideas

## License

Copyright © 2025 Fernando Olle

Distributed under the Eclipse Public License version 2.0.
