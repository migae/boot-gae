(ns bar-filter
  (:import (javax.servlet Filter FilterChain FilterConfig
                          ServletRequest ServletResponse))
  (:require [ns-tracker.core :refer :all]))

(defn -init [^Filter this ^FilterConfig cfg]
  (println "bar-filter init invoked"))

(defn -destroy [^Filter this]
  (println "bar-filter destroy invoked"))

(def modified-namespaces (ns-tracker ["./"]))

(defn -doFilter
  [^Filter this
   ^ServletRequest rqst
   ^ServletResponse resp
   ^FilterChain chain]
  (println "bar-filter invoked for: " rqst)
  (.doFilter chain rqst resp))
