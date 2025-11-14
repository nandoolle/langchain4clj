---
layout: default
title: Multi-Agent Systems
---

# Multi-Agent Systems

Create sophisticated multi-agent systems with sequential, parallel, and collaborative execution patterns.

## Overview

Multi-agent systems allow you to orchestrate multiple AI agents working together to solve complex tasks. LangChain4Clj supports:

- **Sequential Agents** - Pipeline agents that pass results to the next
- **Parallel Agents** - Execute multiple agents simultaneously
- **Collaborative Agents** - Agents working together with shared context
- **Router Agents** - Intelligent routing to specialized agents
- **Map-Reduce Agents** - Distribute work and combine results

## Quick Start

```clojure
(require '[langchain4clj.agents :as agents])
(require '[langchain4clj.core :as llm])

;; Create specialized agents
(def researcher (agents/create-agent
                  {:name "researcher"
                   :description "Research and gather information"
                   :system-prompt "You are a research expert"
                   :provider :openai
                   :api-key "sk-..."}))

(def writer (agents/create-agent
              {:name "writer"
               :description "Write engaging content"
               :system-prompt "You are a skilled writer"
               :provider :openai
               :api-key "sk-..."}))

;; Sequential pipeline
(def blog-pipeline (agents/chain researcher writer))

(agents/execute blog-pipeline "Write about quantum computing")
;; Research → Write → Final blog post
```

## Creating Agents

### Basic Agent

```clojure
(def agent (agents/create-agent
             {:name "agent-name"
              :description "What this agent does"
              :system-prompt "You are an expert at..."
              :provider :openai
              :api-key "your-key"
              :model "gpt-4"}))
```

### Agent with Memory

```clojure
(def memory (agents/create-memory 20))

(def agent (agents/create-agent
             {:name "assistant"
              :description "Helpful assistant"
              :system-prompt "You are helpful"
              :memory memory
              :provider :openai
              :api-key "sk-..."}))
```

### Agent with Tools

```clojure
(require '[langchain4clj.tools :as tools])

(tools/deftool calculator
  "Does math"
  {:expression string?}
  [{:keys [expression]}]
  (str (eval (read-string expression))))

(def agent (agents/create-agent-with-tools
             {:name "math-agent"
              :description "Solves math problems"
              :system-prompt "You are a math expert"
              :tools [calculator]
              :provider :openai
              :api-key "sk-..."}))
```

## Agent Protocol

All agents implement the `Agent` protocol:

```clojure
(defprotocol Agent
  (process [this input context])
  (get-name [this])
  (get-description [this])
  (get-model [this]))

;; Using the protocol
(agents/process agent "Hello" {})
(agents/get-name agent)         ;; => "agent-name"
(agents/get-description agent)  ;; => "What this agent does"
```

## Sequential Agents (Pipeline)

### Creating Pipelines

```clojure
;; Method 1: create-pipeline
(def pipeline (agents/create-pipeline 
                {:agents [agent1 agent2 agent3]}))

;; Method 2: chain convenience function
(def pipeline (agents/chain agent1 agent2 agent3))
```

### Executing Pipelines

```clojure
(agents/execute pipeline "Initial input")
;; Agent1 processes input
;; Agent2 processes Agent1's output
;; Agent3 processes Agent2's output
;; Returns final result
```

### Use Case: Content Creation Pipeline

```clojure
(def researcher (agents/create-agent
                  {:name "researcher"
                   :description "Researches topics"
                   :system-prompt "Research the topic thoroughly. Provide key facts and insights."
                   :provider :openai
                   :api-key "sk-..."}))

(def outliner (agents/create-agent
                {:name "outliner"
                 :description "Creates outlines"
                 :system-prompt "Create a detailed outline from the research."
                 :provider :openai
                 :api-key "sk-..."}))

(def writer (agents/create-agent
              {:name "writer"
               :description "Writes content"
               :system-prompt "Write engaging content following the outline."
               :provider :openai
               :api-key "sk-..."}))

(def editor (agents/create-agent
              {:name "editor"
               :description "Edits and polishes"
               :system-prompt "Edit for clarity, grammar, and style."
               :provider :openai
               :api-key "sk-..."}))

(def content-pipeline (agents/chain researcher outliner writer editor))

(def blog-post (agents/execute content-pipeline "Explain quantum computing"))
;; => Fully researched, outlined, written, and edited blog post
```

## Parallel Agents

### Creating Parallel Systems

