(ns langchain4clj.agents
  "Clojure wrapper for LangChain4j agent capabilities."
  (:require [langchain4clj :as llm])
  (:import [dev.langchain4j.data.message UserMessage SystemMessage AiMessage]
           [dev.langchain4j.memory.chat MessageWindowChatMemory]
           [java.util ArrayList]))

(defprotocol Agent
  (process [this input context] "Process input with context")
  (get-name [this] "Agent name")
  (get-description [this] "Agent description")
  (get-model [this] "Underlying model"))

(defprotocol MemoryProvider
  (add-message [this message] "Add message to memory")
  (get-messages [this] "Get all messages")
  (clear-memory [this] "Clear memory"))

(defprotocol Pipeline
  (execute [this input] "Execute pipeline")
  (add-agent [this agent] "Add agent")
  (get-agents [this] "Get agents"))

(defprotocol Tool
  (execute-tool [this args] "Execute tool")
  (get-tool-info [this] "Tool info"))

(defrecord ChatMemory [^MessageWindowChatMemory memory]
  MemoryProvider
  (add-message [_ message] (.add memory message))
  (get-messages [_] (vec (.messages memory)))
  (clear-memory [_] (.clear memory)))

(defn create-memory
  "Create chat memory with optional max messages."
  ([] (create-memory 10))
  ([max-messages]
   (->ChatMemory (MessageWindowChatMemory/withMaxMessages max-messages))))

(defrecord BaseAgent [name description model system-prompt memory]
  Agent
  (process [_this input context]
    (let [messages (ArrayList.)
          system-msg (when system-prompt (SystemMessage. system-prompt))
          user-msg (UserMessage. (str input))]
      (when system-msg (.add messages system-msg))
      (when memory
        (doseq [msg (get-messages memory)]
          (.add messages msg)))
      (when context
        (.add messages (SystemMessage. (str "Context: " (pr-str context)))))
      (.add messages user-msg)
      (let [chat-response (.chat model messages)
            ai-message (.aiMessage chat-response)]
        (when memory
          (add-message memory user-msg)
          (add-message memory ai-message))
        (.text ai-message))))
  (get-name [_] name)
  (get-description [_] description)
  (get-model [_] model))

(defn create-agent
  "Create agent with config: :name, :description, :system-prompt, :memory."
  [{:keys [name description system-prompt memory] :or {memory nil} :as config}]
  (let [model-config (dissoc config :name :description :system-prompt :memory)
        model (llm/create-model model-config)]
    (->BaseAgent name description model system-prompt memory)))

(defrecord AgentPipeline [agents]
  Pipeline
  (execute [_ input]
    (reduce (fn [current-input agent] (process agent current-input nil)) input agents))
  (add-agent [this agent] (update this :agents conj agent))
  (get-agents [_] agents))

(defn create-pipeline
  "Create agent pipeline."
  ([] (->AgentPipeline []))
  ([agents] (->AgentPipeline (vec agents))))

(defn chain
  "Chain agents into a pipeline."
  [& agents]
  (create-pipeline agents))

