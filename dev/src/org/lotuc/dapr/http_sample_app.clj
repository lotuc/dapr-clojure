(ns org.lotuc.dapr.http-sample-app
  (:require
   [cheshire.core :as json]
   [clojure.string :as s]
   [org.httpkit.server :as hk-server]))

(def topic-raw-reqs (atom []))
(def topic-not-raw-reqs (atom []))

(defn decode-base64 [to-decode]
  (String. (.decode (java.util.Base64/getDecoder) to-decode)))

;; dapr sidecar 启动 (若给定了 app-port) 之后会访问 APP
;; - /dapr/config
;; - /dapr/subscribe
(defn app [req]
  (let [uri (:uri req)]
    (cond
      (s/starts-with? uri "/actors")
      (case (:request-method req)
        :delete
        (let [[_ actor-type actor-id] (re-matches #"/actors/(.+)/(.+)" uri)]
          (println actor-type actor-id "delete")
          {:status 200
           :body "ok"})

        :put
        (let [[_ actor-type actor-id method-name]
              (re-matches #"/actors/(.+)/(.+)/method/(.+)" uri)

              [_ t n]
              (when method-name (re-matches #"(.+)/(.+)" method-name))]
          (cond
            (= t "remind") (println actor-type actor-id "remind" n)
            (= t "timer") (println actor-type actor-id "timer" n)
            :else (println actor-type actor-id "method" method-name))
          {:status 200 :body (slurp (:body req))})

        {:status 404 :body "illegal method"})

      ;; mqtt-binding is the name of binding.
      (= uri "/mqtt-binding")
      (case (:request-method req)
        :options
        ;; {:status 404 :body "do not bind to be"}
        {:status 200 :body "bound"}
        :post
        (do
          (println "recv..."
                   (get (:headers req) "topic")
                   (slurp (:body req)))
          {:status 200 :body "handled"})

        {:status 404 :body "illegal method"})

      (s/starts-with? uri "/configuration")
      (let [[_ store-name configuration-key]
            (re-matches #"/configuration/(.+)/(.+)" uri)]
        (println "recv configuration: " store-name configuration-key
                 (slurp (:body req)))
        {:status 200 :body ""})

      (= uri "/dapr/config")
      {:status 200
       :headers {"Content-Type" "application/json"}
       :body (json/generate-string
              {:entities ["type0" "type1"]
               :actorIdleTimeout "1h"
               :actorScanInterval "30s"
               :drainOngoingCallTimeout "30s"
               :drainRebalancedActors true
               :reentrancy {:enabled true :maxStackDepth 32}
               :entitiesConfig
               [{:entities ["type0"]
                 :actorIdleTimeout "1m"
                 :drainOngoingCallTimeout "10s"
                 :reentrancy {:enabled false}}]})}

      ;; actor.
      (= uri "/healthz")
      {:status 200 :body "ok"}

      (= uri "/add")
      {:status 200
       :headers {"Content-Type" "application/json"}
       :body (let [{:keys [arg1 arg2]} (json/parse-string
                                        (slurp (:body req)) keyword)]
               (json/generate-string (+ arg1 arg2)))}

      (= uri "/dapr/subscribe")
      {:status 200
       :headers {"Content-Type" "application/json"}
       :body (json/generate-string
              [{:pubsubname "pubsub"
                :topic "topic-raw"
                :route "/topic-raw"
                :metadata {:rawPayload "true"}}
               {:pubsubname "pubsub"
                :topic "topic-not-raw"
                :route "/topic-not-raw"
                :metadata {:rawPayload "false"}}])}

      :else
      (let [route-reqs {"/topic-raw" topic-raw-reqs
                        "/topic-not-raw" topic-not-raw-reqs}]
        (if-let [reqs (route-reqs uri)]
          (do (swap! reqs conj
                     (try (assoc req :body-slurp (slurp (:body req)))
                          (catch Exception _ req)))
              {:status 200
               :headers {"Content-Type" "application/json"}})
          (do (println "unkown request" uri)
              {:status 404}))))))

(defonce server (atom nil))

(defn restart-server []
  (swap! server
         (fn [v]
           (when v (v))
           (hk-server/run-server app {:port 9393}))))

(restart-server)
