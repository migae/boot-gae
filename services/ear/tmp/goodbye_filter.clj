(ns goodbye-filter
  (:import (javax.servlet Filter FilterChain FilterConfig
                          ServletRequest ServletResponse))
  (:require [ns-tracker.core :refer :all]))

(defn -init [^Filter this ^FilterConfig cfg]
  (println "goodbye-filter init invoked"))

(defn -destroy [^Filter this]
  (println "goodbye-filter destroy invoked"))

(defn -doFilter
  [^Filter this
   ^ServletRequest rqst
   ^ServletResponse resp
   ^FilterChain chain]
  (println "inbound:  goodbye-filter on: " (str (.getMethod rqst) " " (.getRequestURL rqst)))
  (.doFilter chain rqst resp)
  (println "outbound: goodbye-filter on: " (str (.getMethod rqst) " " (.getRequestURL rqst)))
  )