```clojure
(def parallel-system
  (agents/create-parallel-system
    {:agents [agent1 agent2 agent3]
     :reducer (fn [results]
                ;; Combine results from all agents
                (str/join "\n\n" results))}))
```

### Without Reducer

```clojure
(def parallel-system
  (agents/create-parallel-system
    {:agents [agent1 agent2 agent3]}))

;; Returns vector of results
(agents/execute parallel-system "input")
;; => ["result1" "result2" "result3"]
```

### Direct Parallel Processing

```clojure
(agents/parallel-process [agent1 agent2 agent3] "input" {})
;; Executes all agents in parallel, returns vector of results
```

### Use Case: Multi-Perspective Analysis

```clojure
(def technical-analyst (agents/create-agent
                         {:name "technical"
                          :system-prompt "Analyze from a technical perspective"
                          :provider :openai
                          :api-key "sk-..."}))

(def business-analyst (agents/create-agent
                        {:name "business"
                         :system-prompt "Analyze from a business perspective"
                         :provider :openai
                         :api-key "sk-..."}))

(def security-analyst (agents/create-agent
                        {:name "security"
                         :system-prompt "Analyze from a security perspective"
                         :provider :openai
                         :api-key "sk-..."}))

(def analysis-system
  (agents/create-parallel-system
    {:agents [technical-analyst business-analyst security-analyst]
     :reducer (fn [results]
                {:technical (nth results 0)
                 :business (nth results 1)
                 :security (nth results 2)})}))

(def analysis (agents/execute analysis-system "Evaluate adopting Kubernetes"))
;; => {:technical "..." :business "..." :security "..."}
```

## Collaborative Agents

### Creating Collaborative Systems

```clojure
(def collab-system
  (agents/create-collaborative-system
    {:agents [agent1 agent2 agent3]
     :coordinator coordinator-agent  ;; Optional
     :shared-context {:topic "AI"}})) ;; Optional
```

### Without Coordinator

```clojure
(def collab-system
  (agents/create-collaborative-system
    {:agents [expert1 expert2 expert3]
     :shared-context {}}))

;; Agents share context and build on each other's work
(agents/execute collab-system "Complex problem")
```

### With Coordinator

```clojure
(def coordinator (agents/create-agent
                   {:name "coordinator"
                    :description "Coordinates team"
                    :system-prompt "Synthesize inputs from all team members"
                    :provider :openai
                    :api-key "sk-..."}))

(def collab-system
  (agents/create-collaborative-system
    {:agents [specialist1 specialist2 specialist3]
     :coordinator coordinator}))
```

### Use Case: Software Design Review

```clojure
(def architect (agents/create-agent
                 {:name "architect"
                  :description "System architect"
                  :system-prompt "Focus on architecture and scalability"
                  :provider :openai
                  :api-key "sk-..."}))

(def security-expert (agents/create-agent
                       {:name "security"
                        :description "Security expert"
                        :system-prompt "Focus on security vulnerabilities"
                        :provider :openai
                        :api-key "sk-..."}))

(def performance-expert (agents/create-agent
                          {:name "performance"
                           :description "Performance expert"
                           :system-prompt "Focus on performance bottlenecks"
                           :provider :openai
                           :api-key "sk-..."}))

(def lead (agents/create-agent
            {:name "tech-lead"
             :description "Technical lead"
             :system-prompt "Synthesize feedback and make recommendations"
             :provider :openai
             :api-key "sk-..."}))

(def design-review
  (agents/create-collaborative-system
    {:agents [architect security-expert performance-expert]
     :coordinator lead
     :shared-context {:project "payment-api"}}))

(def review (agents/execute design-review "Review the payment API design"))
;; All experts provide input, lead synthesizes recommendations
```

## Router Agents

### Creating Routers

```clojure
(def router
  (agents/create-router
    {:agents [specialist1 specialist2 specialist3]
     :route-fn (fn [input context agents]
                 ;; Return the appropriate agent
                 (cond
                   (str/includes? input "code") (nth agents 0)
                   (str/includes? input "design") (nth agents 1)
                   :else (nth agents 2)))
     :default-agent fallback-agent}))  ;; Optional
```

### Smart Routing with LLM

```clojure
(def routing-agent (agents/create-agent
                     {:name "router"
                      :description "Routes requests"
                      :system-prompt "Analyze the request and return which specialist to use: code, design, or database"
                      :provider :openai
                      :api-key "sk-..."}))

(defn smart-route-fn [input context agents]
  (let [decision (agents/process routing-agent input context)
        specialist-name (str/lower-case (str/trim decision))]
    (case specialist-name
      "code" (nth agents 0)
      "design" (nth agents 1)
      "database" (nth agents 2)
      (nth agents 0))))  ;; default

(def smart-router
  (agents/create-router
    {:agents [code-expert design-expert db-expert]
     :route-fn smart-route-fn}))
```

