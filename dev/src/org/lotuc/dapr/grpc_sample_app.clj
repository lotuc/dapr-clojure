(ns org.lotuc.dapr.grpc-sample-app
  (:require
   [babashka.process :as p]
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [clojure.tools.logging :as log]
   [org.lotuc.dapr.daprd :as daprd]
   [org.lotuc.dapr.pb-v1 :as pb])
  (:import
   (com.google.protobuf
    ByteString)
   (io.dapr.v1
    DaprGrpc)
   (io.dapr.v1
    AppCallbackGrpc$AppCallbackImplBase)
   (io.grpc Grpc InsecureChannelCredentials ServerBuilder)))

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

(defn restart-app-server []
  (swap! app-server
         (fn [v]
           (when v
             (log/info "stopping app server")
             (.shutdown v)
             (..awaitTermination v))

           (log/info "starting app server")
           (let [server (-> {:port app-port
                             :services [(make-say-hello-world-service)]}
                            make-server)]
             (.start server)
             server))))

(defn restart-app-daprd []
  (swap! app-daprd-process
         (fn [v]
           (when v
             (log/infof "stopping %s daprd server" app-id)
             (p/destroy-tree v) @v)
           (log/infof "starting %s daprd server" app-id)
           (daprd/daprd
            {:app-log-dir "/tmp/app-logs"
             :app-id "app1"
             :app-protocol "grpc"
             :dapr-listen-addresses "localhost"
             :dapr-grpc-port "3501"
             :metrics-port "9091"
             :resources-path (.getAbsolutePath
                              (io/file "doc/http-client-components"))
             :enable-metrics false
             :app-port (str app-port)}))))

(def app-port 9394)
(def app-id "app1")

(defn reset-app []
  (restart-app-server)
  (restart-app-daprd))

(comment
  (reset-app)
  @app-server
  @app-daprd-process)

(comment
  (def client-channel (make-channel "localhost:3501"))
  (def client (make-client client-channel))
  (.shutdownNow client-channel)

  (->> (pb/make-SaveStateRequest
        {:store-name "redis-state-store"
         :states [(pb/make-StateItem
                   {:key "mykey"
                    :value (ByteString/copyFromUtf8 "Hello world")})]})
       pb/proto-map->proto
       (.saveState client)
       pb/proto->proto-map)

  (->> (pb/make-GetStateRequest {:store-name "redis-state-store"
                                 :key "mykey"})
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
    (->> (pb/make-InvokeServiceRequest {:id "app0" :message msg})
         pb/proto-map->proto
         (.invokeService client)
         pb/proto->proto-map))

  (let [msg (pb/make-InvokeRequest
             {:method "say"
              :data (pb/make-protobuf-Any
                     {:value (ByteString/copyFromUtf8
                              (json/generate-string {:arg1 4 :arg2 2}))})
              :http-extension (pb/make-HTTPExtension {:verb :post})})]
    (->> (pb/make-InvokeServiceRequest {:id "app1" :message msg})
         pb/proto-map->proto
         (.invokeService client)
         pb/proto->proto-map)))