(defrecord ParallelAgentSystem [agents reducer-fn]
  Pipeline
  (execute [_ input]
    (let [futures (mapv #(future (process % input nil)) agents)
          results (mapv deref futures)]
      (if reducer-fn (reducer-fn results) results)))
  (add-agent [this agent] (update this :agents conj agent))
  (get-agents [_] agents))

(defn create-parallel-system
  "Create parallel agent system with optional :reducer."
  [{:keys [agents reducer] :or {reducer nil}}]
  (->ParallelAgentSystem (vec agents) reducer))

(defn parallel-process
  "Process input with multiple agents in parallel."
  [agents input context]
  (let [futures (mapv #(future (process % input context)) agents)]
    (mapv deref futures)))

(defrecord CollaborativeSystem [agents coordinator shared-context]
  Pipeline
  (execute [_ input]
    (let [context (atom (or shared-context {}))
          results (atom {})]
      (doseq [agent agents]
        (let [agent-name (get-name agent)
              result (process agent input @context)]
          (swap! results assoc agent-name result)
          (swap! context assoc agent-name result)))
      (if coordinator
        (let [coordinator-input {:input input :results @results :context @context}]
          (process coordinator coordinator-input nil))
        @results)))
  (add-agent [this agent] (update this :agents conj agent))
  (get-agents [_] agents))

(defn create-collaborative-system
  "Create collaborative system with :agents, optional :coordinator."
  [{:keys [agents coordinator shared-context] :or {shared-context {}}}]
  (->CollaborativeSystem (vec agents) coordinator shared-context))

(defrecord SimpleTool [name description fn]
  Tool
  (execute-tool [_ args] (fn args))
  (get-tool-info [_] {:name name :description description}))

(defn create-tool
  "Create tool with :name, :description, :fn."
  [{:keys [name description fn]}]
  (->SimpleTool name description fn))

(defrecord ToolEnabledAgent [base-agent tools tool-selector]
  Agent
  (process [_this input context]
    (if-let [tool (when tool-selector (tool-selector input tools context))]
      (let [result (execute-tool tool input)]
        (if (:pass-through-agent context)
          (process base-agent result context)
          result))
      (process base-agent input context)))
  (get-name [_] (get-name base-agent))
  (get-description [_] (get-description base-agent))
  (get-model [_] (get-model base-agent)))

(defn create-agent-with-tools
  "Create agent with tools and tool-selector function."
  [{:keys [agent tools tool-selector]}]
  (let [base-agent (if (satisfies? Agent agent) agent (create-agent agent))]
    (->ToolEnabledAgent base-agent (vec tools) tool-selector)))

(defrecord RouterAgent [agents route-fn default-agent]
  Agent
  (process [_this input context]
    (let [selected-agent (or (route-fn input context agents)
                             default-agent
                             (first agents))]
      (if selected-agent
        (process selected-agent input context)
        (throw (ex-info "No agent available for routing" {:input input :context context})))))
  (get-name [_] "Router")
  (get-description [_] "Routes requests to appropriate agents")
  (get-model [_] nil))

(defn create-router
  "Create router with :agents, :route-fn, optional :default-agent."
  [{:keys [agents route-fn default-agent]}]
  (->RouterAgent (vec agents) route-fn default-agent))

(defn with-retry
  "Execute agent with retry logic."
  [agent input context max-retries]
  (loop [attempt 1]
    (let [result (try
                   {:success true :value (process agent input context)}
                   (catch Exception e {:success false :error e}))]
      (if (:success result)
        (:value result)
        (if (< attempt max-retries)
          (do (Thread/sleep (* 1000 attempt)) (recur (inc attempt)))
          (throw (:error result)))))))

(defn with-memory
  "Wrap agent with memory management."
  [agent memory]
  (reify Agent
    (process [_ input context]
      (add-message memory (UserMessage. (str input)))
      (let [response (process agent input context)]
        (add-message memory (AiMessage. response))
        response))
    (get-name [_] (get-name agent))
    (get-description [_] (get-description agent))
    (get-model [_] (get-model agent))))

(defn compose
  "Compose agents with a composition function."
  [agents comp-fn]
  (reify Agent
    (process [_ input context]
      (let [results (mapv #(process % input context) agents)]
        (comp-fn results)))
    (get-name [_] "Composed")
    (get-description [_] "Composed agent")
    (get-model [_] nil)))

(defn map-reduce-agents
  "Map input through agents and reduce results."
  [{:keys [agents map-fn reduce-fn] :or {map-fn identity reduce-fn identity}}]
  (reify Agent
    (process [_ input context]
      (let [mapped-input (map-fn input)
            results (mapv #(process % mapped-input context) agents)]
        (reduce-fn results)))
    (get-name [_] "MapReduce")
    (get-description [_] "Map-reduce agent system")
    (get-model [_] nil)))

(defn build-messages
  "Build message list for LLM interaction."
  [{:keys [system user assistant history context]}]
  (let [messages (ArrayList.)]
    (when system (.add messages (SystemMessage. system)))
    (when history (doseq [msg history] (.add messages msg)))
    (when context (.add messages (SystemMessage. (str "Context: " (pr-str context)))))
    (when user (.add messages (UserMessage. user)))
    (when assistant (.add messages (AiMessage. assistant)))
    messages))
