(ns hooks.langchain4clj.memory
  "clj-kondo hook for langchain4clj.memory/defmemory macro."
  (:require [clj-kondo.hooks-api :as api]))

(defn defmemory
  "Hook for defmemory macro.

   Transforms:
   (defmemory my-memory :max-messages 100 :auto-reset {...})

   Into (for linting purposes):
   (def my-memory nil)

   Note: inline-def warnings are suppressed via config-in-ns in config.edn"
  [{:keys [node]}]
  (let [children (rest (:children node))
        name-node (first children)]
    {:node (api/list-node
            (list
             (api/token-node 'def)
             name-node
             (api/token-node nil)))}))
