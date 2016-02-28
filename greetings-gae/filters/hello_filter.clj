(ns hello-filter
  (:import (javax.servlet Filter FilterChain FilterConfig
                          ServletRequest ServletResponse))
  (:require [ns-tracker.core :refer :all]))

(defn -init [^Filter this ^FilterConfig cfg]
  (println "hello-filter init invoked"))

(defn -destroy [^Filter this]
  (println "hello-filter destroy invoked"))

(def modified-namespaces (ns-tracker ["./"]))

(defn -doFilter
  [^Filter this
   ^ServletRequest rqst
   ^ServletResponse resp
   ^FilterChain chain]
  (println "hello-filter invoked for: " rqst)
  (.doFilter chain rqst resp))
