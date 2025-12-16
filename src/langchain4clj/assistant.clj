(ns langchain4clj.assistant
  "High-level assistant abstraction, similar to LangChain4j's AiServices."
  (:require [langchain4clj.core :as core]
            [langchain4clj.tools :as tools]
            [langchain4clj.macros :as macros]
            [clojure.string :as str]
            [clojure.data.json :as json])
  (:import [dev.langchain4j.data.message UserMessage SystemMessage ToolExecutionResultMessage]
           [dev.langchain4j.model.chat ChatModel]))

(defprotocol ChatMemory
  (add-message! [this message])
  (get-messages [this])
  (clear! [this]))

(defn create-memory
  "Creates a message window memory with optional :max-messages."
  [{:keys [max-messages] :or {max-messages 10}}]
  (let [messages (atom [])]
    (reify ChatMemory
      (add-message! [_ message]
        (swap! messages
               (fn [msgs]
                 (let [updated (conj msgs message)]
                   (if (> (count updated) max-messages)
                     (vec (take-last max-messages updated))
                     updated)))))
      (get-messages [_] @messages)
      (clear! [_] (reset! messages [])))))

(defn process-template
  "Replaces {{key}} placeholders with values from variables map."
  [template variables]
  (reduce-kv
   (fn [s k v]
     (str/replace s (str "{{" (name k) "}}") (str v)))
   template
   variables))

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
  "Creates an assistant with model, tools, memory and system message."
  [{:keys [model tools memory system-message max-iterations]
    :or {memory (create-memory {})
         max-iterations 10}}]
  {:pre [(instance? ChatModel model)]}

  (let [tools (or tools [])]
    (fn assistant
      ([user-input] (assistant user-input {}))
      ([user-input {:keys [template-vars clear-memory?]}]
       (when clear-memory? (clear! memory))

       (let [processed-input (if template-vars
                               (process-template user-input template-vars)
                               user-input)
             existing-messages (get-messages memory)
             user-message (UserMessage. processed-input)
             messages (cond-> []
                        system-message (conj (SystemMessage. system-message))
                        true (into existing-messages)
                        true (conj user-message))
             original-count (count existing-messages)
             result (chat-with-tools
                     {:model model
                      :messages messages
                      :tools tools
                      :max-iterations max-iterations})]

         (let [new-messages (drop original-count (:messages result))
               messages-to-save (remove #(instance? SystemMessage %) new-messages)]
           (doseq [msg messages-to-save]
             (add-message! memory msg)))

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
  "Sets system message for assistant config."
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
