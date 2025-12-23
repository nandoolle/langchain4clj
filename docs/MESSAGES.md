---
layout: default
title: Message Serialization
---

# Message Serialization

Convert messages between Java ChatMessage objects, Clojure EDN, and JSON.

## Quick Start

```clojure
(require '[langchain4clj.messages :as msg])
(require '[langchain4clj.assistant :as assistant])

;; Get messages from memory
(def messages (.messages (:memory my-assistant)))

;; Convert to EDN for Clojure manipulation
(def edn-messages (msg/messages->edn messages))
;; => [{:type :user :contents [{:type :text :text "Hello"}]}
;;     {:type :ai :text "Hi there!"}]

;; Convert back to Java for use with LangChain4j
(def java-messages (msg/edn->messages edn-messages))
```

## EDN Format

### Message Types

```clojure
;; User message
{:type :user
 :contents [{:type :text :text "Hello, how are you?"}]}

;; System message  
{:type :system
 :text "You are a helpful assistant."}

;; AI message
{:type :ai
 :text "I'm doing well, thank you!"}

;; AI message with tool calls
{:type :ai
 :text nil
 :tool-execution-requests [{:id "call_123"
                            :name "get_weather"
                            :arguments "{\"city\":\"Tokyo\"}"}]}

;; Tool result message
{:type :tool-result
 :id "call_123"
 :tool-name "get_weather"
 :text "Sunny, 72°F"}
```

## Java to EDN

Convert LangChain4j ChatMessage objects to Clojure data:

```clojure
;; Single message
(msg/message->edn user-message)
;; => {:type :user :contents [{:type :text :text "Hello"}]}

;; Multiple messages
(msg/messages->edn (.messages memory))
;; => [{:type :user ...} {:type :ai ...} ...]
```

## EDN to Java

Convert Clojure data back to ChatMessage objects:

```clojure
;; Single message (simple form)
(msg/edn->message {:type :user :text "Hello"})
;; => #object[UserMessage ...]

;; Single message (full form)
(msg/edn->message {:type :user :contents [{:type :text :text "Hello"}]})
;; => #object[UserMessage ...]

;; Multiple messages
(msg/edn->messages [{:type :user :text "Hi"}
                    {:type :ai :text "Hello!"}])
;; => #object[ArrayList [UserMessage, AiMessage]]
```

## JSON Conversion

Wrapper around LangChain4j's native JSON serialization:

```clojure
;; Java -> JSON
(msg/message->json user-message)
;; => "{\"type\":\"USER\",\"contents\":[{\"type\":\"TEXT\",\"text\":\"Hello\"}]}"

(msg/messages->json messages)
;; => "[{\"type\":\"USER\",...},{\"type\":\"AI\",...}]"

;; JSON -> Java
(msg/json->message json-str)
;; => #object[UserMessage ...]

(msg/json->messages json-array-str)
;; => #object[ArrayList ...]
```

## Persistence Examples

Combine conversion primitives with your storage of choice:

### File Storage (EDN)

```clojure
(require '[clojure.edn :as edn])

;; Save
(spit "chat.edn" (pr-str (msg/messages->edn messages)))

;; Load
(msg/edn->messages (edn/read-string (slurp "chat.edn")))
```

### File Storage (JSON)

```clojure
;; Save
(spit "chat.json" (msg/messages->json messages))

;; Load
(msg/json->messages (slurp "chat.json"))
```

### Database (JDBC)

```clojure
;; Save
(jdbc/insert! db :conversations {:id session-id
                                  :messages (msg/messages->json messages)})

;; Load
(let [row (jdbc/get-by-id db :conversations session-id)]
  (msg/json->messages (:messages row)))
```

### Redis

```clojure
;; Save
(redis/set conn (str "chat:" session-id) (msg/messages->json messages))

;; Load
(msg/json->messages (redis/get conn (str "chat:" session-id)))
```

### Datomic/DataScript

```clojure
;; Save as EDN (Datomic stores EDN natively)
(d/transact conn [{:db/id session-id
                   :session/messages (pr-str (msg/messages->edn messages))}])

;; Load
(let [edn-str (:session/messages (d/entity db session-id))]
  (msg/edn->messages (edn/read-string edn-str)))
```

## Working with Tool Arguments

Tool call arguments arrive as JSON strings. Use `parse-tool-arguments` to convert them:

```clojure
(def ai-msg-edn (msg/message->edn ai-message))
;; => {:type :ai
;;     :tool-execution-requests [{:id "call_123"
;;                                :name "get_weather"
;;                                :arguments "{\"city\":\"Tokyo\"}"}]}

(msg/parse-tool-arguments ai-msg-edn)
;; => {:type :ai
;;     :tool-execution-requests [{:id "call_123"
;;                                :name "get_weather"
;;                                :arguments {:city "Tokyo"}}]}
```

## Roundtrip Example

```clojure
(require '[langchain4clj.messages :as msg])
(require '[langchain4clj.assistant :as assistant])
(require '[clojure.edn :as edn])

;; Create assistant with memory
(def my-assistant (assistant/create-assistant {:model model}))

;; Have a conversation
(my-assistant "My name is Alice")
(my-assistant "I live in Tokyo")

;; Save conversation
(let [messages (.messages (:memory my-assistant))
      edn-data (msg/messages->edn messages)]
  (spit "session.edn" (pr-str edn-data)))

;; Later: restore conversation
(let [saved-edn (edn/read-string (slurp "session.edn"))
      restored-messages (msg/edn->messages saved-edn)
      memory (assistant/create-memory {:max-messages 100})]
  ;; Add messages back to memory
  (doseq [msg restored-messages]
    (.add memory msg))
  
  ;; Create new assistant with restored memory
  (def restored-assistant
    (assistant/create-assistant {:model model :memory memory}))
  
  (restored-assistant "What's my name and where do I live?"))
;; => "Your name is Alice and you live in Tokyo."
```

## API Reference

### EDN Conversion

| Function | Description |
|----------|-------------|
| `message->edn` | Convert ChatMessage to EDN map |
| `messages->edn` | Convert collection to EDN vector |
| `edn->message` | Convert EDN map to ChatMessage |
| `edn->messages` | Convert EDN vector to Java List |

### JSON Conversion

| Function | Description |
|----------|-------------|
| `message->json` | Convert ChatMessage to JSON string |
| `messages->json` | Convert collection to JSON array string |
| `json->message` | Convert JSON string to ChatMessage |
| `json->messages` | Convert JSON array to Java List |

### Helpers

| Function | Description |
|----------|-------------|
| `parse-tool-arguments` | Parse JSON arguments in tool requests |

## Related

- [Assistant](ASSISTANT.md) - Memory management
- [Memory Patterns](MEMORY.md) - Conversation history
- [Chat Listeners](LISTENERS.md) - Message capturing listener
