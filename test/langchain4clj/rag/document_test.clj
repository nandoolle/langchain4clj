(ns langchain4clj.rag.document-test
  (:require [clojure.test :refer [deftest is testing]]
            [langchain4clj.rag.document :as doc])
  (:import [dev.langchain4j.data.document Document DocumentParser DocumentSplitter Metadata]
           [dev.langchain4j.data.document.parser TextDocumentParser]
           [dev.langchain4j.data.document.parser.apache.tika ApacheTikaDocumentParser]
           [dev.langchain4j.data.segment TextSegment]
           [java.nio.file Path]))

;; ============================================================================
;; Test Helpers - Mock Document Creation
;; ============================================================================

(defn- create-mock-document
  "Creates a mock Document for testing."
  ([text]
   (create-mock-document text {}))
  ([text metadata-map]
   (let [metadata (Metadata.)
         _ (doseq [[k v] metadata-map]
             (.put metadata (name k) v))]
     (Document/from text metadata))))

(defn- create-mock-segment
  "Creates a mock TextSegment for testing."
  ([text]
   (create-mock-segment text {}))
  ([text metadata-map]
   (let [metadata (Metadata.)
         _ (doseq [[k v] metadata-map]
             (.put metadata (name k) v))]
     (TextSegment/from text metadata))))

;; ============================================================================
;; Parser Creation Tests
;; ============================================================================

(deftest test-create-parser-text
  (testing "Create text parser"
    (let [parser (doc/create-parser :text)]
      (is (instance? TextDocumentParser parser)))))

(deftest test-create-parser-apache-tika
  (testing "Create Apache Tika parser"
    (let [parser (doc/create-parser :apache-tika)]
      (is (instance? ApacheTikaDocumentParser parser)))))

(deftest test-create-parser-instance
  (testing "Pass parser instance directly"
    (let [parser-instance (TextDocumentParser.)
          result (doc/create-parser parser-instance)]
      (is (identical? parser-instance result)))))

(deftest test-create-parser-invalid
  (testing "Invalid parser type throws exception"
    (is (thrown? IllegalArgumentException
                 (doc/create-parser :invalid)))))

;; ============================================================================
;; Java → Clojure Conversion Tests
;; ============================================================================

(deftest test-document->map
  (testing "Convert Document to Clojure map"
    (let [java-doc (create-mock-document "Test content" {:source "test.txt" :page-count 1})
          clj-doc (doc/document->map java-doc)]

      (is (map? clj-doc))
      (is (= "Test content" (:text clj-doc)))
      (is (map? (:metadata clj-doc)))
      (is (= "test.txt" (get-in clj-doc [:metadata "source"])))
      (is (= 1 (get-in clj-doc [:metadata "page-count"])))
      (is (identical? java-doc (:java-object clj-doc))))))

(deftest test-document->map-nil
  (testing "Convert nil Document returns nil"
    (is (nil? (doc/document->map nil)))))

(deftest test-segment->map
  (testing "Convert TextSegment to Clojure map"
    (let [java-segment (create-mock-segment "Segment content" {:doc-id "123" :index 0})
          clj-segment (doc/segment->map java-segment)]

      (is (map? clj-segment))
      (is (= "Segment content" (:text clj-segment)))
      (is (map? (:metadata clj-segment)))
      (is (= "123" (get-in clj-segment [:metadata "doc-id"])))
      (is (= 0 (get-in clj-segment [:metadata "index"])))
      (is (identical? java-segment (:java-object clj-segment))))))

(deftest test-segment->map-nil
  (testing "Convert nil TextSegment returns nil"
    (is (nil? (doc/segment->map nil)))))

;; ============================================================================
;; Document Loading Tests (with mocks)
;; ============================================================================

(deftest test-load-document-basic
  (testing "Load single document with default parser"
    (let [mock-doc (create-mock-document "File content" {:source "test.txt"})]
      (with-redefs [doc/load-document-from-filesystem
                    (fn [^Path _path ^DocumentParser parser]
                      (is (instance? TextDocumentParser parser))
                      mock-doc)]

        (let [result (doc/load-document "/path/to/test.txt")]
          (is (map? result))
          (is (= "File content" (:text result)))
          (is (= "test.txt" (get-in result [:metadata "source"]))))))))

