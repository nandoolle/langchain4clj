(ns build
  (:refer-clojure :exclude [test])
  (:require [clojure.tools.build.api :as b]))

(def lib 'io.github.nandoolle/langchain4clj)
(def version "1.0.4")
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def jar-file (format "target/%s-%s.jar" (name lib) version))
(def uber-file (format "target/%s-%s-standalone.jar" (name lib) version))

(defn clean
  "Delete the build target directory"
  [_]
  (println "Cleaning target directory...")
  (b/delete {:path "target"}))

(defn test
  "Run all the tests."
  [opts]
  (println "Running tests...")
  (let [basis (b/create-basis {:aliases [:test]})
        cmds (b/java-command
              {:basis basis
               :main 'clojure.main
               :main-args ["-m" "cognitect.test-runner"]})
        {:keys [exit]} (b/process cmds)]
    (when-not (zero? exit)
      (throw (ex-info "Tests failed" {}))))
  opts)

(defn jar
  "Build a library JAR file for deployment to Clojars"
  [_]
  (clean nil)
  (println "Building JAR..." jar-file)
  (b/write-pom {:class-dir class-dir
                :lib lib
                :version version
                :basis basis
                :src-dirs ["src"]
                :scm {:url "https://github.com/nandoolle/langchain4clj"
                      :connection "scm:git:git://github.com/nandoolle/langchain4clj.git"
                      :developerConnection "scm:git:ssh://git@github.com/nandoolle/langchain4clj.git"
                      :tag (str "v" version)}
                :pom-data [[:description "Pure Clojure wrapper for LangChain4j with idiomatic, data-driven access to multiple LLM providers"]
                           [:url "https://github.com/nandoolle/langchain4clj"]
                           [:licenses
                            [:license
                             [:name "Eclipse Public License 2.0"]
                             [:url "https://www.eclipse.org/legal/epl-2.0/"]]]
                           [:developers
                            [:developer
                             [:name "Fernando Olle"]]]]})
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir})
  (b/jar {:class-dir class-dir
          :jar-file jar-file}))

(defn install
  "Install the JAR locally (useful for testing before deploying)"
  [_]
  (jar nil)
  (println "Installing JAR locally...")
  (b/install {:basis basis
              :lib lib
              :version version
              :jar-file jar-file
              :class-dir class-dir}))

(defn deploy
  "Deploy the JAR to Clojars"
  [_]
  (jar nil)
  (println "Deploying to Clojars...")
  (println "Make sure CLOJARS_USERNAME and CLOJARS_PASSWORD are set")
  ((requiring-resolve 'deps-deploy.deps-deploy/deploy)
   {:installer :remote
    :artifact jar-file
    :pom-file (b/pom-path {:lib lib :class-dir class-dir})}))

(defn ci
  "Run the CI pipeline of tests and build the JAR"
  [opts]
  (test opts)
  (jar opts)
  opts)
