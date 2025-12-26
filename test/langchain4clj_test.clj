(ns langchain4clj-test
  (:require [clojure.test :refer [deftest is testing]]
            [langchain4clj :as lc4j])
  (:import [dev.langchain4j.model.chat ChatModel]))

(deftest test-create-model-openai
  (testing "Criação de modelo OpenAI com configuração mínima"
    (with-redefs [lc4j/build-openai-model (fn [_config]
                                            (reify ChatModel
                                              (^String chat [_ ^String msg]
                                                (str "Mock OpenAI response: " msg))))]
      (let [model (lc4j/create-model {:provider :openai
                                      :api-key "test-key"})]
        (is (instance? ChatModel model))
        (is (= "Mock OpenAI response: test"
               (lc4j/chat model "test")))))))

(deftest test-create-model-anthropic
  (testing "Criação de modelo Anthropic com configuração mínima"
    (with-redefs [lc4j/build-anthropic-model (fn [_config]
                                               (reify ChatModel
                                                 (^String chat [_ ^String msg]
                                                   (str "Mock Anthropic response: " msg))))]
      (let [model (lc4j/create-model {:provider :anthropic
                                      :api-key "test-key"})]
        (is (instance? ChatModel model))
        (is (= "Mock Anthropic response: test"
               (lc4j/chat model "test")))))))

(deftest test-create-model-unsupported-provider
  (testing "Unsupported provider should throw exception"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"Unsupported provider"
         (lc4j/create-model {:provider :unsupported
                             :api-key "test-key"})))))

(deftest test-create-model-with-custom-config
  (testing "Criação de modelo com configurações customizadas"
    (let [config {:provider :openai
                  :api-key "test-key"
                  :model "gpt-4"
                  :temperature 0.5
                  :timeout 30000
                  :log-requests true
                  :log-responses true}
          build-called (atom nil)]
      (with-redefs [lc4j/build-openai-model (fn [cfg]
                                              (reset! build-called cfg)
                                              (reify ChatModel
                                                (^String chat [_ ^String msg] msg)))]
        (lc4j/create-model config)
        (is (= config @build-called))))))

(deftest test-chat-basic
  (testing "Função chat retorna resposta do modelo"
    (let [mock-model (reify ChatModel
                       (^String chat [_ ^String msg]
                         (str "Response: " msg)))
          response (lc4j/chat mock-model "Hello")]
      (is (= "Response: Hello" response)))))