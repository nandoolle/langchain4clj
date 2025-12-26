(ns langchain4clj.tools-test
  "Tests for unified tool support"
  (:require [clojure.test :refer [deftest is testing]]
            [langchain4clj.tools :as tools]
            [clojure.spec.alpha :as s]))

;; Only test Spec since it's built-in
;; Schema and Malli tests would require their dependencies

;; ============================================================================
;; Spec Definitions
;; ============================================================================

(s/def ::expression string?)
(s/def ::precision int?)
(s/def ::calc-params
  (s/keys :req-un [::expression]
          :opt-un [::precision]))

(s/def ::location string?)
(s/def ::units #{"celsius" "fahrenheit" "kelvin"})
(s/def ::weather-params
  (s/keys :req-un [::location]
          :opt-un [::units]))

;; Empty params schema for tools without parameters
(s/def ::no-params (s/keys))

;; Pokemon tool params for normalization tests
(s/def ::pokemon-name string?)
(s/def ::pokemon-type string?)
(s/def ::pokemon-params (s/keys :req-un [::pokemon-name]
                                :opt-un [::pokemon-type]))

;; ============================================================================
;; Test Functions
;; ============================================================================

(defn calculate [{:keys [expression precision]}]
  (let [result (eval (read-string expression))]
    (if precision
      (/ (Math/round (* result (Math/pow 10 precision)))
         (Math/pow 10 precision))
      result)))

(defn get-weather [{:keys [location units]
                    :or {units "celsius"}}]
  {:location location
   :temperature 22
   :units units
   :conditions "sunny"})

;; ============================================================================
;; Tests
;; ============================================================================

(deftest test-schema-detection
  (testing "Detects Spec schemas"
    (is (= :spec (tools/detect-schema-type ::calc-params)))
    (is (= :spec (tools/detect-schema-type ::weather-params))))

  (testing "Returns nil for unknown types"
    (is (nil? (tools/detect-schema-type "not-a-schema")))))

(deftest test-create-tool-with-spec
  (testing "Creates tool with Spec validation"
    (let [tool (tools/create-tool
                {:name "calculator"
                 :description "Performs calculations"
                 :params-schema ::calc-params
                 :fn calculate})]

      (is (= "calculator" (:name tool)))
      (is (= "Performs calculations" (:description tool)))
      (is (not (nil? (:specification tool))))
      (is (fn? (:executor-fn tool)))
      (is (= :spec (:schema-type tool)))))

  (testing "Tool execution with valid params"
    (let [tool (tools/create-tool
                {:name "calculator"
                 :description "Performs calculations"
                 :params-schema ::calc-params
                 :fn calculate})]

      (is (= 5 (tools/execute-tool tool {:expression "(+ 2 3)"})))
      (is (= 3.14 (tools/execute-tool tool
                                      {:expression "(+ 1.14 2)"
                                       :precision 2})))))

  (testing "Tool execution with invalid params throws"
    (let [tool (tools/create-tool
                {:name "calculator"
                 :description "Performs calculations"
                 :params-schema ::calc-params
                 :fn calculate})]

      (is (thrown? Exception
                   (tools/execute-tool tool {:wrong-key "value"}))))))

(deftest test-tool-registry
  (testing "Register and retrieve tools"
    (tools/clear-tools!)

    (let [tool (tools/create-tool
                {:name "test-tool"
                 :description "Test"
                 :params-schema ::no-params
                 :fn identity})]

      (tools/register-tool! tool)
      (is (= tool (tools/get-tool "test-tool")))
      (is (= ["test-tool"] (tools/list-tools)))

      (tools/clear-tools!)
      (is (empty? (tools/list-tools))))))

(deftest test-tool-middleware
  (testing "Logging middleware"
    (let [output (atom [])
          tool (tools/create-tool
                {:name "test"
                 :description "Test"
                 :params-schema ::no-params
                 :fn identity})]

      ;; Capture println output
      (with-redefs [println (fn [s] (swap! output conj s))]
        (let [logged-tool (tools/with-logging tool)]
          (tools/execute-tool logged-tool {:test "data"})))

      (is (= 2 (count @output)))
      (is (re-find #"Input:" (first @output)))
      (is (re-find #"Output:" (second @output)))))

  (testing "Retry middleware"
    (let [attempts (atom 0)
          tool (tools/create-tool
                {:name "flaky"
                 :description "Sometimes fails"
                 :params-schema ::no-params
                 :fn (fn [_]
                       (swap! attempts inc)
                       (if (< @attempts 3)
                         (throw (Exception. "Failed"))
                         "Success"))})
          retry-tool (tools/with-retry tool 3)]

      (with-redefs [println (fn [_] nil)]  ; Suppress output
        (is (= "Success" (tools/execute-tool retry-tool {})))
        (is (= 3 @attempts))))))

(deftest test-find-tool
  (testing "Find tool by name"
    (let [tool1 (tools/create-tool {:name "tool1"
                                    :description "T1"
                                    :params-schema ::no-params
                                    :fn identity})
          tool2 (tools/create-tool {:name "tool2"
                                    :description "T2"
                                    :params-schema ::no-params
                                    :fn identity})
          tools [tool1 tool2]]

      (is (= tool1 (tools/find-tool "tool1" tools)))
      (is (= tool2 (tools/find-tool "tool2" tools)))
      (is (nil? (tools/find-tool "tool3" tools))))))

;; ============================================================================
;; Parameter Normalization Tests
;; ============================================================================

(deftest test-kebab->camel-conversion
  (testing "kebab->camel converts kebab-case to camelCase"
    (is (= "pokemonName" (#'tools/kebab->camel :pokemon-name)))
    (is (= "pokemonName" (#'tools/kebab->camel "pokemon-name")))
    (is (= "firstName" (#'tools/kebab->camel :first-name)))
    (is (= "myComplexKey" (#'tools/kebab->camel :my-complex-key))))

  (testing "kebab->camel preserves camelCase"
    (is (= "pokemonName" (#'tools/kebab->camel :pokemonName)))
    (is (= "pokemonName" (#'tools/kebab->camel "pokemonName")))
    (is (= "alreadyCamel" (#'tools/kebab->camel :alreadyCamel))))

  (testing "kebab->camel handles single words"
    (is (= "name" (#'tools/kebab->camel :name)))
    (is (= "data" (#'tools/kebab->camel "data")))))

(deftest test-normalize-tool-params-single-level
  (testing "Adds camelCase versions while preserving originals"
    (is (= {:pokemon-name "pikachu" "pokemonName" "pikachu"}
           (tools/normalize-tool-params {:pokemon-name "pikachu"})))
    (is (= {:first-name "John" "firstName" "John"
            :last-name "Doe" "lastName" "Doe"}
           (tools/normalize-tool-params {:first-name "John" :last-name "Doe"})))
    (is (= {:my-key 123 "myKey" 123}
           (tools/normalize-tool-params {:my-key 123}))))

  (testing "Handles already camelCase keys"
    ;; Keyword camelCase adds string version (for OpenAI compatibility)
    (is (= {:alreadyCamel 1 "alreadyCamel" 1}
           (tools/normalize-tool-params {:alreadyCamel 1})))
    ;; String camelCase has no kebab equivalent, so no duplication
    (is (= {"mixedCase" "value"}
           (tools/normalize-tool-params {"mixedCase" "value"}))))

  (testing "Handles string keys"
    (is (= {"pokemon-name" "pikachu" "pokemonName" "pikachu" :pokemon-name "pikachu"}
           (tools/normalize-tool-params {"pokemon-name" "pikachu"})))))

(deftest test-normalize-tool-params-nested
  (testing "Adds camelCase versions in nested maps"
    (let [result (tools/normalize-tool-params {:outer-key {:inner-key 123}})]
      ;; Should have both original and camelCase at all levels
      (is (contains? result :outer-key))
      (is (contains? result "outerKey"))
      (is (contains? (:outer-key result) :inner-key))
      (is (contains? (:outer-key result) "innerKey"))
      (is (= 123 (get-in result [:outer-key :inner-key])))
      (is (= 123 (get-in result [:outer-key "innerKey"]))))

    (let [result (tools/normalize-tool-params {:level-1 {:level-2 {:level-3 "value"}}})]
      ;; Check nested structure preserves originals
      (is (= "value" (get-in result [:level-1 :level-2 :level-3])))
      (is (= "value" (get-in result [:level-1 :level-2 "level3"])))))

  (testing "Handles deeply nested structures"
    (let [input {:user-data {:first-name "John"
                             :last-name "Doe"
                             :contact-info {:email-address "john@example.com"
                                            :phone-number "123-456-7890"}}}
          result (tools/normalize-tool-params input)]
      ;; Should be able to access via original keys (for spec validation)
      (is (= "John" (get-in result [:user-data :first-name])))
      (is (= "john@example.com" (get-in result [:user-data :contact-info :email-address])))
      ;; Should also have camelCase versions (for OpenAI compatibility)
      (is (= "John" (get-in result [:user-data "firstName"])))
      (is (= "john@example.com" (get-in result [:user-data :contact-info "emailAddress"]))))))

(deftest test-normalize-tool-params-collections
  (testing "Adds camelCase versions in arrays of maps"
    (is (= [{:item-name "test" "itemName" "test"}]
           (tools/normalize-tool-params [{:item-name "test"}])))
    (is (= [{:pokemon-name "pikachu" "pokemonName" "pikachu"}
            {:pokemon-name "charizard" "pokemonName" "charizard"}]
           (tools/normalize-tool-params [{:pokemon-name "pikachu"}
                                         {:pokemon-name "charizard"}]))))

  (testing "Preserves non-map values in arrays"
    (is (= [1 2 3]
           (tools/normalize-tool-params [1 2 3])))
    (is (= ["a" "b" "c"]
           (tools/normalize-tool-params ["a" "b" "c"]))))

  (testing "Handles mixed structures"
    (let [input {:items [{:item-name "A" :item-count 5}
                         {:item-name "B" :item-count 10}]}
          result (tools/normalize-tool-params input)]
      ;; Should preserve original keys for spec validation
      (is (= "A" (get-in result [:items 0 :item-name])))
      (is (= 5 (get-in result [:items 0 :item-count])))
      ;; Should also have camelCase versions
      (is (= "A" (get-in result [:items 0 "itemName"])))
      (is (= 5 (get-in result [:items 0 "itemCount"]))))))

(deftest test-normalize-tool-params-edge-cases
  (testing "Handles nil"
    (is (nil? (tools/normalize-tool-params nil))))

  (testing "Handles empty map"
    (is (= {} (tools/normalize-tool-params {}))))

  (testing "Handles empty array"
    (is (= [] (tools/normalize-tool-params []))))

  (testing "Handles non-map values"
    (is (= "string" (tools/normalize-tool-params "string")))
    (is (= 123 (tools/normalize-tool-params 123)))
    (is (= true (tools/normalize-tool-params true)))))

(deftest test-tool-with-normalization
  (testing "Tools automatically normalize kebab-case params"
    (let [tool (tools/create-tool
                {:name "test-tool"
                 :description "Test tool"
                 :params-schema ::pokemon-params
                 :fn (fn [params]
                       ;; Function receives normalized params
                       (get params "pokemonName"))})]

      (is (= "pikachu"
             (tools/execute-tool tool {:pokemon-name "pikachu"})))
      (is (= "charizard"
             (tools/execute-tool tool {"pokemon-name" "charizard"})))))

  (testing "Tools with schemas work with kebab-case input"
    (let [tool (tools/create-tool
                {:name "pokemon-tool"
                 :description "Pokemon tool"
                 :params-schema ::pokemon-params
                 :fn (fn [params]
                       (str "Pokemon: " (:pokemon-name params)))})]

      ;; Should work with kebab-case input (gets normalized then validated)
      (is (string? (tools/execute-tool tool {:pokemon-name "pikachu"}))))))

(deftest test-normalization-performance
  (testing "Normalization has minimal overhead"
    (let [large-params {:key-1 1 :key-2 2 :key-3 3 :key-4 4 :key-5 5
                        :nested-1 {:sub-key-1 "a" :sub-key-2 "b"}
                        :nested-2 {:sub-key-3 "c" :sub-key-4 "d"}}
          start (System/nanoTime)]
      (dotimes [_ 1000]
        (tools/normalize-tool-params large-params))
      (let [elapsed-ms (/ (- (System/nanoTime) start) 1000000.0)
            avg-ms (/ elapsed-ms 1000.0)]
        ;; Average should be well under 1ms per call
        (is (< avg-ms 1.0)
            (str "Normalization took " avg-ms "ms per call (should be < 1ms)"))))))
