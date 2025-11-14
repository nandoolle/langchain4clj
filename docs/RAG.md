---
layout: default
title: RAG (Document Processing)
---

# RAG - Document Processing

Load and process documents for Retrieval-Augmented Generation (RAG) with flexible parsing and splitting strategies.

## Overview

RAG (Retrieval-Augmented Generation) enhances LLM responses by providing relevant context from your documents. This module provides:

- **Document Loading** - Load from files, directories, or URLs
- **Format Support** - PDF, DOCX, TXT, HTML, and more via Apache Tika
- **Document Splitting** - Break documents into chunks for embedding
- **Flexible Configuration** - Control chunk size, overlap, and splitting strategy

## Quick Start

```clojure
(require '[langchain4clj.rag.document :as doc])

;; Load a document
(def document (doc/load-document "/path/to/file.pdf"
                {:parser :apache-tika}))

;; Split into chunks
(def chunks (doc/split-documents [document]
              {:max-segment-size 500
               :max-overlap 50}))

;; Use in your RAG pipeline
(doseq [chunk chunks]
  (println (:text chunk)))
```

## Document Loading

### Load Single Document

```clojure
;; Text file (default parser)
(def doc (doc/load-document "/path/to/file.txt"))

;; PDF with Apache Tika
(def pdf (doc/load-document "/path/to/file.pdf"
           {:parser :apache-tika}))

;; With custom parser instance
(def custom-parser (doc/create-parser :apache-tika))
(def doc (doc/load-document "/path/to/file.docx"
           {:parser custom-parser}))
```

### Load Multiple Documents

```clojure
;; Load all documents from directory
(def docs (doc/load-documents "/path/to/docs"
            {:parser :apache-tika}))

;; Recursive (include subdirectories)
(def docs (doc/load-documents "/path/to/docs"
            {:parser :apache-tika
             :recursive? true}))

;; With glob pattern
(def pdfs (doc/load-documents "/path/to/docs"
            {:parser :apache-tika
             :glob "*.pdf"}))

;; Multiple patterns
(def docs (doc/load-documents "/path/to/docs"
            {:parser :apache-tika
             :glob "*.{pdf,docx,txt}"}))
```

### Load from URL

```clojure
(def doc (doc/load-from-url
           "https://example.com/document.pdf"
           {:parser :apache-tika}))
```

## Document Parsers

### Text Parser (Built-in)

Basic parser for plain text files:

```clojure
(def doc (doc/load-document "/path/to/file.txt"
           {:parser :text}))  ;; Default
```

### Apache Tika Parser

Auto-detects and parses many formats:

```clojure
(def parser (doc/create-parser :apache-tika))

(def doc (doc/load-document "/path/to/file.pdf"
           {:parser parser}))
```

**Supported Formats:**
- PDF
- Microsoft Office (DOCX, DOC, XLSX, PPTX, PPT)
- OpenOffice (ODT, ODS, ODP)
- HTML, XML
- Plain text (TXT, CSV)
- Rich Text Format (RTF)
- And many more...

### Custom Parser

```clojure
;; Use any LangChain4j DocumentParser
(import 'dev.langchain4j.data.document.parser.TextDocumentParser)

(def custom-parser (TextDocumentParser.))

(def doc (doc/load-document "/path/to/file.txt"
           {:parser custom-parser}))
```

## Document Structure

### Document Map

Loaded documents are returned as Clojure maps:

```clojure
{:text "Document content here..."
 :metadata {:source "/path/to/file.pdf"
            :file-size 12345
            :content-type "application/pdf"}
 :java-object #<Document...>}  ;; Original Java object
```

### Accessing Content

```clojure
(def doc (doc/load-document "/path/to/file.pdf"
           {:parser :apache-tika}))

;; Get text content
(:text doc)

;; Get metadata
(:metadata doc)

;; Get source path
(get-in doc [:metadata :source])

;; Access Java object if needed
(:java-object doc)
```

## Document Splitting

### Basic Splitting

