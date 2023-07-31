(ns org.lotuc.dapr.internal.http-client-v1
  "Checkout https://docs.dapr.io/reference/api/ for Dapr API refenreces."
  (:require
   [cheshire.core :as json]
   [clojure.set :as set]
   [org.httpkit.client :as hk-client]))

;; dapr sidecar http endpoint
(def ^:dynamic *endpoint* "http://localhost:3500")

;; org.httpkit.client/request options.
(def ^:dynamic *request-options* {})

(defn- make-url
  [path-fmt & args]
  (apply format (str *endpoint* path-fmt) (map hk-client/url-encode args)))

(defn- make-request [opts]
  (hk-client/request (merge *request-options* opts)))

(defn- make-metadata-query-params [metadata]
  (->> metadata
       (map (fn [[k v]] [(str "metadata." k) v]))
       (into {})))

(defn invoke
  "PATCH/POST/GET/PUT/DELETE."
  [app-id method-name args {:keys [http-method]}]
  (-> {:url (make-url "/v1.0/invoke/%s/method/%s" app-id method-name)
       :method http-method
       :headers {"content-type" "application/json"}
       :body (when args (json/generate-string args))}
      make-request))

(comment
  @(invoke "app0" "add" {:arg1 4 :arg2 2} {:http-method :post}))

(defn state-get
  [state-store k & {:keys [consistency metadata]}]
  (cond-> {:url (make-url "/v1.0/state/%s/%s" state-store k)}
    (and metadata (seq metadata))
    (assoc :query-params (make-metadata-query-params metadata))
    consistency
    (assoc-in [:query-params :consistency] consistency)
    true make-request))

(comment
  @(state-get "redis-state-store" "a" :consistency "strong"))

(defn state-get-bulk
  [state-store ks & {:keys [metadata]}]
  (cond-> {:url (make-url "/v1.0/state/%s/bulk" state-store)
           :method :post
           :body (json/generate-string {:keys ks})}
    (and metadata (seq metadata))
    (assoc :query-params (make-metadata-query-params metadata))
    true make-request))

(comment
  @(state-get-bulk "redis-state-store" ["a" "b"]))

(defn state-query
  [state-store body & {:keys [metadata]}]
  (cond-> {:url (make-url "/v1.0-alpha1/state/%s/query" state-store)
           :method :post
           :body body}
    (and metadata (seq metadata))
    (assoc :query-params (make-metadata-query-params metadata))
    true make-request))

(comment
  @(state-query "redis-state-store"
                (json/generate-string {})
                :metadata {"contentType" "application/json"}))

(defn state-save
  [state-store states]
  (-> {:url (make-url "/v1.0/state/%s" state-store)
       :method :post
       :headers {"content-type" "application/json"}
       :body (json/generate-string states)}
      make-request))

(comment
  @(state-save "redis-state-store"
               [{:key "a" :value "a-val"}
                {:key "b" :value "b-val"}]))

(defn state-delete
  [state-store k & {:keys [concurrency consistency etag]}]
  (cond-> {:url (make-url "/v1.0/state/%s/%s" state-store k)
           :method :delete
           :headers {"content-type" "application/json"}}
    concurrency (assoc-in [:query-params :concurrency] concurrency)
    consistency (assoc-in [:query-params :consistency] consistency)
    etag (assoc-in [:headers "If-Match"] etag)
    true make-request))

(comment
  @(state-delete "redis-state-store" "a"))

(defn state-transaction
  "post/put."
  [state-store {:keys [operations metadata]}
   & {:keys [http-method]
      request-metadata :metadata
      :or {http-method :post}}]
  (cond-> {:url (make-url "/v1.0/state/%s/transaction" state-store)
           :method http-method
           :headers {"content-type" "application/json"}
           :body (json/generate-string {:operations operations :metadata metadata})}
    (and request-metadata (seq request-metadata))
    (assoc :query-params (make-metadata-query-params request-metadata))
    true make-request))

(comment
  @(state-transaction "redis-state-store"
                      {:operations [{:operation "upsert"
                                     :request {:key "abc" :value "vv"}}
                                    {:operation "delete"
                                     :request {:key "mm"}}]}))

(defn publish-bulk [pubsub-name topic data & {:keys [metadata]}]
  (cond-> {:url (make-url "/v1.0-alpha1/publish/bulk/%s/%s" pubsub-name topic)
           :method :post
           :headers {"content-type" "application/json"}
           :body (json/generate-string data)}
    (and metadata (seq metadata))
    (assoc :query-params (make-metadata-query-params metadata))
    true make-request))

(comment
  @(publish-bulk "redis-pubsub" "topic-raw"
                 [{:entryId "1"
                   :event "first text message"
                   :contentType "text/plain"}
                  {:entryId "2"
                   :event {"messge" "second JSON message"}
                   :contentType "application/json"}]
                 {:metadata
                  {"rawPayload" "true"
                   "maxBulkPubBytes" 10}}))

