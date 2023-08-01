(ns org.lotuc.dapr.daprd
  (:require
   [babashka.process :as p]
   [clojure.java.io :as io]
   [clojure.string :as s]
   [clojure.tools.logging :as log])
  (:import
   (java.io File)))

(defmacro ^:private parse-daprd-opts []
  (let [f (fn [[opts [opt-name opt-val-type opt-help] :as r] line]
            (if (s/starts-with? line "-")
              ;; collect previous option
              [(cond-> opts
                 opt-name
                 (assoc opt-name {:val-hint opt-val-type :help opt-help}))
               ;; start processing new option
               (let [[p0 p1] (s/split line #"\s+")]
                 [(keyword (subs p0 1)) p1 nil])]

              ;; recording help text for current option
              (cond-> r
                opt-name
                (update-in [1 2]
                           (fn [opt-help]
                             (if opt-help (str opt-help "\n" line) line))))))]
    `(->> (str (slurp (io/resource "daprd_help.txt"))
               "\n-sentinel-option")
          s/split-lines
          (map s/trim)
          (filter seq)
          (reduce ~f [{} [nil nil nil]])
          first)))

(def daprd-opts (parse-daprd-opts))

(defn- make-daprd-args
  [opts]
  (letfn [(check-opt [[k v]]
            (when-let [{:keys [val-hint help]} (daprd-opts k)]
              (if (nil? val-hint)
                (when (boolean v) (str "-" (name k)))
                (do
                  (cond
                    (= val-hint "int")
                    (when (not (parse-long v))
                      (throw (Exception. (str "should be int: " help))))

                    (#{"string" "value"} val-hint)
                    (when (or (and (string? v) (empty? v)) (nil? v))
                      (throw (Exception. (str "should not be empty: " help))))

                    :else
                    (log/warnf "unkown value type, skip checking: %s" val-hint))

                  [(str "-" (name k)) (str v)]))))]
    (->> opts
         (map check-opt)
         (filter some?)
         flatten
         (into []))))

(defn daprd
  [{:keys [daprd app-id app-log-dir dry-run?]
    :as opts}]
  (when-not app-id
    (throw (Exception. "app-id is not given")))
  (let [args (make-daprd-args opts)
        daprd (or daprd
                  (let [f (io/file (System/getProperty "user.home")
                                   ".dapr/bin/daprd")
                        r (if (.exists f) (str f) "daprd")]
                    (log/infof "daprd bin defaults to be: %s" r)
                    r))
        app-log-file (if app-log-dir
                       (io/file app-log-dir (str app-id ".log"))
                       (File/createTempFile app-id ".log"))
        process-opts {:out :write :out-file app-log-file}]
    (.mkdirs (.getParentFile app-log-file))
    (log/infof "daprd app %s log file: %s" app-id app-log-file)
    (if dry-run?
      (->> args (into [daprd]))
      (apply p/process process-opts daprd args))))

(comment
  (daprd {:dry-run? true
          :app-log-dir "/tmp/app-logs"
          :app-id "app1"
          :dapr-grpc-port "3501"
          :metrics-port "9091"
          :enable-metrics false})

  (def r (daprd {:app-log-dir "/tmp/app-logs"
                 :app-id "app1"
                 :dapr-grpc-port "3501"
                 :metrics-port "9091"
                 :enable-metrics false}))

  (p/destroy-tree r))
