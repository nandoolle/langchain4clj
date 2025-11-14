---
layout: default
title: Image Generation
---

# Image Generation

Generate images from text prompts using DALL-E 3 and DALL-E 2.

## Overview

LangChain4Clj provides a simple API for generating images from text descriptions using OpenAI's DALL-E models. Both DALL-E 3 (highest quality) and DALL-E 2 (faster, cheaper) are supported.

## Quick Start

```clojure
(require '[langchain4clj.image :as image])

;; Create an image model
(def model (image/create-image-model
             {:provider :openai
              :api-key (System/getenv "OPENAI_API_KEY")
              :model "dall-e-3"}))

;; Generate an image
(def result (image/generate model "A sunset over mountains"))

;; Get the URL
(:url result)
;; => "https://oaidalleapiprodscus.blob.core.windows.net/..."

;; DALL-E 3 provides a revised prompt
(:revised-prompt result)
;; => "A picturesque view of a vibrant sunset casting warm hues..."
```

## Creating Image Models

### DALL-E 3 (Recommended)

```clojure
(def dalle3 (image/create-image-model
              {:provider :openai
               :api-key "your-api-key"
               :model "dall-e-3"
               :quality "hd"
               :size "1024x1024"}))
```

### DALL-E 2

```clojure
(def dalle2 (image/create-image-model
              {:provider :openai
               :api-key "your-api-key"
               :model "dall-e-2"
               :size "512x512"}))
```

### Using Convenience Function

```clojure
(def model (image/openai-image-model
             {:api-key "your-api-key"
              :quality "hd"
              :size "1792x1024"}))
```

## Configuration Options

### DALL-E 3 Options

```clojure
{:provider :openai              ;; Required
 :api-key "your-key"           ;; Required
 :model "dall-e-3"             ;; Required
 :size "1024x1024"             ;; Optional, default: "1024x1024"
                                ;; Options: "1024x1024", "1792x1024", "1024x1792"
 :quality "hd"                 ;; Optional, default: "standard"
                                ;; Options: "standard", "hd"
 :style "vivid"                ;; Optional, default: "vivid"
                                ;; Options: "vivid", "natural"
 :response-format "url"        ;; Optional, default: "url"
                                ;; Options: "url", "b64_json"
 :log-requests false           ;; Optional
 :log-responses false}         ;; Optional
```

### DALL-E 2 Options

```clojure
{:provider :openai              ;; Required
 :api-key "your-key"           ;; Required
 :model "dall-e-2"             ;; Required
 :size "512x512"               ;; Optional, default: "1024x1024"
                                ;; Options: "256x256", "512x512", "1024x1024"
 :response-format "url"        ;; Optional, default: "url"
 :log-requests false           ;; Optional
 :log-responses false}         ;; Optional
```

## Image Sizes

### DALL-E 3

- `"1024x1024"` - Square (default)
- `"1792x1024"` - Landscape (wide)
- `"1024x1792"` - Portrait (tall)

### DALL-E 2

- `"256x256"` - Small square
- `"512x512"` - Medium square
- `"1024x1024"` - Large square (default)

## Quality Settings (DALL-E 3 Only)

### Standard Quality

```clojure
(def standard (image/create-image-model
                {:provider :openai
                 :api-key "sk-..."
                 :model "dall-e-3"
                 :quality "standard"}))
```

- Faster generation
- Lower cost
- Good for most use cases

### HD Quality

```clojure
(def hd (image/create-image-model
          {:provider :openai
           :api-key "sk-..."
           :model "dall-e-3"
           :quality "hd"}))
```

- Higher detail and fidelity
- Slower generation
- Higher cost
- Best for professional use

## Style Settings (DALL-E 3 Only)

### Vivid Style (Default)

```clojure
(def vivid (image/openai-image-model
             {:api-key "sk-..."
              :style "vivid"}))
```

- Hyper-real and dramatic
- More saturated colors
- Enhanced contrast

