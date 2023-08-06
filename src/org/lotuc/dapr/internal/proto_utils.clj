(ns org.lotuc.dapr.internal.proto-utils
  (:require
   [pronto.core :as p]
   [pronto.utils :as u]))

(defn ^:private def-maker-parser
  ([mapper clazz & {:keys [clazz->op-names]}]
   (let [simple-name (second (re-matches #".+\$(.+)$" (str clazz)))

         maker-name
         (or (get-in clazz->op-names [clazz :maker])
             (when simple-name (symbol (str "make-" simple-name)))
             (throw (Exception. (str "Can't defer maker-name: " clazz))))
         parser-name
         (or (get-in clazz->op-names [clazz :parser])
             (when simple-name (symbol (str "parse-" simple-name)))
             (throw (Exception. (str "Can't defer parser-name: " clazz))))]
     `((def ~maker-name
         (fn
           ([] (p/proto-map ~mapper ~clazz))
           ([m#] (p/clj-map->proto-map ~mapper ~clazz m#))))
       (def ~parser-name
         (fn [bytes#] (p/bytes->proto-map ~mapper ~clazz bytes#)))))))

(defmacro import-protos
  [mapper package-name classes]
  (let [builtin-clazz->op-names {'Any {:maker 'make-protobuf-Any
                                       :parser 'parse-protobuf-Any}
                                 'Empty {:maker 'make-protobuf-Empty
                                         :parser 'parse-protobuf-Empty}}
        builtin-defs (mapcat
                      #(def-maker-parser 'mapper %
                         {:clazz->op-names builtin-clazz->op-names})
                      ['Any 'Empty])

        defs (mapcat (partial def-maker-parser mapper) classes)
        proto->proto-map 'proto->proto-map]
    `(do (import (com.google.protobuf
                  ~@(keys builtin-clazz->op-names)))
         (import (~package-name
                  ~@classes))
         (p/defmapper ~mapper
           ~(into [] (concat classes (keys builtin-clazz->op-names)))
           :key-name-fn u/->kebab-case
           :enum-value-fn u/->kebab-case)
         (def ~proto->proto-map (fn [v#] (p/proto->proto-map ~mapper v#)))
         ~@builtin-defs
         ~@defs)))

(defmacro enum-keywords [clazz]
  `(->> (. ~clazz values)
        (map #(.name %))
        (map (comp keyword u/->kebab-case))
        set))
