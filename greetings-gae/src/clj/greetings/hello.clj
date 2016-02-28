(ns greetings.hello
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.util.response :as rsp]
            [ring.util.servlet :as ring]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.defaults :refer :all]))

(println "ring reloading hello")

(defroutes hello-routes
    (GET "/hello/:name" [name]
         (-> (rsp/response (str "Hello there, " name))
             (rsp/content-type "text/html")))
    (route/not-found "<h1>Hello route not found</h1>"))

(ring/defservice
   (-> (routes
        hello-routes)
       (wrap-defaults api-defaults)
       ))
