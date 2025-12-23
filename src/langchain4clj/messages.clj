(ns langchain4clj.messages
  "Message conversion utilities for serialization and manipulation.
   
   Provides primitives for converting between Java ChatMessage objects
   and Clojure EDN / JSON data structures. Users decide how to persist.
   
   ## EDN Conversion (Clojure-friendly)
   
   ```clojure
   ;; Java -> EDN
   (message->edn user-msg)
   ;; => {:type :user :contents [{:type :text :text \"Hello\"}]}
   
   (messages->edn (.messages memory))
   ;; => [{:type :user ...} {:type :ai ...}]
   
   ;; EDN -> Java
   (edn->message {:type :user :text \"Hello\"})
   ;; => #object[UserMessage ...]
   
   (edn->messages [{:type :user :text \"Hi\"} {:type :ai :text \"Hello!\"}])
   ;; => #object[ArrayList ...]
   ```
   
   ## JSON Conversion (LangChain4j wrapper)
   
   ```clojure
   ;; Java -> JSON
   (messages->json (.messages memory))
   ;; => \"[{\\\"type\\\":\\\"USER\\\",...}]\"
   
   ;; JSON -> Java  
   (json->messages json-str)
   ;; => #object[ArrayList ...]
   ```
   
   ## Persistence Example
   
   Users combine these primitives with their storage of choice:
   
   ```clojure
   ;; Save to file as EDN
   (spit \"chat.edn\" (pr-str (messages->edn messages)))
   
   ;; Load from file
   (edn->messages (edn/read-string (slurp \"chat.edn\")))
   
   ;; Save to DB as JSON
   (jdbc/insert! db :chats {:data (messages->json messages)})
   
   ;; Load from DB
   (json->messages (:data (jdbc/get-by-id db :chats id)))
   ```"
  (:require [clojure.data.json :as json])
  (:import [dev.langchain4j.data.message
            ChatMessage
            ChatMessageSerializer
            ChatMessageDeserializer
            UserMessage
            SystemMessage
            AiMessage
            ToolExecutionResultMessage]
           [dev.langchain4j.agent.tool ToolExecutionRequest]
           [java.util List ArrayList]))

;; =============================================================================
;; Java -> EDN Conversion
;; =============================================================================

(defn- tool-execution-request->edn
  "Convert ToolExecutionRequest to EDN."
  [^ToolExecutionRequest req]
  (cond-> {:id (.id req)
           :name (.name req)}
    (.arguments req) (assoc :arguments (.arguments req))))

