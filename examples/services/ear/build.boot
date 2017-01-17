(def +project+ 'tmp/services-app)
(def +version+ "0.1.0-SNAPSHOT")

(set-env!
 :gae {:app-id +project+
       :version +version+}

 ;; FIXME: mv to edn file?
 :checkouts '[[tmp/greetings "0.1.0-SNAPSHOT" :module "greetings" :port 8088]
              [tmp.services/default "0.1.0-SNAPSHOT" :port 8083]] ;; default :module is "default"

 ;; :asset-paths #{"assets"}

 :source-paths #{"config"}

 :repositories {"clojars" "https://clojars.org/repo"
                "central" "http://repo1.maven.org/maven2/"}

 :dependencies '[[migae/boot-gae "0.1.0-SNAPSHOT" :scope "test"]
                 [com.google.appengine/appengine-java-sdk LATEST :scope "test" :extension "zip"]
                 [tmp.services/default "0.1.0-SNAPSHOT"]
                 [tmp/greetings "0.1.0-SNAPSHOT"]])

(require '[migae.boot-gae :as gae])

;; (def modules {:modules [{:coords [tmp.modules/default "0.1.0-SNAPSHOT"]
;;                          :default true
;;                          :port 8088}
;;                         {:coords [tmp/greetings "0.1.0-SNAPSHOT"]
;;                          :port 8089}]})

;; (def modules '[[tmp.modules/default "0.1.0-SNAPSHOT" :default true :port 8088]
;;                [tmp/greetings "0.1.0-SNAPSHOT" :port 8089]])

;; (task-options!
;;  gae/ear {:modules modules}
;;  gae/run {:modules modules})
