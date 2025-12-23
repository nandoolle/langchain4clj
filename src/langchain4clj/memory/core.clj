(ns langchain4clj.memory.core
  "Core memory protocol and basic implementation."
  (:require [langchain4clj.constants :as const]
            [clojure.spec.alpha :as s])
  (:import [dev.langchain4j.model.output TokenUsage]))

;; Protocol definition
(defprotocol ChatMemory
  "Protocol for chat message memory management."
  (add-message!
    [this message]
    [this message metadata]
    "Add a message to memory.

     Args:
     - message: A ChatMessage instance (UserMessage, AiMessage, etc.)
     - metadata (optional): Map that may contain:
       - :token-usage - TokenUsage instance from ChatResponse

     Returns nil.

     Example:
     (add-message! memory (UserMessage. \"Hello\"))

     With token tracking:
     (let [response (chat model \"Hello\")]
       (add-message! memory
                     (.aiMessage response)
                     {:token-usage (-> response .metadata .tokenUsage)}))")

  (get-messages [this] [this opts]
    "Get messages from memory.

     With no args, returns all messages.
     With opts map, supports filtering:
     - :type - Filter by message type (e.g., UserMessage)
     - :limit - Limit number of messages returned
     - :from-index - Start from this index")

  (clear! [this]
    "Clear all messages from memory. Returns nil.")

  (stats [this]
    "Get memory statistics.
     Returns map with:
     - :message-count - Number of messages in memory
     - :token-count - Total tokens counted from TokenUsage metadata"))

;; Private helper to create message store
(defn- create-message-store
  "Creates an atom to hold memory state."
  []
  (atom {:messages []
         :token-count 0}))

;; Private helper to apply filters
(defn- apply-filters
  "Apply filtering options to messages."
  [messages {:keys [type limit from-index]}]
  (let [filtered (if type
                   (filterv #(instance? type %) messages)
                   messages)
        from-indexed (if from-index
                       (vec (drop from-index filtered))
                       filtered)
        limited (if limit
                  (vec (take limit from-indexed))
                  from-indexed)]
    limited))

;; Helper to extract token count from metadata
(defn- extract-token-count
  "Extract total token count from metadata if present."
  [metadata]
  (when-let [^TokenUsage token-usage (:token-usage metadata)]
    (.totalTokenCount token-usage)))

;; Basic memory implementation
(defn create-basic-memory
  "Create a basic message window memory with sliding window behavior.

   Options:
   - :max-messages - Maximum messages to retain (default: 100)

   When max is exceeded, oldest messages are dropped automatically.

   Token tracking is integrated - pass TokenUsage in metadata to track:
   (let [response (chat model \"Hello\")]
     (add-message! memory
                   (.aiMessage response)
                   {:token-usage (-> response .metadata .tokenUsage)}))

   Example:
   (create-basic-memory {:max-messages 50})"
  [{:keys [max-messages]
    :or {max-messages const/default-max-messages}}]
  (let [store (create-message-store)]
    (reify ChatMemory
      (add-message! [this message]
        (add-message! this message nil))

      (add-message! [_ message metadata]
        ;; Track tokens if metadata provided
        (when-let [tokens (extract-token-count metadata)]
          (swap! store update :token-count + tokens))

        ;; Add message with sliding window
        (swap! store update :messages
               (fn [msgs]
                 (let [updated (conj msgs message)]
                   (if (> (count updated) max-messages)
                     (vec (take-last max-messages updated))
                     updated))))
        nil)

      (get-messages [this]
        (get-messages this {}))

      (get-messages [_ opts]
        (apply-filters (:messages @store) opts))

      (clear! [_]
        (reset! store {:messages [] :token-count 0})
        nil)

      (stats [_]
        {:message-count (count (:messages @store))
         :token-count (:token-count @store)}))))

;; Spec definitions
(s/def ::max-messages (s/int-in 1 100001))
(s/def ::message-type class?)
(s/def ::limit pos-int?)
(s/def ::from-index nat-int?)

(s/def ::create-options
  (s/keys :opt-un [::max-messages]))

(s/def ::get-options
  (s/keys :opt-un [::message-type ::limit ::from-index]))

(s/fdef create-basic-memory
  :args (s/cat :opts ::create-options)
  :ret #(satisfies? ChatMemory %))