```clojure
;; Create splitter
(def splitter (doc/recursive-splitter
                {:max-segment-size 500
                 :max-overlap 50}))

;; Split single document
(def segments (doc/split-document document splitter))

;; Split multiple documents
(def all-segments (mapcat #(doc/split-document % splitter) documents))
```

### Convenience Function

```clojure
;; Split multiple documents with inline config
(def segments (doc/split-documents documents
                {:max-segment-size 500
                 :max-overlap 50
                 :unit :tokens}))
```

### Splitting Configuration

```clojure
{:max-segment-size 500     ;; Required: Max tokens/chars per chunk
 :max-overlap 50           ;; Optional: Overlap between chunks (default: 0)
 :unit :tokens             ;; Optional: :tokens or :chars (default: :tokens)
 :tokenizer nil}           ;; Optional: Custom tokenizer
```

## Segment Structure

### Segment Map

Split segments are returned as maps:

```clojure
{:text "Segment content..."
 :metadata {:document-id "doc-123"
            :segment-index 0
            :source "/path/to/file.pdf"}
 :java-object #<TextSegment...>}
```

### Accessing Segments

```clojure
(def segments (doc/split-documents documents
                {:max-segment-size 500}))

;; Get text from first segment
(:text (first segments))

;; Get all segment texts
(map :text segments)

;; Find segments from specific document
(filter #(= "/path/to/file.pdf" 
            (get-in % [:metadata :source]))
        segments)
```

## Complete RAG Pipeline

### Basic Pipeline

```clojure
(require '[langchain4clj.rag.document :as doc])
(require '[langchain4clj.core :as llm])

;; 1. Load documents
(def documents (doc/load-documents "/path/to/knowledge-base"
                 {:parser :apache-tika
                  :recursive? true}))

;; 2. Split into chunks
(def chunks (doc/split-documents documents
              {:max-segment-size 500
               :max-overlap 50}))

;; 3. Store in vector database (pseudo-code)
(doseq [chunk chunks]
  (store-embedding! (:text chunk) (:metadata chunk)))

;; 4. Query with context
(defn query-with-context [question]
  (let [relevant-chunks (search-embeddings question 5)
        context (str/join "\n\n" (map :text relevant-chunks))
        prompt (str "Context:\n" context "\n\nQuestion: " question)]
    (llm/chat model prompt)))

(query-with-context "What is the company policy on remote work?")
```

### Advanced Pipeline with Tools

```clojure
(require '[langchain4clj.tools :as tools])
(require '[langchain4clj.assistant :as assistant])

;; Create search tool
(tools/deftool search-docs
  "Searches the knowledge base"
  {:query string?}
  [{:keys [query]}]
  (let [results (search-embeddings query 3)]
    (str/join "\n\n" (map :text results))))

;; Create assistant with RAG
(def rag-assistant
  (assistant/create-assistant
    {:model model
     :tools [search-docs]
     :system-message "You are a helpful assistant. Use the search tool to find relevant information before answering."}))

(rag-assistant "What are the benefits of our premium plan?")
;; Automatically searches docs and provides informed answer
```

## Chunking Strategies

### Small Chunks (Precise)

```clojure
{:max-segment-size 200
 :max-overlap 20}

;; Good for: Question answering, precise retrieval
;; Pros: More accurate matches
;; Cons: May lose context
```

### Medium Chunks (Balanced)

```clojure
{:max-segment-size 500
 :max-overlap 50}

;; Good for: General purpose RAG
;; Pros: Balance of precision and context
;; Cons: Standard tradeoff
```

### Large Chunks (Contextual)

```clojure
{:max-segment-size 1000
 :max-overlap 100}

;; Good for: Summarization, complex questions
;; Pros: More context preserved
;; Cons: Less precise matching
```

### Token-Based vs Character-Based

```clojure
;; Token-based (recommended for LLMs)
{:max-segment-size 500
 :max-overlap 50
 :unit :tokens}

;; Character-based (simpler, faster)
{:max-segment-size 2000
 :max-overlap 200
 :unit :chars}
```

