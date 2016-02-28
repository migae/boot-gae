(ns foo-filter
  (:import (javax.servlet Filter FilterChain FilterConfig
                          ServletRequest ServletResponse))
  (:require [ns-tracker.core :refer :all]))

(defn -init [^Filter this ^FilterConfig cfg]
  (println "foo-filter init invoked"))

(defn -destroy [^Filter this]
  (println "foo-filter destroy invoked"))

(def modified-namespaces (ns-tracker ["./"]))

(defn -doFilter
  [^Filter this
   ^ServletRequest rqst
   ^ServletResponse resp
   ^FilterChain chain]
  (println "foo-filter invoked for: " rqst)
  (.doFilter chain rqst resp))
