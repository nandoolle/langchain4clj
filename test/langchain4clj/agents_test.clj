(ns langchain4clj.agents-test
  "Tests for the multi-agent system with mocked LLM calls"
  (:require [clojure.test :refer [deftest is run-tests testing]]
            [langchain4clj.agents :as agents]
            [langchain4clj :as llm]
            [langchain4clj.test-utils :as test-utils]))

;; ============================================================================
;; Mock Helpers
;; ============================================================================

(defn create-mock-model
  "Create a mock ChatModel that returns predefined responses"
  [responses]
  (let [call-count (atom 0)]
    (test-utils/create-java-mock-chat-model
     (fn [_]
       (let [idx @call-count
             response (if (< idx (count responses))
                        (nth responses idx)
                        "Default mock response")]
         (swap! call-count inc)
         response)))))

(defn mock-llm-create-model
  "Mock function for llm/create-model"
  [_config]
  (create-mock-model ["Mocked response"]))

;; ============================================================================
;; Basic Agent Tests
;; ============================================================================

(deftest test-create-agent
  (testing "Agent creation with basic config"
    (with-redefs [llm/create-model mock-llm-create-model]
      (let [agent (agents/create-agent {:name "TestAgent"
                                        :description "Test agent"
                                        :provider :openai
                                        :api-key "mock-key"
                                        :system-prompt "You are a test agent"})]
        (is (= "TestAgent" (agents/get-name agent)))
        (is (= "Test agent" (agents/get-description agent)))))))

(deftest test-agent-processing
  (testing "Agent processes input correctly"
    (with-redefs [llm/create-model (fn [_]
                                     (create-mock-model ["Agent processed: Hello"]))]
      (let [agent (agents/create-agent {:name "TestAgent"
                                        :description "Test agent"
                                        :provider :openai
                                        :api-key "mock-key"})]
        (is (= "Agent processed: Hello"
               (agents/process agent "Hello" nil)))))))

(deftest test-agent-with-context
  (testing "Agent uses context in processing"
    (with-redefs [llm/create-model (fn [_]
                                     (create-mock-model ["Response with context"]))]
      (let [agent (agents/create-agent {:name "ContextAgent"
                                        :description "Context-aware agent"
                                        :provider :openai
                                        :api-key "mock-key"})
            context {:user "John" :task "analysis"}]
        (is (= "Response with context"
               (agents/process agent "Process this" context)))))))

;; ============================================================================
;; Specialized Agent Tests
;; ============================================================================

;; TODO: Implement specialized agent creation functions
#_(deftest test-specialized-agents
    (testing "Creating specialized agent types"
      (with-redefs [llm/create-model mock-llm-create-model]
        (let [analyst (agents/create-analyst-agent {:provider :openai :api-key "mock"})
              coder (agents/create-coder-agent {:provider :openai :api-key "mock"})
              reviewer (agents/create-reviewer-agent {:provider :openai :api-key "mock"})
              planner (agents/create-planner-agent {:provider :openai :api-key "mock"})]

          (is (= "Analyst" (agents/get-name analyst)))
          (is (= "Coder" (agents/get-name coder)))
          (is (= "Reviewer" (agents/get-name reviewer)))
          (is (= "Planner" (agents/get-name planner)))))))

;; ============================================================================
;; Memory Tests
;; ============================================================================

(deftest test-chat-memory
  (testing "Memory creation and operations"
    (let [memory (agents/create-memory 5)]
      (is (satisfies? agents/MemoryProvider memory))
      (is (empty? (agents/get-messages memory)))

      ;; Test adding messages
      (let [msg (dev.langchain4j.data.message.UserMessage. "Hello")]
        (agents/add-message memory msg)
        (is (= 1 (count (agents/get-messages memory))))

        ;; Test clearing memory
        (agents/clear-memory memory)
        (is (empty? (agents/get-messages memory)))))))

(deftest test-agent-with-memory
  (testing "Agent with memory retains conversation history"
    (with-redefs [llm/create-model (fn [_]
                                     (create-mock-model ["First response"
                                                         "Second response"]))]
      (let [memory (agents/create-memory)
            agent (agents/create-agent {:name "MemoryAgent"
                                        :description "Agent with memory"
                                        :provider :openai
                                        :api-key "mock-key"
                                        :memory memory})]

        (agents/process agent "First message" nil)
        (is (= 2 (count (agents/get-messages memory)))) ; User + Assistant message

        (agents/process agent "Second message" nil)
        (is (= 4 (count (agents/get-messages memory))))))))

;; ============================================================================
;; Pipeline Tests
;; ============================================================================

