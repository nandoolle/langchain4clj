(ns langchain4clj.resilience
  "Provider failover and circuit breaker for high availability."
  (:require [langchain4clj.constants :as const]
            [clojure.string :as str]
            [clojure.tools.logging :as log])
  (:import [dev.langchain4j.model.chat ChatModel]
           [dev.langchain4j.model.chat.request ChatRequest]
           [dev.langchain4j.model.chat.response ChatResponse]))

(defn- retryable-error?
  "Rate limits, timeouts, temporary unavailability - retry on same provider."
  [^Exception exception]
  (let [msg (str (.getMessage exception) " " (.getClass exception))]
    (or
     ;; Rate limiting
     (str/includes? msg "429")
     (str/includes? msg "rate limit")
     (str/includes? msg "too many requests")

     ;; Temporary unavailability
     (str/includes? msg "503")
     (str/includes? msg "Service Unavailable")
     (str/includes? msg "temporarily unavailable")

     ;; Timeouts
     (str/includes? msg "timeout")
     (str/includes? msg "timed out")
     (str/includes? msg "SocketTimeoutException"))))

(defn- recoverable-error?
  "Auth failures, not found, connection issues - try next provider."
  [^Exception exception]
  (let [msg (str (.getMessage exception) " " (.getClass exception))]
    (or
     ;; Authentication/Authorization
     (str/includes? msg "401")
     (str/includes? msg "Unauthorized")
     (str/includes? msg "Invalid API key")
     (str/includes? msg "authentication")

     (str/includes? msg "403")
     (str/includes? msg "Forbidden")

     ;; Resource not found
     (str/includes? msg "404")
     (str/includes? msg "not found")
     (str/includes? msg "model not found")

     ;; Connection issues
     (str/includes? msg "connection")
     (str/includes? msg "ConnectException")
     (str/includes? msg "network")
     (str/includes? msg "unreachable"))))

(defn- non-recoverable-error?
  "Invalid input, quota exceeded - throw immediately."
  [^Exception exception]
  (let [msg (str (.getMessage exception) " " (.getClass exception))]
    (or
     ;; Bad request - user's fault
     (str/includes? msg "400")
     (str/includes? msg "Bad Request")
     (str/includes? msg "invalid")

     ;; Quota/billing issues
     (str/includes? msg "quota")
     (str/includes? msg "billing")
     (str/includes? msg "payment"))))

(defn- retry-provider
  "Calls provider with retries. Returns response, nil (try next), or throws."
  [provider message-or-request max-retries delay-ms chat-fn]
  (loop [attempt 0
         _last-error nil]
    (let [result (try
                   {:success true
                    :value (chat-fn provider message-or-request)}
                   (catch Exception e
                     {:success false
                      :error e}))]
      (if (:success result)
        ;; Success - return the value
        (do
          (when (> attempt 0)
            (log/info "Provider call succeeded after" attempt "retries"))
          (:value result))

        ;; Error - handle based on type
        (let [e (:error result)]
          (cond
            ;; Non-recoverable error - throw immediately
            (non-recoverable-error? e)
            (do
              (log/error "Non-recoverable error from provider:" (.getMessage e))
              (throw e))

            ;; Retryable error and we have retries left
            (and (retryable-error? e)
                 (< attempt max-retries))
            (do
              (log/warn "Retry attempt" (inc attempt) "/" max-retries "after retryable error:" (.getMessage e))
              (Thread/sleep delay-ms)
              (recur (inc attempt) e))

            ;; Retryable but retries exhausted - try next provider
            (retryable-error? e)
            (do
              (log/debug "All retries exhausted after retryable error, trying next provider")
              nil)

            ;; Recoverable error - try next provider
            (recoverable-error? e)
            (do
              (log/debug "Recoverable error from provider, trying next:" (.getMessage e))
              nil)

            ;; Unknown error - treat as non-recoverable
            :else
            (do
              (log/error "Unknown error from provider:" (.getMessage e))
              (throw e))))))))

