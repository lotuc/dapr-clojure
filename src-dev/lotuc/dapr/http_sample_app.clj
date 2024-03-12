(ns lotuc.dapr.http-sample-app
  (:require
   [babashka.process :as p]
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [clojure.tools.logging :as log]
   [lotuc.dapr.daprd :as daprd]
   [lotuc.dapr.http-app :refer [start-dapr-app]]))

(def topic-raw-reqs (atom []))
(def topic-not-raw-reqs (atom []))

(defn start-app [port]
  (let [dapr-config {:entities ["type0" "type1"]
                     :actorIdleTimeout "1h"
                     :actorScanInterval "30s"
                     :drainOngoingCallTimeout "30s"
                     :drainRebalancedActors true
                     :reentrancy {:enabled true :maxStackDepth 32}
                     :entitiesConfig
                     [{:entities ["type0"]
                       :actorIdleTimeout "1m"
                       :drainOngoingCallTimeout "10s"
                       :reentrancy {:enabled false}}]}
        subscriptions [{:pubsubname "redis-pubsub"
                        :topic "topic-raw"
                        :route "/topic-raw"
                        :metadata {:rawPayload "true"}}
                       {:pubsubname "redis-pubsub"
                        :topic "topic-not-raw"
                        :route "/topic-not-raw"
                        :metadata {:rawPayload "false"}}]
        topic->handler {"topic-raw"
                        (fn [req]
                          (swap! topic-raw-reqs conj req)
                          {:status 200})
                        "topic-not-raw"
                        (fn [req]
                          (swap! topic-not-raw-reqs conj req)
                          {:status 200})}
        binding-name->handler {"mqtt-binding"
                               (fn [{:keys [body]}]
                                 (println "mqtt-binding:" (slurp body))
                                 {:status 200})}
        service-routes [["/add"
                         {:post
                          (fn [{:keys [body]}]
                            (let [{:keys [arg1 arg2]}
                                  (json/parse-string (slurp body) keyword)]
                              {:headers {"Content-Type" "application/json"}
                               :body (json/generate-string (+ arg1 arg2))
                               :status 200}))}]]
        config {:dapr-config dapr-config
                :subscriptions subscriptions
                :topic->handler topic->handler
                :binding-name->handler binding-name->handler
                :service-routes service-routes}]
    (start-dapr-app {:app-config config :port port})))

(declare app-port app-id)
(defonce app-server (atom nil))
(defonce app-daprd-process (atom nil))

(defn reset-app-server [& {:keys [start]}]
  (->> (fn [{:keys [stop!]}]
         (when stop!
           (log/infof "[%s] stopping app server" app-id)
           (stop!))
         (when start
           (log/info "[%s] starting app server" app-id)
           (start-app app-port)))
       (swap! app-server)))

(defn reset-app-daprd [& {:keys [start]}]
  (->> (fn [v]
         (when v
           (log/infof "[%s] stopping daprd server" app-id)
           (p/destroy-tree v) @v)
         (when start
           (log/infof "[%s] starting daprd server" app-id)
           (daprd/daprd
            {:app-log-dir "/tmp/app-logs"
             :app-id app-id
             :dapr-http-port 3500
             :resources-path (.getAbsolutePath
                              (io/file "doc/http-client-components"))
             :app-port (str app-port)})))
       (swap! app-daprd-process)))

(def app-id "app-http-sample")
(def app-port 9393)

(defn restart-app []
  (reset-app-server :start true)
  (reset-app-daprd :start true))

(defn stop-app []
  (reset-app-server)
  (reset-app-daprd))

(comment
  (restart-app)
  (stop-app)
  @app-server
  @app-daprd-process)

(comment
  (:data (:body (@topic-raw-reqs 0)))
  (:body (@topic-not-raw-reqs 0)))
