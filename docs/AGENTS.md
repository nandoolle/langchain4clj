---
layout: default
title: Multi-Agent Systems
---

# Multi-Agent Systems

Orchestrate multiple AI agents working together.

## Quick Start

```clojure
(require '[langchain4clj.agents :as agents])

(def researcher (agents/create-agent
                  {:name "researcher"
                   :system-prompt "You are a research expert"
                   :provider :openai
                   :api-key "sk-..."}))

(def writer (agents/create-agent
              {:name "writer"
               :system-prompt "You are a skilled writer"
               :provider :openai
               :api-key "sk-..."}))

(def blog-pipeline (agents/chain researcher writer))

(agents/execute blog-pipeline "Write about quantum computing")
```

## Creating Agents

```clojure
;; Basic agent
(def agent (agents/create-agent
             {:name "agent-name"
              :description "What this agent does"
              :system-prompt "You are an expert at..."
              :provider :openai
              :api-key "your-key"
              :model "gpt-4"}))

;; With memory
(def agent (agents/create-agent
             {:name "assistant"
              :system-prompt "You are helpful"
              :memory (agents/create-memory 20)
              :provider :openai
              :api-key "sk-..."}))

;; With tools
(def agent (agents/create-agent-with-tools
             {:name "math-agent"
              :system-prompt "You are a math expert"
              :tools [calculator]
              :provider :openai
              :api-key "sk-..."}))
```

## Sequential Agents (Pipeline)

```clojure
;; Create pipeline
(def pipeline (agents/create-pipeline {:agents [agent1 agent2 agent3]}))
;; Or use chain
(def pipeline (agents/chain agent1 agent2 agent3))

;; Execute - each agent processes the previous agent's output
(agents/execute pipeline "Initial input")
```

## Parallel Agents

```clojure
;; Execute multiple agents simultaneously
(def parallel-system
  (agents/create-parallel-system
    {:agents [analyst1 analyst2 analyst3]
     :reducer (fn [results]
                {:technical (nth results 0)
                 :business (nth results 1)
                 :security (nth results 2)})}))

(agents/execute parallel-system "Evaluate adopting Kubernetes")

;; Without reducer - returns vector of results
(agents/parallel-process [agent1 agent2 agent3] "input" {})
```

## Collaborative Agents

```clojure
;; Agents share context and build on each other's work
(def collab-system
  (agents/create-collaborative-system
    {:agents [architect security-expert performance-expert]
     :coordinator lead-agent
     :shared-context {:project "payment-api"}}))

(agents/execute collab-system "Review the payment API design")
```

## Router Agents

```clojure
;; Route to appropriate specialist based on input
(def router
  (agents/create-router
    {:agents [billing-agent technical-agent general-agent]
     :route-fn (fn [input context agents]
                 (cond
                   (str/includes? input "payment") (nth agents 0)
                   (str/includes? input "error") (nth agents 1)
                   :else (nth agents 2)))
     :default-agent general-agent}))

(agents/execute router "I was charged twice")
;; => Routes to billing-agent
```

## Map-Reduce Agents

```clojure
;; Distribute work and combine results
(def doc-summarizer
  (agents/map-reduce-agents
    {:map-agents (repeat 5 summarizer)
     :reduce-agent synthesizer
     :split-fn (fn [text] (str/split text #"\n\n"))
     :combine-fn (fn [summaries] (str/join "\n\n" summaries))}))

(agents/execute doc-summarizer long-document)
```

## Common Patterns

### Content Creation Pipeline

```clojure
(def content-pipeline
  (agents/chain
    (agents/create-agent {:name "researcher" :system-prompt "Research thoroughly"})
    (agents/create-agent {:name "outliner" :system-prompt "Create outline"})
    (agents/create-agent {:name "writer" :system-prompt "Write content"})
    (agents/create-agent {:name "editor" :system-prompt "Edit for clarity"})))
```

### Debate Pattern

```clojure
(def debate-system
  (agents/create-collaborative-system
    {:agents [(agents/create-agent {:name "advocate" :system-prompt "Argue FOR"})
              (agents/create-agent {:name "critic" :system-prompt "Argue AGAINST"})]
     :coordinator (agents/create-agent {:name "judge" :system-prompt "Evaluate and decide"})}))
```

### With Retry

```clojure
(agents/with-retry agent input context 3)
```

## Agent Protocol

```clojure
(defprotocol Agent
  (process [this input context])
  (get-name [this])
  (get-description [this])
  (get-model [this]))

(agents/process agent "Hello" {})
(agents/get-name agent)
```

## Related

- [Assistant System](ASSISTANT.md) - Single-agent with tools and memory
- [Tools & Function Calling](TOOLS.md) - Giving agents capabilities
