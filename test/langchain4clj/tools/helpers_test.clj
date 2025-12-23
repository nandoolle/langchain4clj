(ns langchain4clj.tools.helpers-test
  "Tests for tool registration helpers."
  (:require
   [clojure.data.json :as json]
   [clojure.test :refer [deftest is testing]]
   [langchain4clj.tools.helpers :as helpers])
  (:import
   [dev.langchain4j.agent.tool ToolSpecification ToolExecutionRequest]
   [dev.langchain4j.service.tool ToolExecutor]
   [dev.langchain4j.model.chat.request.json
    JsonObjectSchema
    JsonStringSchema
    JsonIntegerSchema
    JsonArraySchema
    JsonEnumSchema]))

;; =============================================================================
;; Test: create-tool-spec
;; =============================================================================

(deftest create-tool-spec-basic-test
  (testing "creates basic tool spec without parameters"
    (let [spec (helpers/create-tool-spec
                {:name "get_time"
                 :description "Get the current time"})]
      (is (instance? ToolSpecification spec))
      (is (= "get_time" (.name spec)))
      (is (= "Get the current time" (.description spec)))
      (is (nil? (.parameters spec))))))

(deftest create-tool-spec-with-parameters-test
  (testing "creates tool spec with object parameters"
    (let [spec (helpers/create-tool-spec
                {:name "get_weather"
                 :description "Get weather for a location"
                 :parameters {:type :object
                              :properties {:location {:type :string
                                                      :description "City name"}
                                           :units {:type :string}}
                              :required [:location]}})]
      (is (instance? ToolSpecification spec))
      (is (= "get_weather" (.name spec)))
      (is (instance? JsonObjectSchema (.parameters spec)))))

  (testing "creates tool spec with enum parameter"
    (let [spec (helpers/create-tool-spec
                {:name "set_temp"
                 :description "Set temperature units"
                 :parameters {:type :object
                              :properties {:units {:enum ["celsius" "fahrenheit"]}}
                              :required [:units]}})]
      (is (instance? ToolSpecification spec))
      (is (instance? JsonObjectSchema (.parameters spec)))))

  (testing "creates tool spec with array parameter"
    (let [spec (helpers/create-tool-spec
                {:name "process_items"
                 :description "Process a list of items"
                 :parameters {:type :object
                              :properties {:items {:type :array
                                                   :items {:type :string}}}
                              :required [:items]}})]
      (is (instance? ToolSpecification spec)))))

(deftest create-tool-spec-validation-test
  (testing "throws on missing name"
    (is (thrown? AssertionError
                 (helpers/create-tool-spec
                  {:description "Missing name"}))))

  (testing "throws on missing description"
    (is (thrown? AssertionError
                 (helpers/create-tool-spec
                  {:name "test"})))))

;; =============================================================================
;; Test: create-tool-executor
;; =============================================================================

(defn- make-request
  "Helper to create a ToolExecutionRequest for testing."
  [name args-map]
  (-> (ToolExecutionRequest/builder)
      (.id "test-id")
      (.name name)
      (.arguments (json/write-str args-map))
      (.build)))

(deftest create-tool-executor-basic-test
  (testing "executes function with parsed arguments"
    (let [executor (helpers/create-tool-executor
                    (fn [{:keys [x y]}]
                      (+ x y)))
          request (make-request "add" {:x 2 :y 3})
          result (.execute executor request nil)]
      (is (= "5" result))))

  (testing "returns string results as-is"
    (let [executor (helpers/create-tool-executor
                    (fn [{:keys [name]}]
                      (str "Hello, " name "!")))
          request (make-request "greet" {:name "World"})
          result (.execute executor request nil)]
      (is (= "Hello, World!" result))))

  (testing "converts nil to \"null\""
    (let [executor (helpers/create-tool-executor (fn [_] nil))
          request (make-request "noop" {})
          result (.execute executor request nil)]
      (is (= "null" result))))

  (testing "JSON encodes complex results"
    (let [executor (helpers/create-tool-executor
                    (fn [_]
                      {:status "ok"
                       :data [1 2 3]}))
          request (make-request "complex" {})
          result (.execute executor request nil)
          parsed (json/read-str result :key-fn keyword)]
      (is (= {:status "ok" :data [1 2 3]} parsed)))))

