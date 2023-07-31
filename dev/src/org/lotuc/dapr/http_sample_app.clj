(ns org.lotuc.dapr.http-sample-app
  (:require
   [cheshire.core :as json]
   [org.httpkit.server :as hk-server]
   [reitit.core :as r]))

(defn make-dapr-config-route
  "https://docs.dapr.io/reference/api/actors_api/#get-registered-actors"
  [dapr-config]
  ["/dapr/config"
   {:get (fn [_]
           {:status 200
            :headers {"Content-Type" "application/json"}
            :body (json/generate-string dapr-config)})}])

(defn decode-base64 [to-decode]
  (String. (.decode (java.util.Base64/getDecoder) to-decode)))

(defn make-subscribe-routes
  "https://docs.dapr.io/reference/api/pubsub_api/#optional-application-user-code-routes"
  [subscribes topic->handler]
  (->> subscribes
       (map (fn [{:keys [route topic metadata]}]
              (when-let [handler (topic->handler topic)]
                [route
                 {:post
                  (fn [{:keys [body] :as req}]
                    (let [body (-> body
                                   slurp
                                   (json/parse-string keyword))
                          req (assoc req :body body)]
                      (cond-> req
                        (= "true" (get metadata :rawPayload))
                        (update :body
                                #(assoc % :data
                                        (-> body
                                            :data_base64
                                            decode-base64
                                            (json/parse-string keyword))))
                        true handler)))}])))
       (filter some?)
       (into [["/dapr/subscribe"
               {:get (fn [_]
                       {:status 200
                        :headers {"Content-Type" "application/json"}
                        :body (json/generate-string subscribes)})}]])))

(defn make-binding-routes
  "https://docs.dapr.io/reference/api/bindings_api/#binding-endpoints"
  [binding-name->handler]
  (->> binding-name->handler
       (map (fn [[binding-name handler]]
              [(str "/" binding-name)
               {:options (fn [_] {:status 200})
                :post handler}]))
       (into [])))

(defn make-routes
  [{:keys [dapr-config
           subscriptions
           topic->handler
           binding-name->handler
           service-routes]}]
  (->> (concat (make-subscribe-routes subscriptions topic->handler)
               (make-binding-routes binding-name->handler)
               service-routes)
       (filter some?)
       (into [(make-dapr-config-route dapr-config)
              ["/configuration/:store-name/:configuration-key"
               {:post (fn [_] {:status 200})}]

              ["/healthz"
               {:get (fn [_] {:status 200})}]
              ["/actors/:actor-type/:actor-id"
               {:delete (fn [_] {:status 200})}]
              ["/actors/:actor-type/:actor-id/method/remind/:remind"
               {:put (fn [_] {:status 200})}]
              ["/actors/:actor-type/:actor-id/method/timer/:timer"
               {:put (fn [_] {:status 200})}]
              ["/actors/:actor-type/:actor-id/method/:method"
               {:put (fn [_] {:status 200})}]])))

(defn handle [router req]
  (let [{:keys [uri request-method]} req
        {:keys [data path-params]} (r/match-by-path router uri)]
    ;; (println "on" request-method uri)
    (if-let [handler (let [r (get data request-method)]
                       (if (fn? r) r (:handler r)))]
      (handler (assoc req :path-params path-params))
      (do (println "unkown request" uri)
          {:status 404 :uri uri}))))

(def topic-raw-reqs (atom []))
(def topic-not-raw-reqs (atom []))

(comment
  (:data (:body (@topic-raw-reqs 0)))
  (:body (@topic-not-raw-reqs 0)))

(def router
  (-> (make-routes
       {:dapr-config {:entities ["type0" "type1"]
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

        :subscriptions [{:pubsubname "redis-pubsub"
                         :topic "topic-raw"
                         :route "/topic-raw"
                         :metadata {:rawPayload "true"}}
                        {:pubsubname "redis-pubsub"
                         :topic "topic-not-raw"
                         :route "/topic-not-raw"
                         :metadata {:rawPayload "false"}}]

        :topic->handler {"topic-raw"
                         (fn [req]
                           (swap! topic-raw-reqs conj req)
                           {:status 200})
                         "topic-not-raw"
                         (fn [req]
                           (swap! topic-not-raw-reqs conj req)
                           {:status 200})}

        :binding-name->handler {"mqtt-binding"
                                (fn [{:keys [body]}]
                                  (println "mqtt-binding:" (slurp body))
                                  {:status 200})}

        :service-routes
        ["/add"
         {:post (fn [{:keys [body]}]
                  (let [{:keys [arg1 arg2]}
                        (json/parse-string (slurp body) keyword)]
                    {:status 200
                     :headers {"Content-Type" "application/json"}
                     :body (json/generate-string (+ arg1 arg2))}))}]})

      r/router))

(defonce server (atom nil))

(defn restart-server []
  (swap! server
         (fn [v]
           (when v (v))
           (hk-server/run-server
            (partial handle router)
            {:port 9393}))))

(restart-server)
