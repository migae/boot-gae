(set-env!
 :checkouts '[[tmp/greetings "0.1.0-SNAPSHOT" :module "greetings" :port 8089]
              [tmp.modules/main "0.1.0-SNAPSHOT" :port 8083]] ;; defaults to :module "main"
 :asset-paths #{"assets"}
 :repositories {"clojars" "https://clojars.org/repo"
                "central" "http://repo1.maven.org/maven2/"}
 :dependencies '[[migae/boot-gae "0.1.0-SNAPSHOT" :scope "test"]
                 [com.google.appengine/appengine-java-sdk LATEST :scope "test" :extension "zip"]
                 [tmp.modules/main "0.1.0-SNAPSHOT"]
                 [tmp/greetings "0.1.0-SNAPSHOT"]])

(require '[migae.boot-gae :as gae])

;; (def modules {:modules [{:coords [tmp.modules/main "0.1.0-SNAPSHOT"]
;;                          :default true
;;                          :port 8088}
;;                         {:coords [tmp/greetings "0.1.0-SNAPSHOT"]
;;                          :port 8089}]})

;; (def modules '[[tmp.modules/main "0.1.0-SNAPSHOT" :default true :port 8088]
;;                [tmp/greetings "0.1.0-SNAPSHOT" :port 8089]])

;; (task-options!
;;  gae/ear {:modules modules}
;;  gae/run {:modules modules})