## Metadata Management

### Preserving Metadata

```clojure
(def doc (doc/load-document "/docs/policy.pdf"
           {:parser :apache-tika}))

;; Metadata is preserved through splitting
(def segments (doc/split-document doc splitter))

;; Each segment has original metadata
(get-in (first segments) [:metadata :source])
;; => "/docs/policy.pdf"
```

### Adding Custom Metadata

```clojure
(defn load-with-metadata [path tags]
  (let [doc (doc/load-document path {:parser :apache-tika})]
    (update doc :metadata merge {:tags tags
                                 :indexed-at (System/currentTimeMillis)})))

(def doc (load-with-metadata "/docs/policy.pdf" 
           ["hr" "policy" "important"]))
```

### Filtering by Metadata

```clojure
(defn filter-segments-by-tag [segments tag]
  (filter #(contains? (set (get-in % [:metadata :tags])) tag)
          segments))

(def hr-segments (filter-segments-by-tag all-segments "hr"))
```

## Common Patterns

### Batch Processing

```clojure
(defn process-directory [dir-path]
  (-> (doc/load-documents dir-path
        {:parser :apache-tika
         :recursive? true})
      (doc/split-documents
        {:max-segment-size 500
         :max-overlap 50})))

(def all-chunks (process-directory "/knowledge-base"))
```

### Incremental Loading

```clojure
(defn process-files-lazy [file-paths]
  (for [path file-paths
        :let [doc (doc/load-document path {:parser :apache-tika})]
        segment (doc/split-document doc splitter)]
    segment))

(def segments (process-files-lazy file-list))
;; Lazy sequence - processes on demand
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

### Progress Tracking

```clojure
(defn load-with-progress [paths]
  (let [total (count paths)]
    (map-indexed
      (fn [idx path]
        (println (str "Processing " (inc idx) "/" total ": " path))
        (doc/load-document path {:parser :apache-tika}))
      paths)))