### Natural Style

```clojure
(def natural (image/openai-image-model
               {:api-key "sk-..."
                :style "natural"}))
```

- More realistic and subtle
- Natural colors
- Less dramatic

## Response Formats

### URL (Default)

```clojure
(def result (image/generate model "A cat wearing a hat"))
(:url result)
;; => "https://oaidalleapiprodscus.blob.core.windows.net/..."
```

Images are hosted by OpenAI and expire after some time.

### Base64 JSON

```clojure
(def model (image/create-image-model
             {:provider :openai
              :api-key "sk-..."
              :model "dall-e-3"
              :response-format "b64_json"}))

(def result (image/generate model "A cat"))
(:base64 result)
;; => "iVBORw0KGgoAAAANSUhEUgAAA..."

;; Save to file
(require '[clojure.java.io :as io])
(import 'java.util.Base64)

(let [decoder (Base64/getDecoder)
      image-bytes (.decode decoder (:base64 result))]
  (with-open [out (io/output-stream "cat.png")]
    (.write out image-bytes)))
```

## Common Use Cases

### Generate and Display

```clojure
(def result (image/generate model "A futuristic cityscape"))

;; In a web app
(hiccup/html
  [:img {:src (:url result)
         :alt "Generated image"}])
```

### Generate Multiple Variations

```clojure
(def prompts ["A cat in space"
              "A dog in space"
              "A rabbit in space"])

(def results (map #(image/generate model %) prompts))

(doseq [result results]
  (println "URL:" (:url result)))
```

### HD Landscape Images

```clojure
(def landscape-model (image/openai-image-model
                       {:api-key "sk-..."
                        :quality "hd"
                        :size "1792x1024"}))

(def result (image/generate landscape-model
              "A panoramic view of the Grand Canyon at sunset"))
```

### Portrait Photography Style

```clojure
(def portrait-model (image/openai-image-model
                      {:api-key "sk-..."
                       :quality "hd"
                       :size "1024x1792"
                       :style "natural"}))

(def result (image/generate portrait-model
              "Portrait of a woman in natural lighting"))
```

## Prompt Engineering Tips

### Be Specific

❌ Bad: "A dog"
✅ Good: "A golden retriever puppy playing in a sunny garden"

### Include Style Details

```clojure
(image/generate model 
  "A mountain landscape, oil painting style, warm colors, impressionist")
```

### Specify Composition

```clojure
(image/generate model
  "Close-up portrait of an elderly man, dramatic side lighting, shallow depth of field")
```

### Use Descriptive Adjectives

```clojure
(image/generate model
  "A vibrant, bustling Tokyo street at night, neon signs, rain-soaked pavement")
```

## DALL-E 3: Revised Prompts

DALL-E 3 automatically enhances your prompts for better results:

```clojure
(def result (image/generate dalle3-model "A sunset"))

;; Your prompt
"A sunset"

;; DALL-E 3's revised prompt
(:revised-prompt result)
;; => "A picturesque view of a vibrant sunset casting warm hues of orange, 
;;     pink, and purple across the sky. The sun is half-set on the horizon, 
;;     over a calm ocean. Silhouettes of distant mountains can be seen..."
```

This helps you understand:
- How DALL-E interpreted your prompt
- What details were added
- How to improve future prompts

## Error Handling

```clojure
(try
  (def result (image/generate model "Generate an image"))
  (:url result)
  (catch Exception e
    (println "Image generation failed:" (.getMessage e))
    ;; Handle specific errors
    (cond
      (str/includes? (.getMessage e) "content_policy")
      (println "Prompt violated content policy")
      
      (str/includes? (.getMessage e) "rate_limit")
      (println "Rate limit exceeded, wait and retry")
      
      :else
      (println "Unknown error"))))
```

## Rate Limits and Costs

### DALL-E 3 Pricing (as of 2024)