### Use Case: Customer Support Routing

```clojure
(def billing-agent (agents/create-agent
                     {:name "billing"
                      :description "Handles billing questions"
                      :system-prompt "You are a billing specialist"
                      :provider :openai
                      :api-key "sk-..."}))

(def technical-agent (agents/create-agent
                       {:name "technical"
                        :description "Handles technical issues"
                        :system-prompt "You are a technical support specialist"
                        :provider :openai
                        :api-key "sk-..."}))

(def general-agent (agents/create-agent
                     {:name "general"
                      :description "Handles general questions"
                      :system-prompt "You are a customer service representative"
                      :provider :openai
                      :api-key "sk-..."}))

(defn route-customer-query [input context agents]
  (cond
    (or (str/includes? (str/lower-case input) "payment")
        (str/includes? (str/lower-case input) "bill")
        (str/includes? (str/lower-case input) "charge"))
    (nth agents 0)  ;; billing
    
    (or (str/includes? (str/lower-case input) "error")
        (str/includes? (str/lower-case input) "not working")
        (str/includes? (str/lower-case input) "bug"))
    (nth agents 1)  ;; technical
    
    :else
    (nth agents 2)))  ;; general

(def support-router
  (agents/create-router
    {:agents [billing-agent technical-agent general-agent]
     :route-fn route-customer-query}))

(agents/execute support-router "I was charged twice for my subscription")
;; => Routes to billing-agent
```

## Map-Reduce Agents

### Creating Map-Reduce Systems

```clojure
(def map-reduce-system
  (agents/map-reduce-agents
    {:map-agents [agent1 agent2 agent3]
     :reduce-agent reducer-agent
     :split-fn (fn [input] 
                 ;; Split input into chunks
                 (str/split input #"\n\n"))
     :combine-fn (fn [results]
                   ;; Combine results
                   (str/join "\n" results))}))
```

### Use Case: Document Summarization

```clojure
(def summarizer (agents/create-agent
                  {:name "summarizer"
                   :description "Summarizes text"
                   :system-prompt "Summarize the text concisely"
                   :provider :openai
                   :api-key "sk-..."}))

(def synthesizer (agents/create-agent
                   {:name "synthesizer"
                    :description "Synthesizes summaries"
                    :system-prompt "Combine summaries into one coherent summary"
                    :provider :openai
                    :api-key "sk-..."}))

(defn split-document [text]
  ;; Split into paragraphs
  (str/split text #"\n\n"))

(def doc-summarizer
  (agents/map-reduce-agents
    {:map-agents (repeat 5 summarizer)  ;; 5 parallel summarizers
     :reduce-agent synthesizer
     :split-fn split-document
     :combine-fn (fn [summaries]
                   (str "SUMMARIES:\n" (str/join "\n\n" summaries)))}))

(def summary (agents/execute doc-summarizer long-document))
;; Each paragraph summarized in parallel, then synthesized
```

## Advanced Patterns

### Retry with Agents

```clojure
(agents/with-retry agent input context 3)
;; Retries up to 3 times on failure
```

### Agent Composition

```clojure
(defn compose-agents [agents combine-fn]
  (agents/compose agents combine-fn))

(def composed (compose-agents
                [agent1 agent2 agent3]
                (fn [results]
                  {:combined (str/join " " results)})))
```

### Wrapping Agents with Memory

```clojure
(def memory (agents/create-memory 10))

(def agent-with-memory (agents/with-memory agent memory))

;; Agent now has conversation memory
(agents/process agent-with-memory "Hello" {})
(agents/process agent-with-memory "What did I just say?" {})
;; => "You said 'Hello'"
```

### Building Message Lists

```clojure
(def messages (agents/build-messages
                {:system "You are helpful"
                 :user "Hello"
                 :assistant "Hi!"
                 :user "How are you?"}))
```

## Context and State

### Shared Context

```clojure
(def initial-context {:project "api-rewrite"
                      :budget 50000
                      :deadline "Q2"})

;; Pass context through pipeline
(agents/execute pipeline "Plan the project" initial-context)

;; Each agent can read and update context
(defn process-with-context [agent input context]
  (let [result (agents/process agent input context)
        updated-context (assoc context :last-agent (agents/get-name agent))]
    {:result result :context updated-context}))
```