(deftest test-load-document-with-parser
  (testing "Load document with specified parser"
    (let [mock-doc (create-mock-document "PDF content")]
      (with-redefs [doc/load-document-from-filesystem
                    (fn [^Path _path ^DocumentParser parser]
                      (is (instance? ApacheTikaDocumentParser parser))
                      mock-doc)]

        (let [result (doc/load-document "/path/to/test.pdf" {:parser :apache-tika})]
          (is (map? result))
          (is (= "PDF content" (:text result))))))))

(deftest test-load-documents-basic
  (testing "Load multiple documents from directory"
    (let [mock-docs [(create-mock-document "Doc 1" {:source "file1.txt"})
                     (create-mock-document "Doc 2" {:source "file2.txt"})]]
      (with-redefs [doc/load-documents-from-filesystem
                    (fn [^Path _path ^DocumentParser _parser]
                      mock-docs)]

        (let [result (doc/load-documents "/path/to/docs")]
          (is (vector? result))
          (is (= 2 (count result)))
          (is (= "Doc 1" (:text (first result))))
          (is (= "Doc 2" (:text (second result)))))))))

(deftest test-load-documents-recursive
  (testing "Load documents recursively"
    (let [mock-docs [(create-mock-document "Doc 1")
                     (create-mock-document "Doc 2")
                     (create-mock-document "Doc 3")]]
      (with-redefs [doc/load-documents-recursively-from-filesystem
                    (fn [^Path _path ^DocumentParser _parser]
                      mock-docs)]

        (let [result (doc/load-documents "/path/to/docs" {:recursive? true})]
          (is (vector? result))
          (is (= 3 (count result))))))))

(deftest test-load-documents-with-glob
  (testing "Load documents with glob pattern"
    (let [mock-docs [(create-mock-document "Text file")]]
      (with-redefs [doc/load-documents-with-glob-from-filesystem
                    (fn [^Path _path ^String glob ^DocumentParser _parser]
                      (is (= "*.txt" glob))
                      mock-docs)]

        (let [result (doc/load-documents "/path/to/docs" {:glob "*.txt"})]
          (is (vector? result))
          (is (= 1 (count result))))))))

(deftest test-load-from-url
  (testing "Load document from URL"
    (let [mock-doc (create-mock-document "URL content")]
      (with-redefs [doc/load-document-from-url
                    (fn [_url _parser]
                      mock-doc)]

        (let [result (doc/load-from-url "https://example.com/doc.txt")]
          (is (map? result))
          (is (= "URL content" (:text result))))))))

;; ============================================================================
;; Document Splitting Tests
;; ============================================================================

(deftest test-recursive-splitter-creation
  (testing "Create recursive splitter with token-based sizing"
    (let [splitter (doc/recursive-splitter {:max-segment-size 500 :max-overlap 50})]
      (is (instance? DocumentSplitter splitter)))))

(deftest test-recursive-splitter-chars
  (testing "Create recursive splitter with character-based sizing"
    (let [splitter (doc/recursive-splitter {:max-segment-size 1000
                                            :max-overlap 100
                                            :unit :chars})]
      (is (instance? DocumentSplitter splitter)))))

(deftest test-recursive-splitter-missing-size
  (testing "Recursive splitter requires max-segment-size"
    (is (thrown? IllegalArgumentException
                 (doc/recursive-splitter {:max-overlap 50})))))

(deftest test-recursive-splitter-invalid-unit
  (testing "Invalid unit throws exception"
    (is (thrown? IllegalArgumentException
                 (doc/recursive-splitter {:max-segment-size 500 :unit :invalid})))))

(deftest test-split-document
  (testing "Split document into segments"
    (let [java-doc (create-mock-document "This is a test document for splitting.")
          mock-segments [(create-mock-segment "This is a test" {:index 0})
                         (create-mock-segment "document for splitting." {:index 1})]
          mock-splitter (reify DocumentSplitter
                          (split [_ _doc]
                            mock-segments))
          result (doc/split-document java-doc mock-splitter)]

      (is (vector? result))
      (is (= 2 (count result)))
      (is (= "This is a test" (:text (first result))))
      (is (= "document for splitting." (:text (second result)))))))