(defn- try-providers-with-retry
  "Tries providers in sequence for String chat."
  [providers ^String message max-retries delay-ms]
  (loop [remaining-providers providers
         providers-tried []
         provider-index 0]
    (if-let [provider (first remaining-providers)]
      (do
        (when (> provider-index 0)
          (log/warn "Failing over to fallback provider" provider-index))
        (if-let [result (retry-provider provider message max-retries delay-ms
                                        (fn [p m] (.chat ^ChatModel p m)))]
          result
          (recur (rest remaining-providers)
                 (conj providers-tried provider)
                 (inc provider-index))))

      ;; All providers failed
      (do
        (log/error "All" (count providers) "providers failed")
        (throw (ex-info "All providers failed"
                        {:providers-count (count providers)
                         :providers-tried (count providers-tried)}))))))

(defn- try-providers-with-retry-request
  "Tries providers in sequence for ChatRequest."
  [providers ^ChatRequest request max-retries delay-ms]
  (loop [remaining-providers providers
         providers-tried []
         provider-index 0]
    (if-let [provider (first remaining-providers)]
      (do
        (when (> provider-index 0)
          (log/warn "Failing over to fallback provider" provider-index))
        (if-let [result (retry-provider provider request max-retries delay-ms
                                        (fn [p r] (.chat ^ChatModel p r)))]
          result
          (recur (rest remaining-providers)
                 (conj providers-tried provider)
                 (inc provider-index))))

      ;; All providers failed
      (do
        (log/error "All" (count providers) "providers failed")
        (throw (ex-info "All providers failed"
                        {:providers-count (count providers)
                         :providers-tried (count providers-tried)}))))))

(defn- create-circuit-breaker-state
  "Creates initial circuit breaker state."
  []
  (atom {:state :closed
         :failure-count 0
         :success-count 0
         :last-failure-time nil
         :total-calls 0
         :total-failures 0}))

(defn- circuit-breaker-half-open?
  [state-atom timeout-ms]
  (let [state @state-atom]
    (and (= :open (:state state))
         (:last-failure-time state)
         (>= (- (System/currentTimeMillis) (:last-failure-time state))
             timeout-ms))))

(defn- record-success!
  [state-atom success-threshold]
  (let [old-state (:state @state-atom)
        new-state (swap! state-atom
                         (fn [state]
                           (let [new-success-count (inc (:success-count state))]
                             (case (:state state)
                               :closed
                               (-> state
                                   (assoc :failure-count 0)
                                   (update :total-calls inc))

                               :half-open
                               (if (>= new-success-count success-threshold)
                                 ;; Enough successes - close the circuit
                                 {:state :closed
                                  :failure-count 0
                                  :success-count 0
                                  :last-failure-time nil
                                  :total-calls (inc (:total-calls state))
                                  :total-failures (:total-failures state)}
                                 ;; Not enough yet - keep counting
                                 (-> state
                                     (assoc :success-count new-success-count)
                                     (update :total-calls inc)))

                               :open
                               (update state :total-calls inc)))))]
    ;; Log state transitions
    (when (and (= :half-open old-state) (= :closed (:state new-state)))
      (log/info "Circuit breaker transitioned to Closed state"))
    new-state))

(defn- record-failure!
  [state-atom failure-threshold]
  (let [old-state (:state @state-atom)
        new-state (swap! state-atom
                         (fn [state]
                           (let [new-failure-count (inc (:failure-count state))]
                             (case (:state state)
                               :closed
                               (if (>= new-failure-count failure-threshold)
                                 ;; Too many failures - open the circuit
                                 {:state :open
                                  :failure-count new-failure-count
                                  :success-count 0
                                  :last-failure-time (System/currentTimeMillis)
                                  :total-calls (inc (:total-calls state))
                                  :total-failures (inc (:total-failures state))}
                                 ;; Not enough yet - keep counting
                                 (-> state
                                     (assoc :failure-count new-failure-count)
                                     (update :total-calls inc)
                                     (update :total-failures inc)))

                               :half-open
                               ;; Any failure in half-open - back to open
                               {:state :open
                                :failure-count new-failure-count
                                :success-count 0
                                :last-failure-time (System/currentTimeMillis)
                                :total-calls (inc (:total-calls state))
                                :total-failures (inc (:total-failures state))}

                               :open
                               (-> state
                                   (update :total-calls inc)
                                   (update :total-failures inc))))))]
    ;; Log state transitions
    (when (and (= :closed old-state) (= :open (:state new-state)))
      (log/warn "Circuit breaker opened due to failure threshold"))
    (when (and (= :half-open old-state) (= :open (:state new-state)))
      (log/warn "Circuit breaker reopened after failed test"))
    new-state))

