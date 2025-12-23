(ns langchain4clj.tools.helpers
  "Tool registration helpers for creating LangChain4j tools from JSON Schema definitions.
   
   This namespace provides functions to create LangChain4j ToolSpecification and
   ToolExecutor instances from Clojure data, using JSON Schema for parameter definitions.
   
   ## Why use this?
   
   When integrating with external systems that use JSON Schema for tool definitions
   (such as MCP servers, OpenAPI specs, or other protocols), this namespace bridges
   those definitions with LangChain4j's internal representations.
   
   For most use cases with langchain4clj, prefer using `deftool` from 
   `langchain4clj.tools` which provides a more idiomatic Clojure experience
   with predicate-based parameter definitions.
   
   ## Key Functions
   
   - `create-tool-spec` - Create a ToolSpecification from a map
   - `create-tool-executor` - Create a ToolExecutor from a Clojure function  
   - `tools->map` - Convert tool definitions to ToolSpecification->ToolExecutor map
   
   ## Example Usage
   
   ```clojure
   ;; Define individual tools
   (def weather-spec
     (create-tool-spec
       {:name \"get_weather\"
        :description \"Get current weather for a location\"
        :parameters {:type :object
                     :properties {:location {:type :string 
                                             :description \"City name\"}
                                  :units {:type :string
                                          :description \"celsius or fahrenheit\"}}
                     :required [:location]}}))
   
   (def weather-executor
     (create-tool-executor
       (fn [{:keys [location units]}]
         (str \"Weather in \" location \": 22°\" (or units \"C\")))))
   
   ;; Or define tools inline for AiServices
   (def my-tools
     (tools->map
       [{:name \"get_weather\"
         :description \"Get weather for a location\"
         :parameters {:type :object
                      :properties {:location {:type :string}}
                      :required [:location]}
         :fn (fn [{:keys [location]}] 
               (get-weather location))}
        {:name \"search\"
         :description \"Search the web\"
         :parameters {:type :object
                      :properties {:query {:type :string}}
                      :required [:query]}
         :fn (fn [{:keys [query]}]
               (search-web query))}]))
   ```"
  (:require
   [clojure.data.json :as json]
   [langchain4clj.schema :as schema])
  (:import
   [dev.langchain4j.agent.tool ToolSpecification ToolExecutionRequest]
   [dev.langchain4j.service.tool ToolExecutor]))

;; =============================================================================
;; Tool Specification Creation
;; =============================================================================

(defn create-tool-spec
  "Create a LangChain4j ToolSpecification from a Clojure map.
   
   The map should contain:
   - `:name` - Tool name (string, required)
   - `:description` - Tool description (string, required)
   - `:parameters` - JSON Schema EDN for parameters (map, optional)
   
   If `:parameters` is already a JsonSchema object, it's used directly.
   Otherwise, it's converted using `langchain4clj.schema/edn->json-schema`.
   
   Examples:
   ```clojure
   ;; Simple tool without parameters
   (create-tool-spec
     {:name \"get_time\"
      :description \"Get the current time\"})
   
   ;; Tool with JSON Schema parameters
   (create-tool-spec
     {:name \"get_weather\"
      :description \"Get weather for a location\"
      :parameters {:type :object
                   :properties {:location {:type :string 
                                           :description \"City name\"}
                                :units {:enum [\"celsius\" \"fahrenheit\"]}}
                   :required [:location]}})
   ```"
  [{:keys [name description parameters]}]
  {:pre [(string? name) (string? description)]}
  (let [builder (-> (ToolSpecification/builder)
                    (.name name)
                    (.description description))]
    (when parameters
      (let [json-schema (if (instance? dev.langchain4j.model.chat.request.json.JsonSchema parameters)
                          parameters
                          (schema/edn->json-schema parameters))]
        (.parameters builder json-schema)))
    (.build builder)))

;; =============================================================================
;; Tool Executor Creation
;; =============================================================================

(defn- parse-arguments
  "Parse JSON arguments string to Clojure map with keyword keys."
  [^String args-json]
  (if (or (nil? args-json) (empty? args-json))
    {}
    (json/read-str args-json :key-fn keyword)))

(defn- result->string
  "Convert a result to string for LLM consumption.
   
   - nil -> \"null\"
   - String -> as-is
   - Other -> JSON encoded"
  ^String [result]
  (cond
    (nil? result) "null"
    (string? result) result
    :else (json/write-str result)))

(defn create-tool-executor
  "Create a LangChain4j ToolExecutor from a Clojure function.
   
   The function receives parsed arguments as a Clojure map with keyword keys.
   The return value is converted to a string for the LLM:
   - Strings are returned as-is
   - nil becomes \"null\"
   - Other values are JSON encoded
   
   Options (passed as second argument map):
   - `:parse-args?` - If true (default), parse JSON args to EDN map
   
   Examples:
   ```clojure
   ;; Simple executor
   (create-tool-executor
     (fn [{:keys [location]}]
       (str \"Weather in \" location \": sunny, 22°C\")))
   
   ;; Return structured data (will be JSON encoded)
   (create-tool-executor
     (fn [{:keys [query]}]
       {:results [{:title \"Result 1\" :url \"http://...\"}]
        :total 42}))
   
   ;; With options - skip argument parsing (receive raw JSON string)
   (create-tool-executor
     (fn [raw-args]
       (handle-raw-json raw-args))
     {:parse-args? false})
   ```"
  ([f]
   (create-tool-executor f {}))
  ([f {:keys [parse-args?]
       :or {parse-args? true}}]
   (reify ToolExecutor
     (^String execute [_this ^ToolExecutionRequest request ^Object _memory-id]
       (let [args-json (.arguments request)
             args (if parse-args?
                    (parse-arguments args-json)
                    args-json)
             result (f args)]
         (result->string result))))))

