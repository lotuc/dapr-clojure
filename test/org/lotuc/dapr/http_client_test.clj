(ns org.lotuc.dapr.http-client-test
  (:use clojure.test)
  (:require
   [org.lotuc.dapr.http-client :as sut]))

(def state-store "redis-state-store")

(deftest state-test
  (testing "Dapr state tests"
    (let [s0 (str (random-uuid))
          s1 (str (random-uuid))]
      (sut/state-delete state-store s0)
      (sut/state-delete state-store s1)
      (is (nil? (sut/state-get state-store s0)))
      (is (nil? (sut/state-get state-store s1)))

      (testing "State saves & etag updates"
        (let [_ (sut/state-save
                 state-store [{:key s0 :value "hello"}])
              r0 (sut/state-get state-store s0)
              _ (is (= "hello" (:value r0)))
              _ (is (some? (:etag r0)))

              ;; saves with no etag given
              _ (sut/state-save
                 state-store [{:key s0 :value "world"}])
              r1 (sut/state-get state-store s0)
              _ (is (= "world" (:value r1)))
              _ (is (some? (:etag r1)))

              ;; saves on etag match
              _ (sut/state-save
                 state-store [{:key s0 :value "hello world"
                               :etag (:etag r1)}])
              r2 (sut/state-get state-store s0)
              _ (is (= "hello world" (:value r2)))
              _ (is (some? (:etag r2)))]
          (is (not= (:etag r0) (:etag r1)))
          (is (not= (:etag r1) (:etag r2)))))

      (testing "State won't save on etag mismatch"
        (let [{:keys [etag]} (sut/state-get state-store s0)
              random-etag (str (random-uuid))]
          (is (not= etag random-etag))
          (try
            (sut/state-save
             state-store [{:key s0 :value "world"
                           :etag random-etag}])
            (catch Exception e
              (is (re-matches #".*invalid etag value.*" (ex-message e)))
              (let [err-data (ex-data e)]
                (is (= (:error-code err-data) "ERR_STATE_SAVE")))))))

      (sut/state-delete state-store s0))))
