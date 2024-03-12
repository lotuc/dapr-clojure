(ns lotuc.dapr.grpc-sample-app
  (:require
   [babashka.process :as p]
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [clojure.tools.logging :as log]
   [lotuc.dapr.daprd :as daprd]
   [lotuc.dapr.pb-v1 :as pb])
  (:import
   [com.google.protobuf
    ByteString]
   [io.dapr.v1
    DaprGrpc]
   [io.dapr.v1
    AppCallbackGrpc$AppCallbackImplBase]
   [io.grpc Grpc InsecureChannelCredentials ServerBuilder]))

(defn make-say-hello-world-service []
  (proxy [AppCallbackGrpc$AppCallbackImplBase] []
    (onInvoke [request response-observer]
      (try
        (when (= "say" (.getMethod request))
          (-> (pb/make-InvokeResponse)
              (assoc :data (pb/make-protobuf-Any
                            {:value (ByteString/copyFromUtf8 "hello world")}))
              pb/proto-map->proto
              (as-> $ (.onNext response-observer $))))

        (finally (.onCompleted response-observer))))))

(defn make-channel
  ([target] (make-channel target (InsecureChannelCredentials/create)))
  ([target creds] (.build (Grpc/newChannelBuilder target creds))))

(defn make-client
  [channel]
  (DaprGrpc/newBlockingStub channel))

(defn make-server
  [{:keys [port services]}]
  (let [builder (ServerBuilder/forPort port)]
    (when services
      (reduce (fn [b service] (.addService b service)) builder services))
    (.build builder)))

(declare app-port app-id)

(defonce app-server (atom nil))
(defonce app-daprd-process (atom nil))

(defn reset-app-server [& {:keys [start]}]
  (->> (fn [v]
         (when v
           (log/infof "[%s] stopping app server" app-id)
           (.shutdown v)
           (.awaitTermination v))

         (when start
           (log/infof "[%s] starting app server" app-id)
           (let [server (-> {:port app-port
                             :services [(make-say-hello-world-service)]}
                            make-server)]
             (.start server)
             server)))
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
             :app-protocol "grpc"
             :dapr-listen-addresses "localhost"
             :dapr-grpc-port "3501"
             :metrics-port "9091"
             :resources-path (.getAbsolutePath
                              (io/file "doc/http-client-components"))
             :enable-metrics false
             :app-port (str app-port)})))
       (swap! app-daprd-process)))

(def app-port 9394)
(def app-id "app-grpc-sample")

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
  (def client-channel (make-channel "localhost:3501"))
  (def client (make-client client-channel))
  (.shutdownNow client-channel)

  (->> {:store-name "redis-state-store"
        :states [(pb/make-StateItem
                  {:key "mykey"
                   :value (ByteString/copyFromUtf8 "Hello world")})]}
       pb/make-SaveStateRequest
       pb/proto-map->proto
       (.saveState client)
       pb/proto->proto-map)

  (->> {:store-name "redis-state-store"
        :key "mykey"}
       pb/make-GetStateRequest
       pb/proto-map->proto
       (.getState client)
       pb/proto->proto-map)

  ;; this service is provided by http-sample-app
  (let [msg (pb/make-InvokeRequest
             {:method "add"
              :data (pb/make-protobuf-Any
                     {:value (ByteString/copyFromUtf8
                              (json/generate-string {:arg1 4 :arg2 2}))})
              :http-extension (pb/make-HTTPExtension {:verb :post})})]
    (->> {:id "app-http-sample" :message msg}
         pb/make-InvokeServiceRequest
         pb/proto-map->proto
         (.invokeService client)
         pb/proto->proto-map))

  ;; callin service implemented by itself
  (let [msg (pb/make-InvokeRequest
             {:method "say"
              :data (pb/make-protobuf-Any
                     {:value (ByteString/copyFromUtf8
                              (json/generate-string {:arg1 4 :arg2 2}))})
              :http-extension (pb/make-HTTPExtension {:verb :post})})]
    (->>  {:id app-id :message msg}
          pb/make-InvokeServiceRequest
          pb/proto-map->proto
          (.invokeService client)
          pb/proto->proto-map)))
