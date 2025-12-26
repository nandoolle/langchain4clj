(ns langchain4clj.memory.reset
  "Auto-reset strategy for chat memory."
  (:require [langchain4clj.memory.core :as core]
            [langchain4clj.constants :as const]
            [clojure.spec.alpha :as s]))

(defn- should-reset?
  "Check if memory should reset based on message or token thresholds."
  [{:keys [message-count token-count]}
   {:keys [max-messages max-tokens reset-threshold]}]
  (let [threshold (or reset-threshold const/default-reset-threshold)]
    (or
     (when max-messages
       (>= message-count (* threshold max-messages)))
     (when (and max-tokens (pos? token-count))
       (>= token-count (* threshold max-tokens))))))

(defn with-auto-reset
  "Add automatic reset at capacity threshold with optional context preservation."
  [memory {:keys [max-messages max-tokens reset-threshold context]
           :or {reset-threshold const/default-reset-threshold}}]
  {:pre [(satisfies? core/ChatMemory memory)]}
  (let [limits (atom {:max-messages max-messages
                      :max-tokens max-tokens
                      :reset-threshold reset-threshold
                      :context context})]
    (reify core/ChatMemory
      (add-message! [this message]
        (core/add-message! this message nil))

      (add-message! [_ message metadata]
        ;; Add the new message first
        (core/add-message! memory message metadata)

        ;; Check if should reset after adding
        (let [current-stats (core/stats memory)]
          (when (should-reset? current-stats @limits)
            (core/clear! memory)
            ;; Re-add context messages
            (when-let [ctx (:context @limits)]
              (doseq [context-msg ctx]
                (core/add-message! memory context-msg nil)))))
        nil)

      (get-messages [_]
        (core/get-messages memory))

      (get-messages [_ opts]
        (core/get-messages memory opts))

      (clear! [_]
        (core/clear! memory)
        nil)

      (stats [_]
        (core/stats memory)))))

;; Spec definitions
(s/def ::max-messages pos-int?)
(s/def ::max-tokens pos-int?)
(s/def ::reset-threshold (s/double-in :min 0.0 :max 1.0 :NaN? false))
(s/def ::context (s/coll-of any? :kind vector?))

(s/def ::auto-reset-options
  (s/keys :opt-un [::max-messages ::max-tokens ::reset-threshold ::context]))

(s/fdef with-auto-reset
  :args (s/cat :memory #(satisfies? core/ChatMemory %)
               :opts ::auto-reset-options)
  :ret #(satisfies? core/ChatMemory %))