(deftest create-tool-executor-options-test
  (testing "skip argument parsing when parse-args? is false"
    (let [received-args (atom nil)
          executor (helpers/create-tool-executor
                    (fn [raw-args]
                      (reset! received-args raw-args)
                      "done")
                    {:parse-args? false})
          request (make-request "test" {:key "value"})
          _ (.execute executor request nil)]
      (is (string? @received-args))
      (is (= {:key "value"} (json/read-str @received-args :key-fn keyword)))))

  (testing "handles empty arguments"
    (let [executor (helpers/create-tool-executor
                    (fn [args]
                      (count args)))
          request (-> (ToolExecutionRequest/builder)
                      (.id "test")
                      (.name "test")
                      (.arguments "")
                      (.build))
          result (.execute executor request nil)]
      (is (= "0" result)))))

;; =============================================================================
;; Test: create-safe-executor
;; =============================================================================

(deftest create-safe-executor-test
  (testing "catches exceptions and returns error message"
    (let [executor (helpers/create-safe-executor
                    (fn [_]
                      (throw (Exception. "Something went wrong"))))
          request (make-request "fail" {})
          result (.execute executor request nil)]
      (is (string? result))
      (is (.contains result "Error: Something went wrong"))))

  (testing "custom error handler"
    (let [executor (helpers/create-safe-executor
                    (fn [_]
                      (throw (Exception. "Boom")))
                    {:on-error (fn [e]
                                 (str "Custom: " (.getMessage e)))})
          request (make-request "fail" {})
          result (.execute executor request nil)]
      (is (= "Custom: Boom" result))))

  (testing "returns normal results when no exception"
    (let [executor (helpers/create-safe-executor
                    (fn [{:keys [x]}]
                      (* x 2)))
          request (make-request "double" {:x 21})
          result (.execute executor request nil)]
      (is (= "42" result)))))

;; =============================================================================
;; Test: deftool-from-schema
;; =============================================================================

(deftest deftool-from-schema-test
  (testing "creates complete tool with spec and executor"
    (let [tool (helpers/deftool-from-schema
                 {:name "greet"
                  :description "Greet a person"
                  :parameters {:type :object
                               :properties {:name {:type :string}}
                               :required [:name]}
                  :fn (fn [{:keys [name]}]
                        (str "Hello, " name "!"))})]
      (is (= "greet" (:name tool)))
      (is (instance? ToolSpecification (:spec tool)))
      (is (instance? ToolExecutor (:executor tool)))

      ;; Test execution
      (let [request (make-request "greet" {:name "Claude"})
            result (.execute (:executor tool) request nil)]
        (is (= "Hello, Claude!" result)))))

  (testing "throws on missing function"
    (is (thrown? AssertionError
                 (helpers/deftool-from-schema
                   {:name "test"
                    :description "Test"})))))

;; =============================================================================
;; Test: tools->map
;; =============================================================================