(defn actor-call
  "post, get, put or delete."
  [actor-type actor-id method params & {:keys [http-method]}]
  (cond-> {:url (make-url "/v1.0/actors/%s/%s/method/%s"
                          actor-type actor-id method)
           :method http-method}
    (not= http-method :get)
    (assoc :headers {"content-type" "application/json"}
           :body (json/generate-string params))

    true make-request))

(comment
  @(actor-call "type0" "42" "echo" "hello world"))

(defn actor-state-set
  "post or put."
  [actor-type actor-id state-actions
   & {:keys [http-method]
      :or {http-method :post}}]
  (-> {:url (make-url "/v1.0/actors/%s/%s/state" actor-type actor-id)
       :method http-method
       :headers {"content-type" "application/json"}
       :body (json/generate-string state-actions)}
      make-request))

(comment
  @(actor-state-set "type0" "42" [{:operation "upsert"
                                   :request {:key "state0"
                                             :value "state0-val"}}]))

(defn actor-state-get [actor-type actor-id k]
  (-> {:url (make-url "/v1.0/actors/%s/%s/state/%s"
                      actor-type actor-id k)}
      make-request))

(comment
  @(actor-state-get "type0" "42" "state0"))

(defn actor-reminder-create
  "post or put."
  [actor-type actor-id reminder-name
   {:keys [due-time period] :as reminder}
   & {:keys [http-method]
      :or {http-method :post}}]
  (-> {:url (make-url "/v1.0/actors/%s/%s/reminders/%s"
                      actor-type actor-id reminder-name)
       :method http-method
       :headers {"content-type" "application/json"}
       :body (json/generate-string
              (-> reminder (assoc :dueTime due-time) (dissoc :due-time)))}
      make-request))

(comment
  @(actor-reminder-create "type0" "42" "reminder-42"
                          {:due-time "0h0m3s0ms" :period "0h0m7s0ms"}))

(defn actor-reminder-get
  [actor-type actor-id reminder-name]
  (-> {:url (make-url "/v1.0/actors/%s/%s/reminders/%s"
                      actor-type actor-id reminder-name)
       :method :get
       :headers {"content-type" "application/json"}}
      make-request))

(comment
  @(actor-reminder-get "type0" "42" "reminder-42"))

(defn actor-reminder-delete
  [actor-type actor-id reminder-name]
  (-> {:url (make-url "/v1.0/actors/%s/%s/reminders/%s"
                      actor-type actor-id reminder-name)
       :method :delete
       :headers {"content-type" "application/json"}}
      make-request))

(comment
  @(actor-reminder-delete "type0" "42" "reminder-42"))

(defn actor-timer-create
  "post or put."
  [actor-type actor-id timer-name
   {:keys [due-time period] :as timer}
   & {:keys [http-method]
      :or {http-method :post}}]
  (-> {:url (make-url "/v1.0/actors/%s/%s/timers/%s"
                      actor-type actor-id timer-name)
       :method http-method
       :headers {"content-type" "application/json"}
       :body (json/generate-string
              (-> timer (assoc :dueTime due-time) (dissoc :due-time)))}
      make-request))

(comment
  @(actor-timer-create "type0" "42" "reminder-42"
                       {:due-time "0h0m3s0ms" :period "0h0m7s0ms"}))

(defn actor-timer-delete
  [actor-type actor-id timer-name]
  (-> {:url (make-url "/v1.0/actors/%s/%s/timers/%s"
                      actor-type actor-id timer-name)
       :method :delete
       :headers {"content-type" "application/json"}}
      make-request))

(comment
  @(actor-timer-delete "type0" "42" "reminder-42"))

(defn invoke-binding
  "post or put."
  [binding-name {:keys [data metadata operation]}
   & {:keys [http-method]
      :or {http-method :post}}]
  (-> {:url (make-url "/v1.0/bindings/%s" binding-name)
       :method http-method
       :headers {"content-type" "application/json"}
       :body (json/generate-string {:data data
                                    :metadata metadata
                                    :operation operation})}
      make-request))

(comment
  @(invoke-binding "mqtt-binding"
                   {:data "hello world"
                    :metadata {:retain "true"
                               :topic "/abc/efg"}
                    :operation "create"}))

(defn secret-get
  [secret-store-name secret-name & {:keys [metadata]}]
  (cond-> {:url (str *endpoint* "/v1.0/secrets/"
                     secret-store-name "/" secret-name)
           :method :get}
    (and metadata (seq metadata))
    (assoc :query-params (make-metadata-query-params metadata))

    true make-request))

(comment
  @(secret-get "localfile-secret-store" "secret0"))

(defn secret-get-bulk
  [secret-store-name & {:keys [metadata]}]
  (cond-> {:url (make-url "/v1.0/secrets/%s/bulk" secret-store-name)
           :method :get}
    (and metadata (seq metadata))
    (assoc :query-params (make-metadata-query-params metadata))

    true make-request))

