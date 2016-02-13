(ns {{app-ns}}.servlets
  (:refer-clojure))

{{#servlets}}
(gen-class :name {{servlet}}
           :extends javax.servlet.http.HttpServlet
           :impl-ns {{servlet}})
{{/servlets}}

(gen-class :name {{app-ns}}.reloader
           :implements [javax.servlet.Filter]
           :impl-ns {{app-ns}}.reloader)
