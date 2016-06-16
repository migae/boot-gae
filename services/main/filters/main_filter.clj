(ns main-filter
  (:import (javax.servlet Filter FilterChain FilterConfig
                          ServletRequest ServletResponse))
  (:require [ns-tracker.core :refer :all]))

(defn -init [^Filter this ^FilterConfig cfg]
  (println "main-filter init invoked"))

(defn -destroy [^Filter this]
  (println "main-filter destroy invoked"))

(defn -doFilter
  [^Filter this
   ^ServletRequest rqst
   ^ServletResponse resp
   ^FilterChain chain]
  (println "inbound:  main-filter on: " (str (.getMethod rqst) " " (.getRequestURL rqst)))
  (.doFilter chain rqst resp)
  (println "outbound: main-filter on: " (str (.getMethod rqst) " " (.getRequestURL rqst)))
  )
