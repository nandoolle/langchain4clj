(ns langchain4clj.rag.document
  "Document loading and splitting for RAG."
  (:require [langchain4clj.macros :as macros])
  (:import [dev.langchain4j.data.document Document DocumentParser DocumentSplitter Metadata]
           [dev.langchain4j.data.document.loader FileSystemDocumentLoader UrlDocumentLoader]
           [dev.langchain4j.data.document.parser TextDocumentParser]
           [dev.langchain4j.data.document.parser.apache.tika ApacheTikaDocumentParser]
           [dev.langchain4j.data.document.splitter DocumentSplitters]
           [dev.langchain4j.data.segment TextSegment]
           [java.nio.file Path Paths]
           [java.net URL]))

(defmulti create-parser
  "Creates a DocumentParser. Supported: :text, :apache-tika"
  identity)

(defmethod create-parser :text
  [_]
  (TextDocumentParser.))

(defmethod create-parser :apache-tika
  [_]
  (ApacheTikaDocumentParser.))

(defmethod create-parser :default
  [parser-type]
  (if (instance? DocumentParser parser-type)
    parser-type
    (throw (IllegalArgumentException.
            (str "Unknown parser type: " parser-type
                 ". Supported types: :text, :apache-tika")))))

(defn- metadata->map
  [^Metadata metadata]
  (when metadata
    (into {} (.toMap metadata))))

(defn document->map
  "Converts Document to map with :text, :metadata, :java-object."
  [^Document document]
  (when document
    {:text (.text document)
     :metadata (metadata->map (.metadata document))
     :java-object document}))

(defn segment->map
  "Converts TextSegment to map with :text, :metadata, :java-object."
  [^TextSegment segment]
  (when segment
    {:text (.text segment)
     :metadata (metadata->map (.metadata segment))
     :java-object segment}))

(defn- load-document-from-filesystem
  ^Document [^Path path ^DocumentParser parser]
  (FileSystemDocumentLoader/loadDocument path parser))

(defn- load-documents-from-filesystem
  ^java.util.List [^Path path ^DocumentParser parser]
  (FileSystemDocumentLoader/loadDocuments path parser))

(defn- load-document-from-url
  ^Document [^URL url ^DocumentParser parser]
  (UrlDocumentLoader/load url parser))

(defn- load-documents-recursively-from-filesystem
  ^java.util.List [^Path path ^DocumentParser parser]
  (FileSystemDocumentLoader/loadDocumentsRecursively path parser))

(defn- load-documents-with-glob-from-filesystem
  ^java.util.List [^Path path ^String glob ^DocumentParser parser]
  (FileSystemDocumentLoader/loadDocuments path glob parser))

(defn load-document
  "Loads a document from path. Opts: :parser (:text or :apache-tika)."
  ([path]
   (load-document path {}))
  ([path {:keys [parser] :or {parser :text}}]
   (let [parser-instance (create-parser parser)
         java-path (Paths/get path (into-array String []))
         ^Document document (load-document-from-filesystem java-path parser-instance)]
     (document->map document))))

(defn load-documents
  "Loads documents from directory. Opts: :parser, :recursive?, :glob."
  ([path]
   (load-documents path {}))
  ([path {:keys [parser recursive? glob] :or {parser :text recursive? false}}]
   (let [parser-instance (create-parser parser)
         java-path (Paths/get path (into-array String []))
         documents (cond
                     ;; With glob pattern
                     glob
                     (load-documents-with-glob-from-filesystem java-path glob parser-instance)

                     ;; Recursive
                     recursive?
                     (load-documents-recursively-from-filesystem java-path parser-instance)

                     ;; Non-recursive (default)
                     :else
                     (load-documents-from-filesystem java-path parser-instance))]
     (mapv document->map documents))))

(defn load-from-url
  "Loads a document from URL. Opts: :parser (:text or :apache-tika)."
  ([url]
   (load-from-url url {}))
  ([url {:keys [parser] :or {parser :text}}]
   (let [parser-instance (create-parser parser)
         java-url (URL. url)
         ^Document document (load-document-from-url java-url parser-instance)]
     (document->map document))))

(defn recursive-splitter
  "Creates a recursive splitter. Opts: :max-segment-size, :max-overlap, :unit (:tokens/:chars)."
  [{:keys [max-segment-size max-overlap unit tokenizer]
    :or {max-overlap 0 unit :tokens}}]
  (when-not max-segment-size
    (throw (IllegalArgumentException. ":max-segment-size is required")))
  (case unit
    :tokens (if tokenizer
              (DocumentSplitters/recursive max-segment-size max-overlap tokenizer)
              (DocumentSplitters/recursive max-segment-size max-overlap nil))
    :chars (DocumentSplitters/recursive max-segment-size max-overlap)
    (throw (IllegalArgumentException.
            (str "Invalid :unit " unit ". Must be :tokens or :chars")))))

(defn split-document
  "Splits document into segments using splitter."
  [document ^DocumentSplitter splitter]
  (let [^Document java-doc (if (map? document)
                             (:java-object document)
                             document)
        segments (.split splitter java-doc)]
    (mapv segment->map segments)))

(defn split-documents
  "Splits documents into segments. Opts: :max-segment-size, :max-overlap, :unit."
  ([documents]
   (split-documents documents {}))
  ([documents opts]
   (let [config (macros/with-defaults opts
                  {:max-segment-size 500
                   :max-overlap 50
                   :unit :tokens})
         splitter (recursive-splitter config)]
     (vec (mapcat #(split-document % splitter) documents)))))
