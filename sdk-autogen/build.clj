(ns build
  (:require
   [clojure.java.io :as io]
   [clojure.string :as s]
   [clojure.tools.build.api :as b]))

(def basis (b/create-basis {:project "deps.edn"}))
(def proto-src-dir "proto-src")
(def java-src-dir "java-src")
(def class-dir "target/classes")

(def proto-gen-grpc-java-plugin "proto-plugins/protoc-gen-grpc-java")

;; https://github.com/dapr/dapr/tree/release-1.11/dapr/proto

(defn clean []
  (println "\nClenaup...")
  (println "cleanup target directory")
  (b/delete {:path "target"})

  (println "cleanup java-src directory")
  (b/delete {:path "java-src"}))

(defn- run-cmd [cmd]
  (try
    (when-not (-> {:command-args cmd}
                  b/process :exit zero?)
      (throw (RuntimeException.)))
    (catch java.io.IOException _
      (throw (Exception. (str "error run cmd: " cmd))))))

(defn compile-protos []
  (.mkdirs (io/file java-src-dir))

  (let [release (or (System/getenv "SDK_AUTOGEN_DAPR_RELEASE") "release-1.11")
        proto-dir (io/file (io/file proto-src-dir) release)]
    (println "\nCompile proto files for release" release "...")

    (let [f (io/file proto-gen-grpc-java-plugin)]
      (when-not (.exists f)
        (println "proto-gen-grpc-java plugin does not exists")
        (println "  download and put it at: " (.getAbsolutePath f))
        (println "  check out the following link for details:\n"
                 "    https://github.com/grpc/grpc-java/tree/master/compiler")
        (System/exit 1)))

    (doseq [proto-file (->> proto-dir
                            file-seq
                            (map str)
                            (filter #(s/ends-with? % ".proto")))
            :let [cmd0 ["protoc"
                        "--java_out" java-src-dir
                        "--proto_path"  (str proto-dir)
                        proto-file]
                  cmd1 ["protoc"
                        "--plugin" (str "protoc-gen-grpc-java="
                                        proto-gen-grpc-java-plugin)
                        "--grpc-java_out" java-src-dir
                        "--proto_path"  (str proto-dir)
                        ;; "--java_out" java-src-dir
                        proto-file]]]
      (println " " cmd0)
      (run-cmd cmd0)
      (println " " cmd1)
      (run-cmd cmd1))))

(defn compile-java []
  (println "\nCompile java ...")
  (b/javac {:src-dirs [java-src-dir]
            :class-dir class-dir
            :basis basis}))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn prep-lib [_]
  (clean)
  (compile-protos)
  (compile-java))
