(ns greetings.goodbye
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.util.response :as rsp]
            [ring.util.servlet :as ring]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.defaults :refer :all]))

(defroutes goodbye-routes
    (GET "/goodbye/:name" [name]
         (-> (rsp/response (str "Hasta La Vista, " name "!  From the Goodbye servlet."))
             (rsp/content-type "text/html")))
    (route/not-found "<h1>Goodbye route not found</h1>"))

(ring/defservice
   (-> (routes
        goodbye-routes)
       (wrap-defaults api-defaults)
       ))
