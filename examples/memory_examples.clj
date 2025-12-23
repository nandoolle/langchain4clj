(ns memory-examples
  "Practical examples of memory usage in langchain4clj."
  (:require [langchain4clj.memory :as mem]
            [langchain4clj.assistant :as asst]
            [langchain4clj.core :as llm])
  (:import [dev.langchain4j.data.message UserMessage SystemMessage]))

;; =============================================================================
;; Example 1: Simple Chat Bot with Memory
;; =============================================================================

(comment
  (def simple-bot
    (asst/create-assistant
      {:model (llm/create-model {:provider :openai
                                 :api-key (System/getenv "OPENAI_API_KEY")})
       :memory (mem/create-memory {:max-messages 20})}))

  (simple-bot "Hi, my name is Alice")
  (simple-bot "What's my name?")
  )

;; =============================================================================
;; Example 2: Production Agent with Token Limits
;; =============================================================================

(comment
  (def production-memory
    (-> (mem/create-memory {:max-messages 100})
        (mem/with-auto-reset {:reset-threshold 0.85
                              :max-tokens 16000
                              :context [(SystemMessage. "You are a coding assistant")]})))

  (def coding-assistant
    (asst/create-assistant
      {:model (llm/create-model {:provider :openai
                                 :api-key (System/getenv "OPENAI_API_KEY")
                                 :model "gpt-4"})
       :memory production-memory}))

  (coding-assistant "How do I create a Clojure atom?")
  
  (mem/stats production-memory)
  )

;; =============================================================================
;; Example 3: Multi-Tenant Service with Stateless Memory
;; =============================================================================

(comment
  (mem/defmemory service-memory
    :max-messages 50
    :stateless {:context [(SystemMessage. "You are a customer service bot")]})

  (defn handle-customer-request [customer-id query]
    (mem/clear! service-memory)
    (let [response (llm/chat service-model query)]
      {:customer-id customer-id
       :response (.text (.aiMessage response))}))

  (handle-customer-request "user-123" "What are your hours?")
  (handle-customer-request "user-456" "How do I return an item?")
  )

;; =============================================================================
;; Example 4: Context-Aware Agent with Auto-Reset
;; =============================================================================

(comment
  (def project-context
    [(SystemMessage. "Project: langchain4clj")
     (SystemMessage. "Tech stack: Clojure, LangChain4j")])

  (mem/defmemory agent-memory
    :max-messages 50
    :auto-reset {:threshold 0.9
                 :max-tokens 8000
                 :context project-context})

  (def project-agent
    (asst/create-assistant
      {:model (llm/create-model {:provider :openai
                                 :api-key (System/getenv "OPENAI_API_KEY")})
       :memory agent-memory}))

  (dotimes [i 30]
    (project-agent (str "Question " i)))

  (take 3 (mem/get-messages agent-memory))
  )

;; =============================================================================
;; Example 5: Token Tracking for Cost Monitoring
;; =============================================================================

(comment
  (def monitored-memory (mem/create-memory {:max-messages 100}))

  (defn chat-with-tracking [prompt]
    (let [response (llm/chat model prompt)]
      (mem/add-message! monitored-memory (UserMessage. prompt))
      (mem/add-message! monitored-memory
                        (.aiMessage response)
                        {:token-usage (-> response .metadata .tokenUsage)})
      (.text (.aiMessage response))))

  (chat-with-tracking "Explain quantum computing")

  (let [stats (mem/stats monitored-memory)]
    {:tokens (:token-count stats)
     :estimated-cost (* (:token-count stats) 0.00003)})
  )

;; =============================================================================
;; Example 6: Filtering Conversation History
;; =============================================================================

(comment
  (mem/get-messages conversation-memory {:type UserMessage})
  (mem/get-messages conversation-memory {:limit 5})
  (mem/get-messages conversation-memory {:from-index 10})
  
  (->> (mem/get-messages conversation-memory {:type UserMessage})
       (take-last 3))
  )

;; =============================================================================
;; Example 7: Using defmemory Macro
;; =============================================================================

(comment
  (mem/defmemory my-simple-memory
    :max-messages 30)

  (mem/defmemory my-smart-memory
    :max-messages 100
    :auto-reset {:threshold 0.85 :max-tokens 16000})

  (mem/defmemory my-stateless-memory
    :max-messages 50
    :stateless {:context [(SystemMessage. "Bot rules")]})
  )

;; =============================================================================
;; Example 8: Threading-First Configuration
;; =============================================================================

(comment
  (def threaded-memory
    (-> {}
        (mem/with-max-messages 100)
        (mem/with-context [(SystemMessage. "Context")])
        mem/create-memory
        (mem/with-auto-reset {:threshold 0.85})))

  (def threaded-assistant
    (-> {:model (llm/create-model {:provider :openai
                                   :api-key (System/getenv "OPENAI_API_KEY")})}
        (asst/with-memory threaded-memory)
        (asst/with-system-message "You are helpful")
        (asst/build-assistant)))
  )

;; =============================================================================
;; Best Practices
;; =============================================================================

;; 1. Set max-messages based on model context window
;;    - GPT-4 (8k): 50-100 messages
;;    - GPT-4 (32k): 200-400 messages
;;    - Claude (100k): 500-1000 messages

;; 2. Use conservative reset thresholds
;;    - Recommended: 0.85 (85%)
;;    - Avoid: >0.95 (causes abrupt context loss)

;; 3. Always preserve critical context in auto-reset
;;    - System prompts, user preferences, session info

;; 4. Track tokens in production for cost monitoring
;;    - Pass :token-usage metadata from ChatResponse

;; 5. Use stateless mode for multi-tenant services
;;    - Prevents context leakage between users
;;    - Remember to clear! between sessions

;; 6. Choose limits based on both messages AND tokens
;;    - Message count alone can be misleading
;;    - Some messages are much longer than others