(comment
  @(secret-get-bulk "localfile-secret-store"))

(defn configuration-get
  [store-name & {:keys [keys]}]
  (cond-> {:url (make-url "/v1.0/configuration/%s" store-name)
           :method :get}
    (and keys (seq keys))
    (assoc :query-params {:key keys})

    true make-request))

(comment
  @(configuration-get "redis-configuration-store")
  @(configuration-get "redis-configuration-store" {:keys ["a"]}))

(defn configuration-subscribe
  [store-name & {:keys [keys]}]
  (cond-> {:url (make-url "/v1.0/configuration/%s/subscribe" store-name)
           :method :get}
    (and keys (seq keys))
    (assoc :query-params {:key keys})

    true make-request))

(comment
  @(configuration-subscribe "redis-configuration-store"))

(defn configuration-unsubscribe
  [store-name subscription-id]
  (-> {:url (make-url "/v1.0/configuration/%s/%s/unsubscribe"
                      store-name subscription-id)
       :method :get}
      make-request))

(comment
  @(configuration-unsubscribe "redis-configuration-store"
                              "4cbcbeae-9d60-49a8-8269-965dbef81af2"))

(defn lock
  [store-name {:keys [resource-id lock-owner expiry-in-seconds] :as opts}]
  (-> {:url (make-url "/v1.0-alpha1/lock/%s" store-name)
       :method :post
       :body (->> {:resource-id :resourceId
                   :lock-owner :lockOwner
                   :expiry-in-seconds :expiryInSeconds}
                  (set/rename-keys opts)
                  json/generate-string)}
      make-request))

(comment
  @(lock "redis-lock-store" {:resource-id "a" :lock-owner "lotuc"
                             :expiry-in-seconds 30}))

(defn unlock
  [store-name {:keys [resource-id lock-owner] :as opts}]
  (-> {:url (make-url "/v1.0-alpha1/unlock/%s" store-name)
       :method :post
       :body (->> {:resource-id :resourceId
                   :lock-owner :lockOwner}
                  (set/rename-keys opts)
                  json/generate-string)}
      make-request))

(comment
  @(unlock "redis-lock-store" {:resource-id "a" :lock-owner "lotuc"}))

(defn workflow-start
  [workflow-component-name workflow-name & {:keys [instance-id]}]
  (cond-> {:url (make-url "/v1.0-alpha1/workflows/%s/%s/start"
                          workflow-component-name workflow-name)
           :method :post}
    instance-id (assoc :query-params {:instanceId instance-id})

    true make-request))

(comment
  @(workflow-start "workflow-comp" "workflow-1" {:instance-id "42"}))

(defn workflow-get
  [workflow-component-name instance-id]
  (-> {:url (make-url "/v1.0-alpha1/workflows/%s/%s"
                      workflow-component-name instance-id)
       :method :get}
      make-request))

(comment
  @(workflow-get "workflow-comp" "42"))

(defn workflow-raise-event
  [workflow-component-name instance-id event-name]
  (-> {:url (make-url "/v1.0-alpha1/workflows/%s/%s/raiseEvent/%s"
                      workflow-component-name instance-id event-name)
       :method :post}
      make-request))

(comment
  @(workflow-raise-event "workflow-comp" "42" "hello"))

(defn- workflow-run-op*
  [workflow-component-name instance-id op-name]
  (-> {:url (make-url "/v1.0-alpha1/workflows/%s/%s/%s"
                      workflow-component-name instance-id op-name)
       :method :post}
      make-request))

(defn workflow-terminate
  [workflow-component-name instance-id]
  (workflow-run-op* workflow-component-name instance-id "terminate"))

(defn workflow-pause
  [workflow-component-name instance-id]
  (workflow-run-op* workflow-component-name instance-id "pause"))

(defn workflow-resume
  [workflow-component-name instance-id]
  (workflow-run-op* workflow-component-name instance-id "resume"))

(defn workflow-purge
  [workflow-component-name instance-id]
  (workflow-run-op* workflow-component-name instance-id "purge"))

(comment
  @(workflow-terminate "workflow-comp" "42")
  @(workflow-pause "workflow-comp" "42")
  @(workflow-resume "workflow-comp" "42")
  @(workflow-purge "workflow-comp" "42"))

(defn healthz []
  (-> {:url (make-url "/v1.0/healthz")
       :method :get}
      make-request))

(comment
  @(healthz))

(defn metadata []
  (-> {:url (make-url "/v1.0/metadata")
       :method :get}
      make-request))

(comment
  @(metadata))

(defn metadata-add-label
  [attribute-name attribute-value]
  (-> {:url (str *endpoint* "/v1.0/metadata/" attribute-name)
       :headers {"content-type" "text/plain"}
       :body attribute-value
       :method :put}
      make-request))

(comment
  @(metadata-add-label "custom-key" "coustom-val"))