(defn- transition-to-half-open!
  [state-atom]
  (let [old-state (:state @state-atom)
        new-state (swap! state-atom
                         (fn [state]
                           (if (= :open (:state state))
                             (assoc state
                                    :state :half-open
                                    :success-count 0
                                    :failure-count 0)
                             state)))]
    (when (and (= :open old-state) (= :half-open (:state new-state)))
      (log/info "Circuit breaker transitioned to Half-Open state for testing"))
    new-state))

(defn- should-allow-request?
  [state-atom timeout-ms]
  (let [state @state-atom]
    (case (:state state)
      :closed true
      :half-open true
      :open (when (circuit-breaker-half-open? state-atom timeout-ms)
              (transition-to-half-open! state-atom)
              true))))

(defn- retry-provider-with-circuit-breaker
  [provider message-or-request max-retries delay-ms chat-fn
   circuit-breaker-state cb-config]

  (let [{:keys [failure-threshold success-threshold timeout-ms]} cb-config]
    ;; Check if circuit breaker allows the request
    (if-not (should-allow-request? circuit-breaker-state timeout-ms)
      ;; Circuit is open - skip this provider
      (do
        (log/warn "Circuit breaker is open, skipping provider")
        nil)

      ;; Circuit allows request - try with retry logic
      (loop [attempt 0
             _last-error nil]
        (let [result (try
                       {:success true
                        :value (chat-fn provider message-or-request)}
                       (catch Exception e
                         {:success false
                          :error e}))]

          (if (:success result)
            ;; Success - record and return
            (do
              (when (> attempt 0)
                (log/info "Provider call succeeded after" attempt "retries"))
              (record-success! circuit-breaker-state success-threshold)
              (:value result))

            ;; Error - handle based on type
            (let [e (:error result)]
              (cond
                ;; Non-recoverable error - record failure and throw
                (non-recoverable-error? e)
                (do
                  (log/error "Non-recoverable error from provider:" (.getMessage e))
                  (record-failure! circuit-breaker-state failure-threshold)
                  (throw e))

                ;; Retryable error and we have retries left
                (and (retryable-error? e)
                     (< attempt max-retries))
                (do
                  (log/warn "Retry attempt" (inc attempt) "/" max-retries "after retryable error:" (.getMessage e))
                  (Thread/sleep delay-ms)
                  (recur (inc attempt) e))

                ;; Retryable but retries exhausted - record failure, try next
                (retryable-error? e)
                (do
                  (log/debug "All retries exhausted after retryable error, trying next provider")
                  (record-failure! circuit-breaker-state failure-threshold)
                  nil)

                ;; Recoverable error - record failure, try next provider
                (recoverable-error? e)
                (do
                  (log/debug "Recoverable error from provider, trying next:" (.getMessage e))
                  (record-failure! circuit-breaker-state failure-threshold)
                  nil)

                ;; Unknown error - record failure and throw
                :else
                (do
                  (log/error "Unknown error from provider:" (.getMessage e))
                  (record-failure! circuit-breaker-state failure-threshold)
                  (throw e))))))))))

