(ns main.core
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.util.response :as rsp]
            [ring.util.servlet :as ring]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.defaults :refer :all]))

(defroutes main-routes
    (GET "/main" [name :as rqst]
         (do (println "main servlet handler:  main.core on " (:request-method rqst)
                      (str (.getRequestURL (:servlet-request rqst))))
             (-> (rsp/response (str "HOWDY there, from main.core servlet"))
                 (rsp/content-type "text/html"))))
    (route/not-found "<h1>main route not found</h1>"))

(ring/defservice
   (-> (routes
        main-routes)
       (wrap-defaults api-defaults)
       ))
