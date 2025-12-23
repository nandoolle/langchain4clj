# Environment Variable Resolution

langchain4clj supports automatic resolution of environment variables in configuration maps using the `[:env "VAR_NAME"]` pattern.

## Overview

Instead of hardcoding API keys or sensitive values, you can reference environment variables that will be resolved at runtime:

```clojure
(require '[langchain4clj.core :as lc])

;; Environment variable reference
(lc/create-model {:provider :openai
                  :api-key [:env "OPENAI_API_KEY"]
                  :model "gpt-4o"})

;; Direct value (also works)
(lc/create-model {:provider :openai
                  :api-key "sk-..."
                  :model "gpt-4o"})
```

## The `[:env "VAR"]` Pattern

The pattern `[:env "VAR_NAME"]` is a vector with two elements:
1. The keyword `:env`
2. A string with the environment variable name

```clojure
[:env "OPENAI_API_KEY"]     ;; Resolves to value of $OPENAI_API_KEY
[:env "ANTHROPIC_API_KEY"]  ;; Resolves to value of $ANTHROPIC_API_KEY
[:env "CUSTOM_BASE_URL"]    ;; Works with any env var
```

## Automatic Resolution

`create-model` automatically resolves all `[:env ...]` patterns before building the model:

```clojure
;; This config with env refs...
(lc/create-model {:provider :openai
                  :api-key [:env "OPENAI_API_KEY"]
                  :base-url [:env "OPENAI_BASE_URL"]
                  :model "gpt-4o"})

;; ...is equivalent to:
(lc/create-model {:provider :openai
                  :api-key (System/getenv "OPENAI_API_KEY")
                  :base-url (System/getenv "OPENAI_BASE_URL")
                  :model "gpt-4o"})
```

## Manual Resolution

You can also resolve env refs manually using `resolve-env-refs`:

```clojure
(lc/resolve-env-refs {:api-key [:env "OPENAI_API_KEY"]})
;; => {:api-key "sk-actual-key-value"}

;; Nested maps are resolved recursively
(lc/resolve-env-refs {:config {:api-key [:env "KEY"] :other "value"}})
;; => {:config {:api-key "resolved" :other "value"}}

;; Sequential collections too
(lc/resolve-env-refs [{:a [:env "VAR1"]} {:b [:env "VAR2"]}])
;; => [{:a "value1"} {:b "value2"}]
```

## Testing with Environment Overrides

For testing, use the `*env-overrides*` dynamic var to mock environment variables:

```clojure
(binding [lc/*env-overrides* {"OPENAI_API_KEY" "test-key"
                               "CUSTOM_VAR" "test-value"}]
  (lc/resolve-env-refs {:api-key [:env "OPENAI_API_KEY"]}))
;; => {:api-key "test-key"}
```

## Usage with Presets

Env resolution works seamlessly with presets:

```clojure
(require '[langchain4clj.presets :as presets])

(lc/create-model
  (presets/get-preset :openai/gpt-4o
                      {:api-key [:env "OPENAI_API_KEY"]}))

(lc/create-model
  (presets/get-preset :anthropic/claude-sonnet-4
                      {:api-key [:env "ANTHROPIC_API_KEY"]}))
```

## Best Practices

1. **Use env refs for secrets**: API keys, tokens, and sensitive URLs
2. **Use direct values for non-secrets**: Model names, temperature, max-tokens
3. **Test with `*env-overrides*`**: Mock env vars in tests instead of setting real ones

```clojure
;; Recommended pattern
{:provider :openai
 :api-key [:env "OPENAI_API_KEY"]    ;; Secret - use env ref
 :base-url [:env "OPENAI_BASE_URL"]  ;; Optional, may vary by environment
 :model "gpt-4o"                      ;; Not secret - use direct value
 :temperature 0.7}                    ;; Not secret - use direct value
```

## Missing Environment Variables

If an environment variable is not set, `resolve-env-refs` returns `nil` for that value:

```clojure
(lc/resolve-env-refs {:api-key [:env "NONEXISTENT_VAR"]})
;; => {:api-key nil}
```

This allows the model builder to fail with a clear error message about the missing API key rather than silently using an invalid value.
