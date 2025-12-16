---
layout: default
title: Image Generation
---

# Image Generation

Generate images from text prompts using DALL-E 3 and DALL-E 2.

## Quick Start

```clojure
(require '[langchain4clj.image :as image])

(def model (image/create-image-model
             {:provider :openai
              :api-key (System/getenv "OPENAI_API_KEY")
              :model "dall-e-3"}))

(def result (image/generate model "A sunset over mountains"))

(:url result)
;; => "https://oaidalleapiprodscus.blob.core.windows.net/..."

(:revised-prompt result)  ;; DALL-E 3 only
;; => "A picturesque view of a vibrant sunset..."
```

## Creating Models

```clojure
;; DALL-E 3 (recommended)
(def dalle3 (image/create-image-model
              {:provider :openai
               :api-key "your-api-key"
               :model "dall-e-3"
               :quality "hd"
               :size "1024x1024"}))

;; DALL-E 2
(def dalle2 (image/create-image-model
              {:provider :openai
               :api-key "your-api-key"
               :model "dall-e-2"
               :size "512x512"}))

;; Convenience function
(def model (image/openai-image-model
             {:api-key "your-api-key"
              :quality "hd"
              :size "1792x1024"}))
```

## Configuration Options

### DALL-E 3

```clojure
{:provider :openai
 :api-key "your-key"
 :model "dall-e-3"
 :size "1024x1024"       ;; "1024x1024", "1792x1024", "1024x1792"
 :quality "hd"           ;; "standard", "hd"
 :style "vivid"          ;; "vivid", "natural"
 :response-format "url"} ;; "url", "b64_json"
```

### DALL-E 2

```clojure
{:provider :openai
 :api-key "your-key"
 :model "dall-e-2"
 :size "512x512"         ;; "256x256", "512x512", "1024x1024"
 :response-format "url"}
```

## Response Formats

```clojure
;; URL (default)
(def result (image/generate model "A cat"))
(:url result)

;; Base64
(def model (image/create-image-model
             {:provider :openai
              :api-key "sk-..."
              :model "dall-e-3"
              :response-format "b64_json"}))

(def result (image/generate model "A cat"))
(:base64 result)
```

## Saving Images

```clojure
(require '[clojure.java.io :as io])
(import 'java.net.URL)
(import 'java.util.Base64)

;; Save from URL
(defn save-image [url filename]
  (with-open [in (io/input-stream (URL. url))
              out (io/output-stream filename)]
    (io/copy in out)))

(save-image (:url result) "image.png")

;; Save from Base64
(defn save-base64-image [base64-str filename]
  (let [bytes (.decode (Base64/getDecoder) base64-str)]
    (with-open [out (io/output-stream filename)]
      (.write out bytes))))

(save-base64-image (:base64 result) "image.png")
```

## Common Patterns

```clojure
;; HD Landscape
(def landscape-model (image/openai-image-model
                       {:api-key "sk-..."
                        :quality "hd"
                        :size "1792x1024"}))

;; Portrait with natural style
(def portrait-model (image/openai-image-model
                      {:api-key "sk-..."
                       :quality "hd"
                       :size "1024x1792"
                       :style "natural"}))

;; Generate multiple variations
(def prompts ["A cat in space" "A dog in space" "A rabbit in space"])
(def results (map #(image/generate model %) prompts))
```

## Error Handling

```clojure
(try
  (image/generate model "Generate an image")
  (catch Exception e
    (cond
      (str/includes? (.getMessage e) "content_policy")
      (println "Prompt violated content policy")
      
      (str/includes? (.getMessage e) "rate_limit")
      (println "Rate limit exceeded")
      
      :else
      (println "Error:" (.getMessage e)))))
```

## Related

- [Core Chat](CORE_CHAT.md) - Text generation
- [Assistant System](ASSISTANT.md) - Image generation in assistants
