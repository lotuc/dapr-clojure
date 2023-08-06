(ns org.lotuc.dapr.http-client
  (:require
   [org.lotuc.dapr.internal.http-client-v1 :as hc]
   [org.lotuc.dapr.internal.http-client-v1-resp :as hc-resp]))

(defn invoke
  [app-id method args {:keys [http-method] :as opts}]
  (->> @(hc/invoke app-id method args opts)
       (hc-resp/handle-resp :invoke)))

(comment
  (invoke "app-http-sample" "add"
          {:arg1 4 :arg2 2}
          {:http-method :post}))

(defn state-save [state-store states]
  (->> @(hc/state-save state-store states)
       (hc-resp/handle-resp :state-save)))

(defn state-get
  [state-store k & {:keys [consistency metadata] :as opts}]
  (->> @(hc/state-get state-store k opts)
       (hc-resp/handle-resp :state-get)))

(defn state-get-bulk
  [state-store ks & {:keys [metadata] :as opts}]
  (->> @(hc/state-get-bulk state-store ks opts)
       (hc-resp/handle-resp :state-get-bulk)))

(defn state-delete
  [state-store k & {:keys [concurrency consistency etag] :as opts}]
  (->> @(hc/state-delete state-store k opts)
       (hc-resp/handle-resp :state-delete)))

(defn state-transaction
  [state-store {:keys [operations metadata] :as op}
   & {:keys [http-method metadata] :as opts}]
  (->> @(hc/state-transaction state-store op opts)
       (hc-resp/handle-resp :state-transaction)))

(comment
  (state-save "redis-state-store" [{:key "abc" :value [1 2]}])
  (state-get "redis-state-store" "abc")
  (state-get-bulk "redis-state-store" ["abc" "def"])
  (state-delete "redis-state-store" "abc" :etag "1")
  (state-transaction "redis-state-store"
                     {:operations
                      [{:operation "upsert"
                        :request {:key "abc" :value "fg" :etag "2"}}]}))