(deftest test-agent-pipeline
  (testing "Pipeline executes agents in sequence"
    (with-redefs [llm/create-model (fn [config]
                                     (case (:temperature config)
                                       0.5 (create-mock-model ["Analyzed: "])
                                       0.7 (create-mock-model ["Enhanced: Analyzed: "])
                                       0.9 (create-mock-model ["Finalized: Enhanced: Analyzed: "])
                                       (create-mock-model ["Default"])))]
      (let [agent1 (agents/create-agent {:name "Agent1"
                                         :provider :openai
                                         :api-key "mock"
                                         :temperature 0.5})
            agent2 (agents/create-agent {:name "Agent2"
                                         :provider :openai
                                         :api-key "mock"
                                         :temperature 0.7})
            agent3 (agents/create-agent {:name "Agent3"
                                         :provider :openai
                                         :api-key "mock"
                                         :temperature 0.9})
            pipeline (agents/create-pipeline [agent1 agent2 agent3])]

        (is (= 3 (count (agents/get-agents pipeline))))
        (is (= "Finalized: Enhanced: Analyzed: "
               (agents/execute pipeline "input")))))))

(deftest test-chain-function
  (testing "Chain function creates pipeline correctly"
    (with-redefs [llm/create-model mock-llm-create-model]
      (let [agent1 (agents/create-agent {:name "A1" :provider :openai :api-key "mock"})
            agent2 (agents/create-agent {:name "A2" :provider :openai :api-key "mock"})
            pipeline (agents/chain agent1 agent2)]

        (is (= 2 (count (agents/get-agents pipeline))))))))

;; ============================================================================
;; Collaborative System Tests
;; ============================================================================

(deftest test-collaborative-system
  (testing "Collaborative system with multiple agents"
    (with-redefs [llm/create-model (fn [config]
                                     (case (:model config)
                                       "analyst" (create-mock-model ["Analysis result"])
                                       "coder" (create-mock-model ["Code result"])
                                       "coordinator" (create-mock-model ["Final synthesis"])
                                       (create-mock-model ["Default"])))]
      (let [analyst (agents/create-agent {:name "Analyst"
                                          :provider :openai
                                          :api-key "mock"
                                          :model "analyst"})
            coder (agents/create-agent {:name "Coder"
                                        :provider :openai
                                        :api-key "mock"
                                        :model "coder"})
            coordinator (agents/create-agent {:name "Coordinator"
                                              :provider :openai
                                              :api-key "mock"
                                              :model "coordinator"})
            system (agents/create-collaborative-system
                    {:agents [analyst coder]
                     :coordinator coordinator})]

        (is (= "Final synthesis"
               (agents/execute system "Task to complete")))))))

(deftest test-collaborative-without-coordinator
  (testing "Collaborative system returns all results without coordinator"
    (with-redefs [llm/create-model (fn [config]
                                     (case (:model config)
                                       "a1" (create-mock-model ["Result A"])
                                       "a2" (create-mock-model ["Result B"])
                                       (create-mock-model ["Default"])))]
      (let [agent1 (agents/create-agent {:name "Agent1"
                                         :provider :openai
                                         :api-key "mock"
                                         :model "a1"})
            agent2 (agents/create-agent {:name "Agent2"
                                         :provider :openai
                                         :api-key "mock"
                                         :model "a2"})
            system (agents/create-collaborative-system
                    {:agents [agent1 agent2]})
            results (agents/execute system "Task")]

        (is (map? results))
        (is (= "Result A" (get results "Agent1")))
        (is (= "Result B" (get results "Agent2")))))))

;; ============================================================================
;; Tool Agent Tests
;; ============================================================================

(deftest test-tool-creation
  (testing "Tool creation and execution"
    (let [tool (agents/create-tool {:name "calculator"
                                    :description "Performs calculations"
                                    :fn (fn [x] (* x 2))})]
      (is (= 10 (agents/execute-tool tool 5)))
      (is (= {:name "calculator" :description "Performs calculations"}
             (agents/get-tool-info tool))))))

;; TODO: Implement create-tool-agent
#_(deftest test-tool-agent
    (testing "Agent with tools can execute them"
      (with-redefs [llm/create-model (fn [_]
                                       (create-mock-model ["TOOL: math ARGS: 10"]))]
        (let [math-tool (agents/create-tool {:name "math"
                                             :description "Math operations"
                                             :fn (fn [x] (* x x))})
              agent (agents/create-tool-agent {:name "ToolUser"
                                               :description "Uses tools"
                                               :provider :openai
                                               :api-key "mock"
                                               :tools [math-tool]})]

          (is (= "Tool math result: 100"
                 (agents/process agent "Square 10" nil)))))))

