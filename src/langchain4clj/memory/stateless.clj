(ns langchain4clj.memory.stateless
  "Stateless mode strategy for chat memory."
  (:require [langchain4clj.memory.core :as core]
            [clojure.spec.alpha :as s]))

(defn with-stateless-mode
  "Make memory stateless - clears before each session with optional context."
  [memory {:keys [context]}]
  {:pre [(satisfies? core/ChatMemory memory)]}
  (let [session-started? (atom false)]
    (reify core/ChatMemory
      (add-message! [this message]
        (core/add-message! this message nil))

      (add-message! [_ message metadata]
        ;; Clear at start of new session
        (when-not @session-started?
          (core/clear! memory)
          ;; Add context messages
          (when context
            (doseq [ctx-msg context]
              (core/add-message! memory ctx-msg nil)))
          (reset! session-started? true))

        (core/add-message! memory message metadata)
        nil)

      (get-messages [this]
        (core/get-messages memory))

      (get-messages [this opts]
        (core/get-messages memory opts))

      (clear! [_]
        (reset! session-started? false)
        (core/clear! memory)
        nil)

      (stats [_]
        (core/stats memory)))))

;; Spec definitions
(s/def ::context (s/coll-of any? :kind vector?))

(s/def ::stateless-options
  (s/keys :opt-un [::context]))

(s/fdef with-stateless-mode
  :args (s/cat :memory #(satisfies? core/ChatMemory %)
               :opts ::stateless-options)
  :ret #(satisfies? core/ChatMemory %))