(deftest tools->map-test
  (testing "creates map of ToolSpecification -> ToolExecutor"
    (let [tools-map (helpers/tools->map
                     [{:name "add"
                       :description "Add two numbers"
                       :parameters {:type :object
                                    :properties {:a {:type :integer}
                                                 :b {:type :integer}}
                                    :required [:a :b]}
                       :fn (fn [{:keys [a b]}] (+ a b))}
                      {:name "multiply"
                       :description "Multiply two numbers"
                       :parameters {:type :object
                                    :properties {:a {:type :integer}
                                                 :b {:type :integer}}
                                    :required [:a :b]}
                       :fn (fn [{:keys [a b]}] (* a b))}])]
      (is (map? tools-map))
      (is (= 2 (count tools-map)))

      ;; All keys should be ToolSpecification
      (is (every? #(instance? ToolSpecification %) (keys tools-map)))

      ;; All values should be ToolExecutor
      (is (every? #(instance? ToolExecutor %) (vals tools-map)))))

  (testing "with safe mode enabled"
    (let [tools-map (helpers/tools->map
                     [{:name "risky"
                       :description "Might fail"
                       :fn (fn [_] (throw (Exception. "Failed!")))}]
                     {:safe? true})
          spec (first (keys tools-map))
          executor (get tools-map spec)
          request (make-request "risky" {})
          result (.execute executor request nil)]
      (is (.contains result "Error: Failed!")))))

;; =============================================================================
;; Test: tools->specs
;; =============================================================================

(deftest tools->specs-test
  (testing "extracts just the specifications"
    (let [specs (helpers/tools->specs
                 [{:name "tool1"
                   :description "First tool"
                   :parameters {:type :object
                                :properties {:x {:type :string}}}
                   :fn identity}
                  {:name "tool2"
                   :description "Second tool"
                   :fn identity}])]
      (is (vector? specs))
      (is (= 2 (count specs)))
      (is (every? #(instance? ToolSpecification %) specs))
      (is (= "tool1" (.name (first specs))))
      (is (= "tool2" (.name (second specs)))))))

;; =============================================================================
;; Test: find-executor
;; =============================================================================

(deftest find-executor-test
  (testing "finds executor by tool name"
    (let [tools-map (helpers/tools->map
                     [{:name "get_weather"
                       :description "Get weather"
                       :fn (fn [_] "sunny")}
                      {:name "get_time"
                       :description "Get time"
                       :fn (fn [_] "12:00")}])
          executor (helpers/find-executor tools-map "get_weather")
          request (make-request "get_weather" {})]
      (is (instance? ToolExecutor executor))
      (is (= "sunny" (.execute executor request nil)))))

  (testing "returns nil for unknown tool"
    (let [tools-map (helpers/tools->map
                     [{:name "known"
                       :description "Known tool"
                       :fn identity}])]
      (is (nil? (helpers/find-executor tools-map "unknown"))))))

;; =============================================================================
;; Integration Tests
;; =============================================================================

(deftest integration-real-world-tool-test
  (testing "calculator tool"
    (let [calc-tool (helpers/deftool-from-schema
                      {:name "calculate"
                       :description "Perform arithmetic calculations"
                       :parameters {:type :object
                                    :properties {:operation {:enum ["add" "subtract" "multiply" "divide"]}
                                                 :a {:type :number
                                                     :description "First operand"}
                                                 :b {:type :number
                                                     :description "Second operand"}}
                                    :required [:operation :a :b]}
                       :fn (fn [{:keys [operation a b]}]
                             (case operation
                               "add" (+ a b)
                               "subtract" (- a b)
                               "multiply" (* a b)
                               "divide" (/ a b)))})
          request (make-request "calculate" {:operation "multiply" :a 6 :b 7})
          result (.execute (:executor calc-tool) request nil)]
      (is (= "42" result))))

  (testing "search tool with complex response"
    (let [search-fn (fn [{:keys [query limit]}]
                      {:query query
                       :results (take (or limit 3)
                                      [{:title "Result 1" :score 0.95}
                                       {:title "Result 2" :score 0.88}
                                       {:title "Result 3" :score 0.75}
                                       {:title "Result 4" :score 0.60}])
                       :total 100})
          tools-map (helpers/tools->map
                     [{:name "search"
                       :description "Search for documents"
                       :parameters {:type :object
                                    :properties {:query {:type :string}
                                                 :limit {:type :integer}}
                                    :required [:query]}
                       :fn search-fn}])
          executor (helpers/find-executor tools-map "search")
          request (make-request "search" {:query "clojure" :limit 2})
          result (.execute executor request nil)
          parsed (json/read-str result :key-fn keyword)]
      (is (= "clojure" (:query parsed)))
      (is (= 2 (count (:results parsed))))
      (is (= 100 (:total parsed))))))
