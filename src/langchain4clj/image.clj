(ns langchain4clj.image
  "Image generation support for LangChain4j models."
  (:require [langchain4clj.macros :as macros])
  (:import [dev.langchain4j.model.image ImageModel]
           [dev.langchain4j.model.openai OpenAiImageModel]
           [dev.langchain4j.data.image Image]))

(defn- image->map [^Image image]
  (when image
    {:url (str (.url image))
     :base64 (.base64Data image)
     :revised-prompt (.revisedPrompt image)}))

(macros/defbuilder build-openai-image-model
  (OpenAiImageModel/builder)
  {:api-key :apiKey
   :model :modelName
   :size :size
   :quality :quality
   :style :style
   :user :user
   :response-format :responseFormat
   :log-requests :logRequests
   :log-responses :logResponses})

(defmulti create-image-model
  "Creates an image model. See docs/IMAGE.md for details."
  :provider)

(defmethod create-image-model :openai
  [{:keys [api-key model size quality style user response-format log-requests log-responses]
    :or {model "dall-e-3"}}]
  (build-openai-image-model
   (cond-> {:api-key api-key :model model}
     size (assoc :size size)
     quality (assoc :quality quality)
     style (assoc :style style)
     user (assoc :user user)
     response-format (assoc :response-format response-format)
     log-requests (assoc :log-requests log-requests)
     log-responses (assoc :log-responses log-responses))))

(defn generate
  "Generates an image from a text prompt. Returns map with :url, :base64, :revised-prompt."
  [^ImageModel model prompt]
  {:pre [(some? model) (string? prompt)]}
  (let [response (.generate model prompt)
        image (.content response)]
    (image->map image)))

(defn openai-image-model
  "Convenience function for creating OpenAI image models."
  [config]
  (create-image-model (assoc config :provider :openai)))

(comment
  (require '[langchain4clj.image :as image])

  (def model (image/create-image-model
              {:provider :openai
               :api-key (System/getenv "OPENAI_API_KEY")
               :model "dall-e-3"}))

  (def result (image/generate model "A beautiful sunset"))
  (:url result))
