(ns lotuc.dapr.internal.http-client-v1-resp
  (:require
   [muuntaja.core :as m]))

(defn decode-http-response [{:keys [headers body] :as r}]
  (if (or (nil? body) (empty? body))
    nil
    (-> r
        (assoc-in [:headers "Content-Type"] (:content-type headers))
        ;; https://github.com/metosin/muuntaja/issues/134
        (update :body #(str "[" % ",true]"))
        m/decode-response-body
        first)))

(defn- throw-error [status response & {:keys [decoded-body] :as opts}]
  (let [{:keys [errorCode message]} (if (contains? opts :decoded-body)
                                      decoded-body
                                      (decode-http-response response))
        error-message (if message message
                          (str "Unkown Dapr Error. HTTP status code: " status))
        error-data (cond-> {:response response}
                     errorCode (assoc :error-code errorCode))]
    (throw (ex-info error-message error-data))))

(defmulti handle-resp (fn [request-type _response] request-type))

(defmethod handle-resp :invoke [_ response]
  (let [{:keys [status]} response]
    (when-let [error-msg ({400 "Method name not given"
                           403 "Invocation forbidden by access control"
                           500 "Request failed"}
                          status)]
      (throw (ex-info error-msg {:response response})))
    response))

(defmethod handle-resp :state-get [_ response]
  (let [{:keys [status]} response]
    (when-let [error-msg ({400 "State store is missing or misconfigured"
                           500 "Get state failed"}
                          status)]
      (throw (ex-info error-msg {:response response})))
    (when-not (#{200 204} status)
      (throw-error status response))
    ;; status 404: Key is not found.
    (when-not (= status 204)
      {:value (decode-http-response response)
       :etag (get-in response [:headers :etag])})))

(defmethod handle-resp :state-get-bulk [_ response]
  (let [{:keys [status]} response]
    (when-not (= status 200)
      (throw-error status response))
    (decode-http-response response)))

(defmethod handle-resp :state-save [_ response]
  (let [{:keys [status]} response]
    (when-not (= status 204)
      (throw-error status response))))

(defmethod handle-resp :state-delete [_ response]
  (let [{:keys [status]} response]
    (when-let [error-msg ({400 "State store is missing or misconfigured"
                           500 "Delete state failed"}
                          status)]
      (throw (ex-info error-msg {:response response})))
    (when-not (= status 204)
      (throw-error status response))))

(defmethod handle-resp :state-transaction [_ response]
  (let [{:keys [status]} response]
    (when-let [error-msg ({400 "State store is missing or misconfigured or malformed request"}
                          status)]
      (throw (ex-info error-msg {:response response})))
    (when-not (= status 204)
      (throw-error status response))))

(defmethod handle-resp :default [_ response]
  (let [{:keys [status]} response]
    (when (and (>= status 200) (< status 300))
      (throw-error status response)))
  response)