#_(deftest test-tool-agent-direct-response
    (testing "Tool agent can provide direct response without tools"
      (with-redefs [llm/create-model (fn [_]
                                       (create-mock-model ["Direct response without tools"]))]
        (let [tool (agents/create-tool {:name "tool1"
                                        :description "A tool"
                                        :fn identity})
              agent (agents/create-tool-agent {:name "ToolAgent"
                                               :provider :openai
                                               :api-key "mock"
                                               :tools [tool]})]

          (is (= "Direct response without tools"
                 (agents/process agent "Just answer directly" nil)))))))

;; ============================================================================
;; Utility Function Tests
;; ============================================================================

(deftest test-with-retry
  (testing "Retry logic on failure"
    (let [attempt-count (atom 0)
          mock-agent (reify agents/Agent
                       (process [_ _ _]
                         (swap! attempt-count inc)
                         (if (< @attempt-count 3)
                           (throw (Exception. "Temporary failure"))
                           "Success"))
                       (get-name [_] "RetryAgent"))]

      (is (= "Success" (agents/with-retry mock-agent "input" nil 5)))
      (is (= 3 @attempt-count)))))

(deftest test-parallel-process
  (testing "Parallel processing of multiple agents"
    (with-redefs [llm/create-model (fn [config]
                                     (create-mock-model [(str "Response-" (:id config))]))]
      (let [agents (map #(agents/create-agent {:name (str "Agent" %)
                                               :provider :openai
                                               :api-key "mock"
                                               :id %})
                        (range 3))
            results (agents/parallel-process agents "input" nil)]

        (is (= 3 (count results)))
        (is (every? #(re-find #"Response-\d" %) results))))))

;; TODO: Implement create-router-agent
#_(deftest test-router-agent
    (testing "Router agent selects appropriate agent"
      (with-redefs [llm/create-model (fn [config]
                                       (if (= "Router" (:name config))
                                         (create-mock-model ["Coder"])
                                         (create-mock-model ["Code generated"])))]
        (let [analyst (agents/create-agent {:name "Analyst"
                                            :description "Analyzes data"
                                            :provider :openai
                                            :api-key "mock"})
              coder (agents/create-agent {:name "Coder"
                                          :description "Writes code"
                                          :provider :openai
                                          :api-key "mock"})
              router (agents/create-router-agent {:agents [analyst coder]
                                                  :provider :openai
                                                  :api-key "mock"})]

          (is (= "Code generated"
                 (agents/process router "Write a function" nil)))))))

;; ============================================================================
;; Template Tests
;; ============================================================================

;; TODO: Implement create-from-template
#_(deftest test-agent-templates
    (testing "Agent creation from templates"
      (with-redefs [llm/create-model mock-llm-create-model]
        (let [analyst (agents/create-from-template :analyst {:provider :openai
                                                             :api-key "mock"})
              writer (agents/create-from-template :writer {:provider :openai
                                                           :api-key "mock"})]

          (is (agents/process analyst "Analyze this" nil))
          (is (agents/process writer "Write about this" nil))))))

;; ============================================================================
;; Integration Tests
;; ============================================================================

;; TODO: Implement specialized agent functions
#_(deftest test-complex-workflow
    (testing "Complex workflow with multiple agent types"
      (with-redefs [llm/create-model (fn [config]
                                       (let [name (:name config)]
                                         (cond
                                           (= name "Planner") (create-mock-model ["Step 1: Analyze\nStep 2: Code"])
                                           (= name "Analyst") (create-mock-model ["Analysis complete"])
                                           (= name "Coder") (create-mock-model ["Code complete"])
                                           (= name "Reviewer") (create-mock-model ["Review: All good"])
                                           :else (create-mock-model ["Default"]))))]

        (let [planner (agents/create-planner-agent {:provider :openai :api-key "mock"})
              analyst (agents/create-analyst-agent {:provider :openai :api-key "mock"})
              coder (agents/create-coder-agent {:provider :openai :api-key "mock"})
              reviewer (agents/create-reviewer-agent {:provider :openai :api-key "mock"})

            ;; Create a collaborative system
              system (agents/create-collaborative-system
                      {:agents [analyst coder]
                       :coordinator reviewer
                       :shared-context {:project "TestProject"}})

            ;; Plan first
              plan (agents/process planner "Create a data processing pipeline" nil)]

          (is (string? plan))
          (is (re-find #"Step" plan))

        ;; Execute the system
          (let [result (agents/execute system plan)]
            (is (= "Review: All good" result)))))))

;; Run tests
(defn run-agent-tests
  "Run all agent tests"
  []
  (run-tests 'langchain4clj.agents-test))