(defn create-safe-executor
  "Create a ToolExecutor that catches exceptions and returns error messages.
   
   This is useful for production environments where you want the LLM to
   receive error feedback instead of having the entire request fail.
   
   Options:
   - `:on-error` - Function called with exception, should return error message string
                   Default: returns exception message
   - `:parse-args?` - Same as `create-tool-executor`
   
   Example:
   ```clojure
   (create-safe-executor
     (fn [{:keys [url]}]
       (fetch-url url))
     {:on-error (fn [e] 
                  (str \"Failed to fetch URL: \" (.getMessage e)))})
   ```"
  ([f]
   (create-safe-executor f {}))
  ([f {:keys [on-error parse-args?]
       :or {on-error #(str "Error: " (.getMessage ^Exception %))
            parse-args? true}}]
   (reify ToolExecutor
     (^String execute [_this ^ToolExecutionRequest request ^Object _memory-id]
       (try
         (let [args-json (.arguments request)
               args (if parse-args?
                      (parse-arguments args-json)
                      args-json)
               result (f args)]
           (result->string result))
         (catch Exception e
           (on-error e)))))))

;; =============================================================================
;; Complete Tool Definition
;; =============================================================================

(defn deftool-from-schema
  "Create a complete tool definition with both specification and executor.
   
   Returns a map with:
   - `:spec` - The ToolSpecification
   - `:executor` - The ToolExecutor
   - `:name` - Tool name (for convenience)
   
   This is useful when you need both the spec and executor separately.
   For AiServices, prefer `tools->map` which creates the required format directly.
   
   Example:
   ```clojure
   (def weather-tool
     (deftool-from-schema
       {:name \"get_weather\"
        :description \"Get weather for a location\"
        :parameters {:type :object
                     :properties {:location {:type :string}}
                     :required [:location]}
        :fn (fn [{:keys [location]}]
              (fetch-weather location))}))
   
   ;; Access parts
   (:spec weather-tool)     ;; => ToolSpecification
   (:executor weather-tool) ;; => ToolExecutor
   ```"
  [{:keys [name description parameters] tool-fn :fn :as tool-def}]
  {:pre [(string? name) (string? description) (fn? tool-fn)]}
  {:name name
   :spec (create-tool-spec (select-keys tool-def [:name :description :parameters]))
   :executor (create-tool-executor tool-fn)})

;; =============================================================================
;; Batch Tool Registration
;; =============================================================================

(defn tools->map
  "Convert a sequence of tool definitions to a map of ToolSpecification -> ToolExecutor.
   
   This is the format expected by LangChain4j's AiServices for tool registration.
   
   Each tool definition is a map with:
   - `:name` - Tool name (string)
   - `:description` - Tool description (string)
   - `:parameters` - JSON Schema EDN (optional)
   - `:fn` - Clojure function to execute
   
   Options (second argument):
   - `:safe?` - If true, wrap executors with error handling (default: false)
   - `:on-error` - Error handler function when `:safe?` is true
   
   Example:
   ```clojure
   (def my-tools
     (tools->map
       [{:name \"get_weather\"
         :description \"Get weather\"
         :parameters {:type :object
                      :properties {:location {:type :string}}
                      :required [:location]}
         :fn (fn [{:keys [location]}] (get-weather location))}
        {:name \"add_numbers\"
         :description \"Add two numbers\"
         :parameters {:type :object
                      :properties {:a {:type :number}
                                   :b {:type :number}}
                      :required [:a :b]}
         :fn (fn [{:keys [a b]}] (+ a b))}]
       {:safe? true}))
   
   ;; Use with AiServices
   (-> (AiServices/builder MyInterface)
       (.chatModel model)
       (.tools my-tools)
       (.build))
   ```"
  ([tool-definitions]
   (tools->map tool-definitions {}))
  ([tool-definitions {:keys [safe? on-error]}]
   (reduce
    (fn [acc {:keys [name description parameters] tool-fn :fn}]
      (let [spec (create-tool-spec {:name name
                                    :description description
                                    :parameters parameters})
            executor (if safe?
                       (create-safe-executor tool-fn (when on-error {:on-error on-error}))
                       (create-tool-executor tool-fn))]
        (assoc acc spec executor)))
    {}
    tool-definitions)))

(defn tools->specs
  "Extract just the ToolSpecifications from tool definitions.
   
   Useful when you need to pass specifications separately from executors.
   
   Example:
   ```clojure
   (def specs (tools->specs my-tool-definitions))
   ;; => [ToolSpecification1, ToolSpecification2, ...]
   ```"
  [tool-definitions]
  (mapv #(create-tool-spec (select-keys % [:name :description :parameters]))
        tool-definitions))

(defn find-executor
  "Find the executor for a tool by name in a tools map.
   
   Example:
   ```clojure
   (def tools-map (tools->map [...]))
   (def executor (find-executor tools-map \"get_weather\"))
   ```"
  [tools-map tool-name]
  (some (fn [[spec executor]]
          (when (= tool-name (.name ^ToolSpecification spec))
            executor))
        tools-map))
