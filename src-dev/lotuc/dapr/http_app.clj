(ns lotuc.dapr.http-app
  (:require
   [cheshire.core :as json]
   [clojure.tools.logging :as log]
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
                                            decode-base64)))
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
    (try
      (if-let [handler (let [r (get data request-method)]
                         (if (fn? r) r (:handler r)))]
        (handler (assoc req :path-params path-params))
        (do (println "unkown request" request-method uri)
            {:status 404 :uri uri}))
      (catch Exception e
        (log/infof e "error handling: %s %s - %s"
                   request-method uri (slurp (:body req)))
        (throw e)))))

(defn start-dapr-app
  [{:keys [port app-config] :as opts}]
  (let [router (-> app-config make-routes r/router)
        handler (partial handle router)
        server (-> opts
                   (dissoc :app-config)
                   (assoc :legacy-return-value? false)
                   (as-> $ (hk-server/run-server handler $)))]
    {:server server
     :stop! (fn [] (hk-server/server-stop! server))}))
