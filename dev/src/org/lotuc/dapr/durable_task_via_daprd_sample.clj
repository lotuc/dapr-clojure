(ns org.lotuc.dapr.durable-task-via-daprd-sample
  (:require
   [babashka.process :as p]
   [clojure.java.io :as io]
   [clojure.tools.logging :as log]
   [org.lotuc.dapr.daprd :as daprd]
   [org.lotuc.dapr.pb-v1 :as pb])
  (:import
   (com.microsoft.durabletask
    DurableTaskGrpcClientBuilder
    DurableTaskGrpcWorkerBuilder
    TaskCanceledException
    TaskOrchestration
    TaskActivity
    TaskActivityFactory
    TaskOrchestrationFactory)
   (io.dapr.v1
    DaprGrpc)
   (io.grpc
    Grpc
    InsecureChannelCredentials)
   (java.time
    Duration)))

(declare app-id)

(defonce app-daprd-process (atom nil))

(defn make-channel
  ([target] (make-channel target (InsecureChannelCredentials/create)))
  ([target creds] (.build (Grpc/newChannelBuilder target creds))))

(defn make-client
  [channel]
  (DaprGrpc/newBlockingStub channel))

(defn make-demo-workflow
  "https://github.com/microsoft/durabletask-java/blob/8fd73b2a0f56d265709742c5ce280723abb119a3/samples/src/main/java/io/durabletask/samples/ChainingPattern.java"
  [& {:keys [port]}]
  (let [builder (cond-> (DurableTaskGrpcWorkerBuilder.)
                  port (. (port port)))]
    (.addOrchestration
     builder
     (reify TaskOrchestrationFactory
       (getName [_] "demo-workflow")
       (create [_]
         (reify TaskOrchestration
           (run [_ ctx]
             (log/info (str "Starting workflow: " (.getName ctx)))
             (log/info (str "Instance ID: " (.getInstanceId ctx)))
             (log/info "Waiting for event: 'myEvent'...")
             (try (-> (.waitForExternalEvent ctx "myEvent" (Duration/ofSeconds 10))
                      (. await))
                  (log/info "Received!")
                  (catch TaskCanceledException e
                    (log/warnf "Timeout: %s" (.getMessage e))))

             (log/info "call greet")
             (log/infof "greet response: %s"
                        (.await (.callActivity ctx "greet" "42" String)))

             (-> ctx (. (complete "finished"))))))))

    (.addActivity
     builder
     (reify TaskActivityFactory
       (getName [_] "greet")
       (create [_]
         (reify TaskActivity
           (run [_ ctx]
             (->> (.getInput ctx String) (str "hello, ")))))))
    (.build builder)))

(declare dapr-grpc-port)

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
             ;; Notice the internal actor will start when palcment set, and the
             ;; workflow engine needs actor system to be enabled.
             :placement-host-address "localhost:50005"
             :dapr-grpc-port (str dapr-grpc-port)
             :metrics-port "9092"
             :resources-path (.getAbsolutePath
                              (io/file "doc/http-client-components"))
             :enable-metrics false})))
       (swap! app-daprd-process)))

(def app-id "app-durable-task-sample")
(def dapr-grpc-port 3502)

(comment
  ;; step 1: start/restarts the daprd
  (reset-app-daprd :start true)
  ;; stops the daprd
  (reset-app-daprd)
  @app-daprd-process

  ;; step 2: create & start workflow handling server - connects to daprd
  (def server (make-demo-workflow {:port dapr-grpc-port}))
  (.start server)

  (.stop server)

  ;; step 3: create a duraletask client - connects to daprd
  (def durable-task-client (-> (DurableTaskGrpcClientBuilder.)
                               (. (port dapr-grpc-port))
                               (. (build))))

  ;; step 4: create workflow, raise events, wait for results...
  (def instance-id (.scheduleNewOrchestrationInstance
                    durable-task-client "demo-workflow"))
  (.raiseEvent durable-task-client instance-id "myEvent")
  (.waitForInstanceCompletion
   durable-task-client instance-id (Duration/ofSeconds 30) true)

  ;; TODO: step 5: try create workflow using dapr grpc client
  (def client-channel (make-channel (str "localhost:" dapr-grpc-port)))
  (def dapr-grpc-client (make-client client-channel))
  (.shutdownNow client-channel)

  (pb/make-StartWorkflowRequest))
