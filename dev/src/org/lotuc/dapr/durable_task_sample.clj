(ns org.lotuc.dapr.durable-task-sample
  (:import
   (com.microsoft.durabletask
    TaskActivity
    TaskOrchestrationFactory
    NewOrchestrationInstanceOptions
    DurableTaskGrpcWorkerBuilder
    TaskActivityFactory
    TaskOrchestration
    DurableTaskGrpcClientBuilder)
   (java.time
    Duration))
  (:require
   [clojure.string :as s]))

(defn make-activity-chaining-worker
  "https://github.com/microsoft/durabletask-java/blob/8fd73b2a0f56d265709742c5ce280723abb119a3/samples/src/main/java/io/durabletask/samples/ChainingPattern.java"
  []
  (let [builder (DurableTaskGrpcWorkerBuilder.)]
    (.addOrchestration
     builder
     (reify TaskOrchestrationFactory
       (getName [_] "ActivityChaining")
       (create [_]
         (reify TaskOrchestration
           (run [_ ctx]
             (let [input (.getInput ctx String)
                   x (.await (.callActivity ctx "reverse" input String))
                   y (.await (.callActivity ctx "capitalize" x String))
                   z (.await (.callActivity ctx "replaceWhitespace" y String))]
               (.complete ctx z)))))))

    (.addActivity
     builder
     (reify TaskActivityFactory
       (getName [_] "reverse")
       (create [_]
         (reify TaskActivity
           (run [_ ctx]
             (-> (.getInput ctx String)
                 StringBuilder.
                 (. reverse)
                 (. toString)))))))

    (.addActivity
     builder
     (reify TaskActivityFactory
       (getName [_] "capitalize")
       (create [_]
         (reify TaskActivity
           (run [_ ctx]
             (-> (.getInput ctx String)
                 (. toUpperCase)))))))

    (.addActivity
     builder
     (reify TaskActivityFactory
       (getName [_] "replaceWhitespace")
       (create [_]
         (reify TaskActivity
           (run [_ ctx]
             (-> (.getInput ctx String)
                 (s/replace #"\s+" "-")))))))

    (.build builder)))

(defn call-and-get []
  (let [client (-> (DurableTaskGrpcClientBuilder.)
                   (. (port 4001))
                   (. (build)))
        instance-id (.scheduleNewOrchestrationInstance
                     client
                     "ActivityChaining"
                     (-> (NewOrchestrationInstanceOptions.)
                         (. (setInput "Hello, world!"))))
        completed-instance (.waitForInstanceCompletion
                            client
                            instance-id (Duration/ofSeconds 30) true)]
    (println "> res" completed-instance)
    (.readOutputAs completed-instance String)))

(comment
  ;; Run durable task locally first:
  ;; https://github.com/microsoft/durabletask-go/tree/main#running-locally

  (def server (make-activity-chaining-worker))
  (.start server)

  ;; https://github.com/microsoft/durabletask-java/blob/8fd73b2a0f56d265709742c5ce280723abb119a3/client/src/main/java/com/microsoft/durabletask/DurableTaskGrpcWorker.java#L122
  ;; This will not stop the server correctly
  (.stop server)

  (call-and-get))