(defn- try-providers-with-circuit-breaker
  [providers message max-retries delay-ms circuit-breakers cb-config]
  (loop [remaining-providers providers
         remaining-breakers circuit-breakers
         providers-tried []
         provider-index 0]
    (if-let [provider (first remaining-providers)]
      (let [breaker (first remaining-breakers)]
        (when (> provider-index 0)
          (log/warn "Failing over to fallback provider" provider-index))
        (if-let [result (retry-provider-with-circuit-breaker
                         provider message max-retries delay-ms
                         (fn [p m] (.chat ^ChatModel p m))
                         breaker cb-config)]
          result
          (recur (rest remaining-providers)
                 (rest remaining-breakers)
                 (conj providers-tried provider)
                 (inc provider-index))))

      ;; All providers failed or were skipped
      (do
        (log/error "All" (count providers) "providers failed or unavailable")
        (throw (ex-info "All providers failed or unavailable"
                        {:providers-count (count providers)
                         :providers-tried (count providers-tried)}))))))

(defn- try-providers-with-circuit-breaker-request
  [providers request max-retries delay-ms circuit-breakers cb-config]
  (loop [remaining-providers providers
         remaining-breakers circuit-breakers
         providers-tried []
         provider-index 0]
    (if-let [provider (first remaining-providers)]
      (let [breaker (first remaining-breakers)]
        (when (> provider-index 0)
          (log/warn "Failing over to fallback provider" provider-index))
        (if-let [result (retry-provider-with-circuit-breaker
                         provider request max-retries delay-ms
                         (fn [p r] (.chat ^ChatModel p r))
                         breaker cb-config)]
          result
          (recur (rest remaining-providers)
                 (rest remaining-breakers)
                 (conj providers-tried provider)
                 (inc provider-index))))

      ;; All providers failed or were skipped
      (do
        (log/error "All" (count providers) "providers failed or unavailable")
        (throw (ex-info "All providers failed or unavailable"
                        {:providers-count (count providers)
                         :providers-tried (count providers-tried)}))))))

(defn create-resilient-model
  "Creates a ChatModel with automatic failover between providers.
   Config: :primary, :fallbacks, :max-retries, :retry-delay-ms, :circuit-breaker?"
  [{:keys [primary fallbacks max-retries retry-delay-ms
           circuit-breaker? failure-threshold success-threshold timeout-ms]
    :or {fallbacks []
         max-retries const/default-max-retries
         retry-delay-ms const/default-retry-delay-ms
         circuit-breaker? false
         failure-threshold const/default-failure-threshold
         success-threshold const/default-success-threshold
         timeout-ms const/default-circuit-breaker-timeout-ms}}]

  {:pre [(some? primary)
         (>= max-retries 0)
         (> retry-delay-ms 0)
         (> failure-threshold 0)
         (> success-threshold 0)
         (> timeout-ms 0)]}

  (let [all-providers (cons primary fallbacks)]

    (if circuit-breaker?
      ;; Phase 2: With circuit breaker
      (let [circuit-breakers (vec (repeatedly (count all-providers)
                                              create-circuit-breaker-state))
            cb-config {:failure-threshold failure-threshold
                       :success-threshold success-threshold
                       :timeout-ms timeout-ms}]
        (reify ChatModel
          ;; Simple string chat
          (^String chat [_ ^String message]
            (try-providers-with-circuit-breaker all-providers message
                                                max-retries retry-delay-ms
                                                circuit-breakers cb-config))

          ;; ChatRequest chat (for advanced features like tools, JSON mode)
          (^ChatResponse chat [_ ^ChatRequest request]
            (try-providers-with-circuit-breaker-request all-providers request
                                                        max-retries retry-delay-ms
                                                        circuit-breakers cb-config))))

      ;; Phase 1: Without circuit breaker (backward compatible)
      (reify ChatModel
        ;; Simple string chat
        (^String chat [_ ^String message]
          (try-providers-with-retry all-providers message
                                    max-retries retry-delay-ms))

        ;; ChatRequest chat (for advanced features like tools, JSON mode)
        (^ChatResponse chat [_ ^ChatRequest request]
          (try-providers-with-retry-request all-providers request
                                            max-retries retry-delay-ms))))))

