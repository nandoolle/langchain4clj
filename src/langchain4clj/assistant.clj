(ns langchain4clj.assistant
  "High-level assistant abstraction, similar to LangChain4j's AiServices."
  (:require [langchain4clj.core :as core]
            [langchain4clj.tools :as tools]
            [langchain4clj.macros :as macros]
            [langchain4clj.memory.core :as mem-core]
            [clojure.string :as str]
            [clojure.data.json :as json])
  (:import [dev.langchain4j.data.message UserMessage SystemMessage ToolExecutionResultMessage]
           [dev.langchain4j.model.chat ChatModel]))

;; Re-export memory protocol for backward compatibility
(def ChatMemory mem-core/ChatMemory)
(def add-message! mem-core/add-message!)
(def get-messages mem-core/get-messages)
(def clear! mem-core/clear!)

(defn create-memory
  "Creates a message window memory with optional :max-messages.
   Uses new memory implementation with integrated token tracking."
  [{:keys [max-messages] :or {max-messages 10}}]
  (mem-core/create-basic-memory {:max-messages max-messages}))

(defn process-template
  "Replaces {{key}} placeholders with values from variables map."
  [template variables]
  (reduce-kv
   (fn [s k v]
     (str/replace s (str "{{" (name k) "}}") (str v)))
   template
   variables))

(defn- resolve-system-message
  "Resolve system-message to a string.
   
   Accepts:
   - nil -> returns nil
   - string -> returns as-is
   - function -> calls with context map and returns result
   
   Context map contains:
   - :user-input - the current user input
   - :template-vars - template variables if provided"
  [system-message context]
  (cond
    (nil? system-message) nil
    (fn? system-message) (system-message context)
    (string? system-message) system-message
    :else (str system-message)))

(defn execute-tool-calls
  "Executes tool calls from AI response."
  [tool-requests registered-tools]
  (mapv (fn [request]
          (let [tool-name (.name request)
                tool (tools/find-tool tool-name registered-tools)]
            (if tool
              (let [args-json (.arguments request)
                    args (json/read-str args-json :key-fn keyword)
                    result (tools/execute-tool tool args)]
                (ToolExecutionResultMessage/from request (pr-str result)))
              (ToolExecutionResultMessage/from request
                                               (str "Tool not found: " tool-name)))))
        tool-requests))

(defn chat-with-tools
  "Handles conversation with automatic tool execution loop."
  [{:keys [model messages tools max-iterations]
    :or {max-iterations 10}}]
  (loop [current-messages messages
         iteration 0]
    (if (>= iteration max-iterations)
      {:error "Max iterations reached"
       :messages current-messages}

      (let [response (core/chat model current-messages
                                {:tools (map :specification tools)})
            ai-message (.aiMessage response)
            tool-requests (.toolExecutionRequests ai-message)]

        (if (empty? tool-requests)
          {:result (.text ai-message)
           :messages (conj current-messages ai-message)}

          (let [tool-results (execute-tool-calls tool-requests tools)
                updated-messages (-> current-messages
                                     (conj ai-message)
                                     (into tool-results))]
            (recur updated-messages (inc iteration))))))))

(defn create-assistant
  "Creates an assistant with model, tools, memory and system message.
   
   The system message is stored in memory on first call, allowing get-messages
   to return the complete conversation history including the system prompt.
   
   Options:
   - :model - ChatModel instance (required)
   - :tools - vector of tools (optional)
   - :memory - ChatMemory instance (optional, defaults to 10 message window)
   - :system-message - string or function (optional)
     - If string: used as-is
     - If function: called with {:user-input \"...\" :template-vars {...}}
   - :max-iterations - max tool execution loops (optional, default 10)
   
   Example with dynamic system message:
   ```clojure
   (create-assistant
     {:model model
      :system-message (fn [{:keys [user-input]}]
                        (str \"Help the user with: \" user-input))})
   ```"
  [{:keys [model tools memory system-message max-iterations]
    :or {memory (create-memory {})
         max-iterations 10}}]
  {:pre [(instance? ChatModel model)]}

  (let [tools (or tools [])
        system-message-added? (atom false)]
    (fn assistant
      ([user-input] (assistant user-input {}))
      ([user-input {:keys [template-vars clear-memory?]}]
       (when clear-memory?
         (clear! memory)
         (reset! system-message-added? false))

       ;; Add system message to memory once (on first call or after clear)
       ;; System message can be string or function
       (when (and system-message (not @system-message-added?))
         (let [context {:user-input user-input
                        :template-vars template-vars}
               resolved (resolve-system-message system-message context)]
           (when resolved
             (add-message! memory (SystemMessage. resolved))))
         (reset! system-message-added? true))

       (let [processed-input (if template-vars
                               (process-template user-input template-vars)
                               user-input)
             user-message (UserMessage. processed-input)
             _ (add-message! memory user-message)
             messages (get-messages memory)
             result (chat-with-tools
                     {:model model
                      :messages messages
                      :tools tools
                      :max-iterations max-iterations})
             ;; Get only the new messages (AI response and any tool results)
             ;; by comparing with what we had before
             new-messages (drop (count messages) (:messages result))]

         ;; Save new messages (AI responses, tool results) to memory
         (doseq [msg new-messages]
           (add-message! memory msg))

         (:result result))))))

(defn assistant
  "Creates an assistant config for threading. Finalize with build-assistant."
  [config]
  (macros/with-defaults config
    {:memory (create-memory {})
     :max-iterations 10
     :tools []}))

(defn with-tools
  "Adds tools to assistant config."
  [config tools]
  (assoc config :tools tools))

(defn with-memory
  "Sets memory for assistant config."
  [config memory-instance]
  (assoc config :memory memory-instance))

(defn with-system-message
  "Sets system message for assistant config.
   
   Accepts:
   - string: static system message
   - function: (fn [{:keys [user-input template-vars]}] \"dynamic message\")
   
   Example:
   ```clojure
   ;; Static
   (with-system-message config \"You are helpful\")
   
   ;; Dynamic based on user input
   (with-system-message config 
     (fn [{:keys [user-input]}]
       (str \"Help with: \" user-input)))
   ```"
  [config system-msg]
  (assoc config :system-message system-msg))

(defn with-max-iterations
  "Sets max tool execution iterations."
  [config max-iter]
  (assoc config :max-iterations max-iter))

(defn memory
  "Creates a memory instance. Alias for create-memory."
  [opts]
  (create-memory opts))

(defn build-assistant
  "Finalizes config and returns the assistant function."
  [config]
  (create-assistant config))

(defn with-structured-output
  "Wraps an assistant to parse responses with parser-fn."
  [assistant parser-fn]
  (fn [& args]
    (let [response (apply assistant args)]
      (parser-fn response))))

(comment
  (def my-assistant
    (create-assistant
     {:model (core/create-model {:provider :openai
                                 :api-key (System/getenv "OPENAI_API_KEY")})
      :tools [(tools/create-tool
               {:name "calculator"
                :description "Performs calculations"
                :params-schema {:expression :string}
                :fn (fn [{:keys [expression]}]
                      (eval (read-string expression)))})]
      :system-message "You are a helpful assistant"
      :memory (create-memory {:max-messages 20})}))

  (my-assistant "What is 2 + 2?")

  (my-assistant "Translate '{{text}}' to {{language}}"
                {:template-vars {:text "Hello" :language "Spanish"}}))
