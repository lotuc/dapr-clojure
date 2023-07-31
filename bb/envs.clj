(ns envs
  (:require
   [babashka.process :as p]
   [clojure.java.io :as io]
   [clojure.set :as set]
   [clojure.string :as s]))

(defn- env-set [env-string]
  (->> env-string
       s/split-lines
       set))

(def ^:const envs-without-dapr "out/envs-without-dapr.txt")

(.mkdir (.getParentFile (io/file envs-without-dapr)))

(defn spit-envs []
  (println "spit envs to " envs-without-dapr)
  (->> (p/shell {:out :string} "env")
       :out
       (spit envs-without-dapr)))

(defn diff-envs-and-wait []
  (doseq [v (set/difference
             (-> (p/shell {:out :string} "env")
                 :out
                 env-set)
             (-> (slurp envs-without-dapr)
                 env-set))]
    (println v))

  (println "Press Ctrl-c to quit.")
  @(promise))
