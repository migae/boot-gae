(ns hello-filter
  (:import (javax.servlet Filter FilterChain FilterConfig
                          ServletRequest ServletResponse))
  (:require [ns-tracker.core :refer :all]))

(defn -init [^Filter this ^FilterConfig cfg]
  (println "hello-filter init invoked"))

(defn -destroy [^Filter this]
  (println "hello-filter destroy invoked"))

#_(defn make-dofilter-method
  "Turns a handler into a function that takes the same arguments and has the
  same return value as the doFilter method in the servlet.Filter class."
  [handler]
  (fn [^Filter this
       ^HttpServletRequest request
       ^HttpServletResponse response
       ^FilterChain filter-chain]
    (let [request-map (-> request
                          (ring/build-request-map)
                          (ring/merge-servlet-keys servlet request response))]
      (if-let [response-map (handler request-map)]
        (.doFilter
         filter-chain
         request
         (update-servlet-response response response-map))
        (throw (NullPointerException. "Handler returned nil"))))))

(defn -doFilter
  [^Filter this
   ^ServletRequest rqst
   ^ServletResponse resp
   ^FilterChain chain]
  (println "INBOUND:  hello-filter on: " (str (.getMethod rqst) " " (.getRequestURL rqst)))
  (.doFilter chain rqst resp)
  (println "outbound: hello-filter on: " (str (.getMethod rqst) " " (.getRequestURL rqst))))
