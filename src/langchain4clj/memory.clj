(ns langchain4clj.memory
  "Chat memory management with token tracking and auto-reset strategies."
  (:require [langchain4clj.memory.core :as core]
            [langchain4clj.memory.reset :as reset]
            [langchain4clj.memory.stateless :as stateless]
            [langchain4clj.constants :as const]))

;; Re-export protocol
(def ChatMemory core/ChatMemory)

;; Re-export protocol methods
(def add-message! core/add-message!)
(def get-messages core/get-messages)
(def clear! core/clear!)
(def stats core/stats)

;; Core creation
(defn create-memory
  "Create a message window memory with sliding window and integrated token tracking."
  ([] (create-memory {}))
  ([opts]
   (core/create-basic-memory opts)))

(defn memory
  "Alias for create-memory. Use in threading."
  [opts]
  (create-memory opts))

;; Strategy decorators - re-export
(def with-auto-reset reset/with-auto-reset)
(def with-stateless-mode stateless/with-stateless-mode)

;; Threading-first config helpers
(defn with-max-messages
  "Set maximum message limit. Use in threading before creating memory."
  [config max-msgs]
  (assoc config :max-messages max-msgs))

(defn with-context
  "Set context messages to preserve. Use in threading before creating memory."
  [config context-messages]
  (assoc config :context context-messages))

;; Convenience macro
(defmacro defmemory
  "Define a preconfigured memory instance with optional strategies.

   Example:
   (defmemory production-memory
     :max-messages 100
     :auto-reset {:threshold 0.85 :max-tokens 16000}
     :stateless {:context [(SystemMessage. \"Context\")]})"
  [name & {:keys [max-messages auto-reset stateless]
           :or {max-messages const/default-max-messages}}]
  (let [base-memory `(create-memory {:max-messages ~max-messages})
        with-auto-reset? (and auto-reset (not= auto-reset false))
        with-stateless? (and stateless (not= stateless false))]
    `(def ~name
       (cond-> ~base-memory
         ~with-auto-reset? (with-auto-reset ~auto-reset)
         ~with-stateless? (with-stateless-mode ~stateless)))))