```

## Performance Optimization

### Parallel Loading

```clojure
(require '[clojure.core.async :as async])

(defn load-documents-parallel [paths]
  (let [results (async/chan)]
    (doseq [path paths]
      (async/go
        (async/>! results
          (doc/load-document path {:parser :apache-tika}))))
    (async/<!! (async/into [] (async/take (count paths) results)))))
```

### Caching

```clojure
(def document-cache (atom {}))

(defn load-cached [path parser]
  (if-let [cached (@document-cache path)]
    cached
    (let [doc (doc/load-document path {:parser parser})]
      (swap! document-cache assoc path doc)
      doc)))
```

### Streaming Large Files

```clojure
;; For very large files, process in streaming fashion
(defn process-large-file [path chunk-fn]
  (let [doc (doc/load-document path {:parser :apache-tika})
        segments (doc/split-document doc splitter)]
    (doseq [segment segments]
      (chunk-fn segment)  ;; Process immediately
      )))

(process-large-file "/huge-document.pdf"
  (fn [segment]
    (store-to-db! segment)
    (println "Processed chunk")))
```

## Integration with Vector Databases

### With Pseudo Vector DB

```clojure
(defn embed-and-store [segments embedding-fn store-fn]
  (doseq [segment segments]
    (let [embedding (embedding-fn (:text segment))]
      (store-fn {:text (:text segment)
                 :embedding embedding
                 :metadata (:metadata segment)}))))

;; Usage
(-> (doc/load-documents "/docs" {:parser :apache-tika})
    (doc/split-documents {:max-segment-size 500})
    (embed-and-store generate-embedding store-in-db))
```

### Search and Retrieve

```clojure
(defn semantic-search [query vector-db]
  (let [query-embedding (generate-embedding query)
        results (vector-search vector-db query-embedding 5)]
    results))

(defn answer-with-rag [question model vector-db]
  (let [relevant-docs (semantic-search question vector-db)
        context (str/join "\n\n" (map :text relevant-docs))
        prompt (str "Use this context to answer:\n\n"
                   context
                   "\n\nQuestion: " question)]
    (llm/chat model prompt)))
```

## Best Practices

### 1. Choose Appropriate Chunk Size

```clojure
;; For question-answering (small, precise)
{:max-segment-size 300 :max-overlap 30}

;; For general RAG (balanced)
{:max-segment-size 500 :max-overlap 50}

;; For summarization (large, contextual)
{:max-segment-size 1000 :max-overlap 100}
```

### 2. Use Overlap for Context

```clojure
;; ❌ No overlap
{:max-segment-size 500 :max-overlap 0}
;; Context might be lost at boundaries

;; ✅ With overlap
{:max-segment-size 500 :max-overlap 50}
;; 10% overlap preserves context
```

### 3. Preserve Metadata

```clojure
;; Add useful metadata for filtering
(defn enrich-metadata [doc]
  (update doc :metadata merge
    {:indexed-at (System/currentTimeMillis)
     :file-type (get-file-type (:source (:metadata doc)))
     :importance (calculate-importance doc)}))
```

### 4. Handle Errors Gracefully

```clojure
(defn robust-load-documents [paths]
  (keep (fn [path]
          (try
            (doc/load-document path {:parser :apache-tika})
            (catch Exception e
              (log/warn e (str "Skipping " path))
              nil)))
        paths))
```

### 5. Monitor Processing

```clojure
(defn load-and-log [path]
  (let [start (System/currentTimeMillis)
        doc (doc/load-document path {:parser :apache-tika})
        elapsed (- (System/currentTimeMillis) start)]
    (log/info (str "Loaded " path " in " elapsed "ms"))
    doc))
```

## Troubleshooting

### Issue: Parsing Fails

**Solution**: Check file format and try different parser

```clojure
(try
  (doc/load-document path {:parser :apache-tika})
  (catch Exception e
    (log/warn "Apache Tika failed, trying text parser")
    (doc/load-document path {:parser :text})))
```

### Issue: Out of Memory

**Solution**: Process files in batches

```clojure
(defn process-in-batches [paths batch-size]
  (doseq [batch (partition-all batch-size paths)]
    (let [docs (map #(doc/load-document % {:parser :apache-tika}) batch)
          segments (doc/split-documents docs {:max-segment-size 500})]
      (store-segments! segments)
      (clear-memory!))))
```

### Issue: Slow Loading

**Solution**: Use parallel loading or caching

```clojure
;; Parallel
(require '[clojure.core.async :as async])
(load-documents-parallel paths)

;; Or cache
(load-cached path :apache-tika)
```

## Configuration Reference

### load-document Options

```clojure
{:parser :text|:apache-tika|parser-instance  ;; Default: :text
}
```

### load-documents Options

```clojure
{:parser :text|:apache-tika|parser-instance  ;; Default: :text
 :recursive? false                           ;; Load from subdirectories
 :glob "*.pdf"}                              ;; Glob pattern to filter
```

### split-documents Options

```clojure
{:max-segment-size 500                       ;; Required
 :max-overlap 50                             ;; Default: 0
 :unit :tokens|:chars                        ;; Default: :tokens
 :tokenizer nil}                             ;; Optional custom tokenizer
```

## Limitations

### 1. Vector Database Not Included

This module only handles document loading and splitting. You'll need to:
- Generate embeddings (using your embedding model)
- Store in vector database (your choice of DB)
- Implement search (using vector similarity)

### 2. No Built-in Embedding

```clojure
;; You need to generate embeddings separately
(defn generate-embedding [text]
  ;; Use OpenAI, Sentence Transformers, etc.
  ...)
```

### 3. Limited Format Support

While Apache Tika supports many formats, some specialized formats may not parse correctly. Always test with your specific documents.

## See Also

- [Assistant System](ASSISTANT.md) - Use RAG with assistants
- [Tools & Function Calling](TOOLS.md) - Create search tools for RAG
- [Structured Output](STRUCTURED_OUTPUT.md) - Extract structured data from documents