(deftest test-split-document-with-map
  (testing "Split document map (not java object)"
    (let [java-doc (create-mock-document "Test document")
          doc-map (doc/document->map java-doc)
          mock-segments [(create-mock-segment "Test" {:index 0})
                         (create-mock-segment "document" {:index 1})]
          mock-splitter (reify DocumentSplitter
                          (split [_ _doc]
                            mock-segments))
          result (doc/split-document doc-map mock-splitter)]

      (is (vector? result))
      (is (= 2 (count result))))))

(deftest test-split-documents-with-defaults
  (testing "Split multiple documents with default settings"
    (let [docs [(create-mock-document "Document 1")
                (create-mock-document "Document 2")]
          doc-maps (mapv doc/document->map docs)
          call-count (atom 0)]

      (with-redefs [doc/split-document (fn [_doc _splitter]
                                         (swap! call-count inc)
                                         [(doc/segment->map
                                           (create-mock-segment
                                            (str "Segment from doc " @call-count)))])]

        (let [result (doc/split-documents doc-maps)]
          (is (vector? result))
          (is (= 2 (count result)))
          (is (= 2 @call-count)))))))

(deftest test-split-documents-with-custom-options
  (testing "Split documents with custom options"
    (let [docs [(create-mock-document "Test")]
          doc-maps (mapv doc/document->map docs)
          splitter-config (atom nil)]

      (with-redefs [doc/recursive-splitter (fn [config]
                                             (reset! splitter-config config)
                                             (reify DocumentSplitter
                                               (split [_ _doc]
                                                 [(create-mock-segment "segment")])))
                    doc/split-document (fn [_doc _splitter]
                                         [(doc/segment->map
                                           (create-mock-segment "segment"))])]

        (let [result (doc/split-documents doc-maps {:max-segment-size 1000
                                                    :max-overlap 100
                                                    :unit :chars})]
          (is (vector? result))
          (is (= 1000 (:max-segment-size @splitter-config)))
          (is (= 100 (:max-overlap @splitter-config)))
          (is (= :chars (:unit @splitter-config))))))))

;; ============================================================================
;; Integration-Style Tests
;; ============================================================================

(deftest test-load-and-split-workflow
  (testing "Complete load and split workflow"
    (let [mock-doc (create-mock-document "This is a long document that needs to be split into segments.")
          mock-segments [(create-mock-segment "This is a long document")
                         (create-mock-segment "that needs to be split")
                         (create-mock-segment "into segments.")]]

      (with-redefs [doc/load-document-from-filesystem
                    (fn [_ _] mock-doc)
                    doc/split-document (fn [_ _] (mapv doc/segment->map mock-segments))]

        (let [doc (doc/load-document "/path/to/file.txt")
              splitter (doc/recursive-splitter {:max-segment-size 500 :max-overlap 50})
              segments (doc/split-document doc splitter)]

          (is (map? doc))
          (is (= 3 (count segments)))
          (is (every? #(contains? % :text) segments))
          (is (every? #(contains? % :metadata) segments))
          (is (every? #(contains? % :java-object) segments)))))))

(deftest test-threading-first-pattern
  (testing "Threading-first pattern works correctly"
    (let [mock-docs [(create-mock-document "Doc 1")
                     (create-mock-document "Doc 2")]
          mock-segments [(create-mock-segment "Seg 1")
                         (create-mock-segment "Seg 2")]]

      (with-redefs [doc/load-documents-from-filesystem
                    (fn [_ _] mock-docs)
                    doc/split-document (fn [_ _] [(doc/segment->map (first mock-segments))])]

        (let [result (-> "/path/to/docs"
                         (doc/load-documents {:parser :text})
                         (doc/split-documents {:max-segment-size 500}))]

          (is (vector? result))
          (is (every? map? result)))))))
