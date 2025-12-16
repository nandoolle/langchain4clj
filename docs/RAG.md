---
layout: default
title: RAG (Document Processing)
---

# RAG - Document Processing

Load and process documents for Retrieval-Augmented Generation.

## Quick Start

```clojure
(require '[langchain4clj.rag.document :as doc])

(def document (doc/load-document "/path/to/file.pdf"
                {:parser :apache-tika}))

(def chunks (doc/split-documents [document]
              {:max-segment-size 500
               :max-overlap 50}))

(doseq [chunk chunks]
  (println (:text chunk)))
```

## Document Loading

```clojure
;; Single document
(def doc (doc/load-document "/path/to/file.txt"))

;; PDF with Apache Tika
(def pdf (doc/load-document "/path/to/file.pdf"
           {:parser :apache-tika}))

;; Load from directory
(def docs (doc/load-documents "/path/to/docs"
            {:parser :apache-tika
             :recursive? true}))

;; With glob pattern
(def pdfs (doc/load-documents "/path/to/docs"
            {:parser :apache-tika
             :glob "*.pdf"}))

;; From URL
(def doc (doc/load-from-url
           "https://example.com/document.pdf"
           {:parser :apache-tika}))
```

## Document Parsers

**Text Parser** - Basic parser for plain text files (default)

**Apache Tika** - Auto-detects and parses many formats:
- PDF, DOCX, DOC, XLSX, PPTX
- HTML, XML, RTF
- OpenOffice formats (ODT, ODS, ODP)
- Plain text (TXT, CSV)

```clojure
(def parser (doc/create-parser :apache-tika))
(def doc (doc/load-document "/path/to/file.pdf" {:parser parser}))
```

## Document Structure

```clojure
{:text "Document content here..."
 :metadata {:source "/path/to/file.pdf"
            :file-size 12345
            :content-type "application/pdf"}
 :java-object #<Document...>}
```

## Document Splitting

```clojure
;; Create splitter
(def splitter (doc/recursive-splitter
                {:max-segment-size 500
                 :max-overlap 50}))

;; Split documents
(def segments (doc/split-document document splitter))

;; Convenience function
(def segments (doc/split-documents documents
                {:max-segment-size 500
                 :max-overlap 50
                 :unit :tokens}))
```

### Configuration

```clojure
{:max-segment-size 500  ;; Max tokens/chars per chunk
 :max-overlap 50        ;; Overlap between chunks
 :unit :tokens}         ;; :tokens or :chars
```

## Complete Pipeline

```clojure
(require '[langchain4clj.rag.document :as doc])
(require '[langchain4clj.core :as llm])

;; 1. Load documents
(def documents (doc/load-documents "/path/to/knowledge-base"
                 {:parser :apache-tika :recursive? true}))

;; 2. Split into chunks
(def chunks (doc/split-documents documents
              {:max-segment-size 500 :max-overlap 50}))

;; 3. Store in vector database (your implementation)
(doseq [chunk chunks]
  (store-embedding! (:text chunk) (:metadata chunk)))

;; 4. Query with context
(defn query-with-context [question]
  (let [relevant-chunks (search-embeddings question 5)
        context (str/join "\n\n" (map :text relevant-chunks))
        prompt (str "Context:\n" context "\n\nQuestion: " question)]
    (llm/chat model prompt)))
```

## RAG with Assistant

```clojure
(require '[langchain4clj.tools :as tools])
(require '[langchain4clj.assistant :as assistant])

(tools/deftool search-docs
  "Searches the knowledge base"
  {:query string?}
  [{:keys [query]}]
  (let [results (search-embeddings query 3)]
    (str/join "\n\n" (map :text results))))

(def rag-assistant
  (assistant/create-assistant
    {:model model
     :tools [search-docs]
     :system-message "Use the search tool to find relevant information."}))

(rag-assistant "What are the benefits of our premium plan?")
```

## Chunking Strategies

```clojure
;; Small chunks (precise retrieval)
{:max-segment-size 200 :max-overlap 20}

;; Medium chunks (balanced)
{:max-segment-size 500 :max-overlap 50}

;; Large chunks (more context)
{:max-segment-size 1000 :max-overlap 100}
```

## Common Patterns

### Batch Processing

```clojure
(defn process-directory [dir-path]
  (-> (doc/load-documents dir-path
        {:parser :apache-tika :recursive? true})
      (doc/split-documents
        {:max-segment-size 500 :max-overlap 50})))
```

### Error Handling

```clojure
(defn safe-load-document [path]
  (try
    (doc/load-document path {:parser :apache-tika})
    (catch Exception e
      (log/error e (str "Failed to load: " path))
      nil)))

(def docs (keep safe-load-document file-paths))
```

### Adding Metadata

```clojure
(defn load-with-metadata [path tags]
  (let [doc (doc/load-document path {:parser :apache-tika})]
    (update doc :metadata merge {:tags tags
                                 :indexed-at (System/currentTimeMillis)})))
```

## Configuration Reference

```clojure
;; load-document
{:parser :text|:apache-tika|parser-instance}

;; load-documents
{:parser :text|:apache-tika
 :recursive? false
 :glob "*.pdf"}

;; split-documents
{:max-segment-size 500
 :max-overlap 50
 :unit :tokens|:chars}
```

## Related

- [Assistant System](ASSISTANT.md) - Use RAG with assistants
- [Tools & Function Calling](TOOLS.md) - Create search tools