(defn message->edn
  "Convert a ChatMessage to EDN map.
   
   Returns a map with :type and message-specific fields:
   - UserMessage:    {:type :user :contents [{:type :text :text \"...\"}]}
   - SystemMessage:  {:type :system :text \"...\"}
   - AiMessage:      {:type :ai :text \"...\" :tool-execution-requests [...]}
   - ToolExecutionResultMessage: {:type :tool-result :id \"...\" :tool-name \"...\" :text \"...\"}
   
   Arguments:
   - message: A ChatMessage instance
   
   Example:
   ```clojure
   (message->edn (UserMessage/from \"Hello\"))
   ;; => {:type :user :contents [{:type :text :text \"Hello\"}]}
   ```"
  [^ChatMessage message]
  (cond
    (instance? UserMessage message)
    (let [^UserMessage um message]
      {:type :user
       :contents (mapv (fn [content]
                         {:type :text
                          :text (.text content)})
                       (.contents um))})

    (instance? SystemMessage message)
    (let [^SystemMessage sm message]
      {:type :system
       :text (.text sm)})

    (instance? AiMessage message)
    (let [^AiMessage am message
          tool-reqs (.toolExecutionRequests am)]
      (cond-> {:type :ai}
        (.text am) (assoc :text (.text am))
        (seq tool-reqs) (assoc :tool-execution-requests
                               (mapv tool-execution-request->edn tool-reqs))))

    (instance? ToolExecutionResultMessage message)
    (let [^ToolExecutionResultMessage trm message]
      {:type :tool-result
       :id (.id trm)
       :tool-name (.toolName trm)
       :text (.text trm)})

    :else
    {:type :unknown
     :raw (str message)}))

(defn messages->edn
  "Convert a collection of ChatMessages to EDN vector.
   
   Arguments:
   - messages: A List/collection of ChatMessage instances
   
   Returns: Vector of EDN maps
   
   Example:
   ```clojure
   (messages->edn (.messages memory))
   ;; => [{:type :user :contents [...]} {:type :ai :text \"...\"}]
   ```"
  [messages]
  (mapv message->edn messages))

;; =============================================================================
;; EDN -> Java Conversion
;; =============================================================================

(defn- edn->tool-execution-request
  "Convert EDN map to ToolExecutionRequest."
  [{:keys [id name arguments]}]
  (-> (ToolExecutionRequest/builder)
      (.id id)
      (.name name)
      (cond-> arguments (.arguments arguments))
      (.build)))

(defn edn->message
  "Convert EDN map to ChatMessage.
   
   Accepts maps with :type key indicating message type:
   - :user or :USER     -> UserMessage
   - :system or :SYSTEM -> SystemMessage  
   - :ai or :AI         -> AiMessage
   - :tool-result or :TOOL_EXECUTION_RESULT -> ToolExecutionResultMessage
   
   For UserMessage, accepts either:
   - {:type :user :text \"...\"} (simple form)
   - {:type :user :contents [{:type :text :text \"...\"}]} (full form)
   
   Arguments:
   - edn: Map with :type and message content
   
   Example:
   ```clojure
   (edn->message {:type :user :text \"Hello\"})
   ;; => #object[UserMessage ...]
   
   (edn->message {:type :ai :text \"Hi!\" :tool-execution-requests [...]})
   ;; => #object[AiMessage ...]
   ```"
  [{:keys [type text contents tool-execution-requests id tool-name] :as edn}]
  (let [type-kw (if (string? type) (keyword type) type)
        normalized-type (keyword (clojure.string/lower-case (name type-kw)))]
    (case normalized-type
      :user
      (if text
        (UserMessage/from ^String text)
        (UserMessage/from ^String (or (-> contents first :text) "")))

      :system
      (SystemMessage/from ^String text)

      :ai
      (if (seq tool-execution-requests)
        (AiMessage. ^String text ^java.util.List (mapv edn->tool-execution-request tool-execution-requests))
        (AiMessage/from ^String (or text "")))

      (:tool-result :tool_execution_result)
      (ToolExecutionResultMessage. ^String id ^String tool-name ^String text)

      (throw (ex-info (str "Unknown message type: " type) {:type type :edn edn})))))

(defn edn->messages
  "Convert EDN vector to List of ChatMessages.
   
   Arguments:
   - edn-messages: Vector/sequence of EDN message maps
   
   Returns: java.util.List<ChatMessage>
   
   Example:
   ```clojure
   (edn->messages [{:type :user :text \"Hi\"}
                   {:type :ai :text \"Hello!\"}])
   ;; => #object[ArrayList ...]
   ```"
  [edn-messages]
  (ArrayList. ^java.util.Collection (mapv edn->message edn-messages)))

;; =============================================================================
;; JSON Conversion (LangChain4j wrapper)
;; =============================================================================

(defn message->json
  "Convert a ChatMessage to JSON string.
   
   Uses LangChain4j's ChatMessageSerializer for consistent format.
   
   Arguments:
   - message: A ChatMessage instance
   
   Returns: JSON string
   
   Example:
   ```clojure
   (message->json (UserMessage/from \"Hello\"))
   ;; => \"{\\\"type\\\":\\\"USER\\\",...}\"
   ```"
  [^ChatMessage message]
  (ChatMessageSerializer/messageToJson message))

(defn messages->json
  "Convert a collection of ChatMessages to JSON string.
   
   Uses LangChain4j's ChatMessageSerializer for consistent format.
   
   Arguments:
   - messages: A List/collection of ChatMessage instances
   
   Returns: JSON string (array format)
   
   Example:
   ```clojure
   (messages->json (.messages memory))
   ;; => \"[{\\\"type\\\":\\\"USER\\\",...},{\\\"type\\\":\\\"AI\\\",...}]\"
   ```"
  [^List messages]
  (ChatMessageSerializer/messagesToJson messages))

(defn json->message
  "Convert JSON string to ChatMessage.
   
   Uses LangChain4j's ChatMessageDeserializer.
   
   Arguments:
   - json-str: JSON string representing a single message
   
   Returns: ChatMessage instance
   
   Example:
   ```clojure
   (json->message \"{\\\"type\\\":\\\"USER\\\",...}\")
   ;; => #object[UserMessage ...]
   ```"
  [^String json-str]
  (ChatMessageDeserializer/messageFromJson json-str))

(defn json->messages
  "Convert JSON string to List of ChatMessages.
   
   Uses LangChain4j's ChatMessageDeserializer.
   
   Arguments:
   - json-str: JSON string representing array of messages
   
   Returns: java.util.List<ChatMessage>
   
   Example:
   ```clojure
   (json->messages \"[{\\\"type\\\":\\\"USER\\\",...}]\")
   ;; => #object[ArrayList ...]
   ```"
  [^String json-str]
  (ChatMessageDeserializer/messagesFromJson json-str))

;; =============================================================================
;; Helper Functions
;; =============================================================================

(defn parse-tool-arguments
  "Parse JSON string arguments in tool-execution-requests to EDN maps.
   
   Tool arguments come as JSON strings from the LLM. This helper
   parses them into Clojure maps for easier manipulation.
   
   Arguments:
   - message-edn: EDN map from message->edn (must be :ai type with tool requests)
   
   Returns: Updated message-edn with parsed :arguments
   
   Example:
   ```clojure
   (-> (message->edn ai-msg)
       (parse-tool-arguments))
   ;; Tool requests now have {:arguments {:x 1}} instead of {:arguments \"{\\\"x\\\":1}\"}
   ```"
  [message-edn]
  (if-let [tool-reqs (:tool-execution-requests message-edn)]
    (assoc message-edn
           :tool-execution-requests
           (mapv (fn [req]
                   (if-let [args (:arguments req)]
                     (assoc req :arguments (json/read-str args :key-fn keyword))
                     req))
                 tool-reqs))
    message-edn))
