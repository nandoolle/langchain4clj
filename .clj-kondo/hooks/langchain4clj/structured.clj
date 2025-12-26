(ns hooks.langchain4clj.structured
  "clj-kondo hook for langchain4clj.structured/defstructured macro."
  (:require [clj-kondo.hooks-api :as api]
            [clojure.string :as str]))

(defn defstructured
  "Hook for defstructured macro.

   Transforms:
   (defstructured Person {:name :string :age :int})

   Into (for linting purposes):
   (do
     (def Person nil)
     (defn get-person [_model _prompt] nil)
     (defn get-person-json [_model _prompt] nil))

   Note: inline-def warnings are suppressed via config-in-ns in config.edn"
  [{:keys [node]}]
  (let [children (rest (:children node))
        name-node (first children)
        name-str (str (api/sexpr name-node))
        edn-fn-name (symbol (str "get-" (str/lower-case name-str)))
        json-fn-name (symbol (str "get-" (str/lower-case name-str) "-json"))]
    {:node (api/list-node
            (list
             (api/token-node 'do)
             ;; Define the type symbol (used in some tests)
             (api/list-node
              (list (api/token-node 'def)
                    name-node
                    (api/token-node nil)))
             ;; Define the EDN function (use _ prefix to suppress unused binding warnings)
             (api/list-node
              (list (api/token-node 'defn)
                    (api/token-node edn-fn-name)
                    (api/vector-node
                     [(api/token-node '_model)
                      (api/token-node '_prompt)])
                    (api/token-node nil)))
             ;; Define the JSON function
             (api/list-node
              (list (api/token-node 'defn)
                    (api/token-node json-fn-name)
                    (api/vector-node
                     [(api/token-node '_model)
                      (api/token-node '_prompt)])
                    (api/token-node nil)))))}))