### Stateful Agents

```clojure
(defrecord StatefulAgent [name model state-atom]
  agents/Agent
  (process [this input context]
    (swap! state-atom update :call-count inc)
    (let [result (llm/chat model input)]
      (swap! state-atom assoc :last-input input)
      result))
  (get-name [this] name)
  (get-description [this] "Stateful agent")
  (get-model [this] model))

(def stateful (->StatefulAgent "counter" model (atom {:call-count 0})))

(agents/process stateful "Hello" {})
@(:state-atom stateful)
;; => {:call-count 1 :last-input "Hello"}
```

## Error Handling

### Agent Failures

```clojure
(try
  (agents/execute pipeline "input")
  (catch Exception e
    (println "Agent failed:" (.getMessage e))
    (println "Failed at agent:" (:agent (ex-data e)))
    (use-fallback)))
```

### Partial Results

```clojure
(defn execute-with-fallback [pipeline input]
  (try
    (agents/execute pipeline input)
    (catch Exception e
      ;; Return partial results if available
      (or (:partial-result (ex-data e))
          "Operation failed"))))
```

### Agent Timeouts

```clojure
(defn execute-with-timeout [agent input timeout-ms]
  (let [result (promise)]
    (future
      (deliver result (agents/process agent input {})))
    (deref result timeout-ms :timeout)))
```

## Best Practices

### 1. Design Clear Agent Roles

```clojure
;; ❌ Vague
(agents/create-agent {:name "agent1" :system-prompt "Do stuff"})

;; ✅ Specific
(agents/create-agent
  {:name "code-reviewer"
   :system-prompt "Review code for bugs, performance issues, and best practices. Focus on actionable feedback."})
```

### 2. Use Appropriate Patterns

```clojure
;; Sequential: When order matters
(agents/chain research → write → edit)

;; Parallel: When order doesn't matter
(agents/parallel [analyst1 analyst2 analyst3])

;; Collaborative: When agents need to build on each other
(agents/collaborative [expert1 expert2] coordinator)

;; Router: When input determines which agent to use
(agents/router [specialist1 specialist2])
```

### 3. Keep Context Lightweight

```clojure
;; ❌ Too much in context
{:full-document long-text
 :entire-history [...1000 messages...]
 :all-data {...massive-object...}}

;; ✅ Essential only
{:document-id "doc-123"
 :summary "Brief summary"
 :stage "review"}
```

### 4. Handle Failures Gracefully

```clojure
(defn robust-execute [pipeline input]
  (try
    (agents/execute pipeline input)
    (catch Exception e
      (log/error e "Pipeline failed")
      {:success false
       :error (.getMessage e)
       :fallback (generate-fallback-response)})))
```

### 5. Monitor Agent Performance

```clojure
(defn timed-process [agent input context]
  (let [start (System/currentTimeMillis)
        result (agents/process agent input context)
        elapsed (- (System/currentTimeMillis) start)]
    (log/info (str "Agent " (agents/get-name agent) " took " elapsed "ms"))
    result))
```

## Common Patterns

### Debate Pattern

Agents argue different sides, coordinator decides:

```clojure
(def for-agent (agents/create-agent
                 {:name "advocate"
                  :system-prompt "Argue FOR the proposal"}))

(def against-agent (agents/create-agent
                     {:name "critic"
                      :system-prompt "Argue AGAINST the proposal"}))

(def judge (agents/create-agent
             {:name "judge"
              :system-prompt "Evaluate both arguments and decide"}))

(def debate-system
  (agents/create-collaborative-system
    {:agents [for-agent against-agent]
     :coordinator judge}))
```

### Hierarchical Pattern

Manager delegates to specialists:

```clojure
(def manager (agents/create-router
               {:agents [specialist1 specialist2 specialist3]
                :route-fn smart-routing-fn}))

;; Manager decides which specialist handles each task
```

### Iterative Refinement

Agent improves output through multiple passes:

```clojure
(def refiner (agents/create-agent {...}))

(loop [output initial-output
       iterations 0]
  (if (or (good-enough? output) (>= iterations 5))
    output
    (recur (agents/process refiner (str "Improve: " output) {})
           (inc iterations))))
```

## See Also

- [Assistant System](ASSISTANT.md) - Single-agent with tools and memory
- [Tools & Function Calling](TOOLS.md) - Giving agents capabilities
- [Resilience & Failover](RESILIENCE.md) - Making agents robust