- Standard (1024×1024): ~$0.040 per image
- Standard (1024×1792, 1792×1024): ~$0.080 per image
- HD (1024×1024): ~$0.080 per image
- HD (1024×1792, 1792×1024): ~$0.120 per image

### DALL-E 2 Pricing

- 1024×1024: ~$0.020 per image
- 512×512: ~$0.018 per image
- 256×256: ~$0.016 per image

### Rate Limits

Check OpenAI's current limits. Typically:
- Tier 1: 5 images per minute
- Tier 2+: Higher limits based on usage

## Comparison: DALL-E 3 vs DALL-E 2

| Feature | DALL-E 3 | DALL-E 2 |
|---------|----------|----------|
| Quality | ⭐⭐⭐⭐⭐ Highest | ⭐⭐⭐ Good |
| Speed | ~10-20 seconds | ~5-10 seconds |
| Cost | Higher | Lower |
| Sizes | 3 options | 3 options |
| HD Quality | ✅ Yes | ❌ No |
| Style Control | ✅ Yes | ❌ No |
| Prompt Enhancement | ✅ Yes | ❌ No |
| Best For | Professional, high-quality | Quick prototypes, lower cost |

## Best Practices

### 1. Choose the Right Model

```clojure
;; For production/professional use
(def dalle3-hd (image/openai-image-model
                 {:api-key "sk-..."
                  :model "dall-e-3"
                  :quality "hd"}))

;; For prototyping/testing
(def dalle2 (image/create-image-model
              {:provider :openai
               :api-key "sk-..."
               :model "dall-e-2"}))
```

### 2. Cache Generated Images

```clojure
(def image-cache (atom {}))

(defn generate-cached [model prompt]
  (if-let [cached (@image-cache prompt)]
    cached
    (let [result (image/generate model prompt)]
      (swap! image-cache assoc prompt result)
      result)))
```

### 3. Handle Errors Gracefully

```clojure
(defn safe-generate [model prompt]
  (try
    (image/generate model prompt)
    (catch Exception e
      {:error (.getMessage e)
       :fallback-url "/images/placeholder.png"})))
```

### 4. Use Threading-First Style

```clojure
(-> {:api-key (System/getenv "OPENAI_API_KEY")
     :quality "hd"
     :size "1792x1024"
     :style "natural"}
    image/openai-image-model
    (image/generate "A serene forest"))
```

### 5. Validate Prompts

```clojure
(defn valid-prompt? [prompt]
  (and (string? prompt)
       (> (count prompt) 5)
       (< (count prompt) 1000)))

(when (valid-prompt? user-prompt)
  (image/generate model user-prompt))
```

## Content Policy

OpenAI's content policy prohibits:
- Violence and gore
- Adult content
- Hateful imagery
- Privacy violations
- Deceptive content

Violations result in errors. Always validate user input.

## Saving Images

### Save URL Image

```clojure
(require '[clojure.java.io :as io])
(import 'java.net.URL)

(defn save-image [url filename]
  (with-open [in (io/input-stream (URL. url))
              out (io/output-stream filename)]
    (io/copy in out)))

(def result (image/generate model "A cat"))
(save-image (:url result) "cat.png")
```

### Save Base64 Image

```clojure
(require '[clojure.java.io :as io])
(import 'java.util.Base64)

(defn save-base64-image [base64-str filename]
  (let [decoder (Base64/getDecoder)
        bytes (.decode decoder base64-str)]
    (with-open [out (io/output-stream filename)]
      (.write out bytes))))

(def model (image/create-image-model
             {:provider :openai
              :api-key "sk-..."
              :model "dall-e-3"
              :response-format "b64_json"}))

(def result (image/generate model "A sunset"))
(save-base64-image (:base64 result) "sunset.png")
```

## See Also

- [Core Chat](README.md#core-chat) - Text generation
- [Structured Output](STRUCTURED_OUTPUT.md) - Combine with image descriptions
- [Assistant System](ASSISTANT.md) - Image generation in assistants
