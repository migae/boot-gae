(ns hello.math
  (:require [clojure.math.numeric-tower :as math]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.util.response :as rsp]
            [ring.util.servlet :as ring]
            [ring.middleware.defaults :refer :all]))

(println "ring reloading math")

(defroutes math-routes
  (context
   "/math" []
   (GET "/plus" {params :query-params}  ; query string params
        (let [x (read-string (get params "x"))
              y (read-string (get params "y"))]
          (str (+ x y) "\n")))
   (GET "/foo" []
         (str "bar"))
   (POST "/minus" [x y :as req]        ; body params
         (str (- (read-string x) (read-string y)) "\n"))
   (GET "/times/:x/:y" [x y]           ; named (path) params
        (-> (rsp/response (str "X: " (* (read-string x) (read-string y)) "\n"))
            (rsp/content-type "text/html")))
   (GET "/power" {:keys [headers] :as req} ; header params
        (let [x (read-string (get headers "x-x"))
              y (read-string (get headers "x-y"))]
          (str (math/expt x y) "\n")))
    (route/not-found "<h1>Math API not found</h1>")))

(ring/defservice
 ;; "math-"
  (-> (routes
       math-routes)
      (wrap-defaults api-defaults)
      ))
