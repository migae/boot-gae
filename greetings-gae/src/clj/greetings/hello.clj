(ns greetings.hello
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.util.response :as rsp]
            [ring.util.servlet :as ring]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.defaults :refer :all]))

(println "ring reloading hello")

(defroutes hello-routes
    (GET "/hello/:name" [name :as rqst]
         (do (println "handler:  greetings.hello on " (:request-method rqst)
                      (str (.getRequestURL (:servlet-request rqst))))
             (-> (rsp/response (str "Hello there, " name))
                 (rsp/content-type "text/html"))))
    (GET "/foo/:name" [name :as rqst]
         (do (println "handler:  greetings.hello on " (:request-method rqst)
                      (str (.getRequestURL (:servlet-request rqst))))
             (-> (rsp/response (str name "?  I pity the foo!"))
                 (rsp/content-type "text/html"))))
    (route/not-found "<h1>Hello route not found</h1>"))

(ring/defservice
   (-> (routes
        hello-routes)
       (wrap-defaults api-defaults)
       ))
