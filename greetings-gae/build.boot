;; caution: this example uses +project+ to set the GAE appid, see below
(def +project+ 'tmp/hello-gae)
(def +version+ "0.1.0-SNAPSHOT")

(set-env!
 :gae {:app-id "hello-gae-id"
       :version "0-1-0-SNAPSHOT"}
 :asset-paths #{"resources/public" "filters"}
 :source-paths #{"config" "src/clj"}
 ;; :source-paths #{"src"}
 ;; :resource-paths #{"resources/public" "src"}
 :repositories {"clojars" "https://clojars.org/repo"
                "maven-central" "http://mvnrepository.com"
                "central" "http://repo1.maven.org/maven2/"}
 :dependencies   '[[org.clojure/clojure "1.8.0" :scope "provided"]
                   [boot/core "2.5.2" :scope "provided"]
                   [migae/boot-gae "0.1.0-SNAPSHOT" :scope "test"]
                   [miraj/boot-miraj "0.1.0-SNAPSHOT" :scope "test"]
                   [miraj/html "5.1.0-SNAPSHOT"]
                   [components/greetings "0.1.0-SNAPSHOT"]
                   ;; [boot/core "2.5.2" :scope "provided"]
                   ;; [adzerk/boot-test "1.0.7" :scope "test"]
                   [com.google.appengine/appengine-java-sdk "1.9.32"
                    :scope "provided" :extension "zip"]
                   ;; we need this so we can import KickStart:
                   [com.google.appengine/appengine-tools-sdk "1.9.32"]
                   [javax.servlet/servlet-api "2.5"]
                   [org.clojure/clojure "1.7.0"]
                   [org.clojure/math.numeric-tower "0.0.4"]
                   [compojure/compojure "1.4.0"]
                   [ring/ring-core "1.4.0"]
                   [ring/ring-devel "1.4.0"]
                   [ring/ring-servlet "1.4.0"]
                   [ring/ring-defaults "0.1.5"]
                   [ns-tracker/ns-tracker "0.3.0"]
                   ])

(def gae
  ;; web.xml doco: http://docs.oracle.com/cd/E13222_01/wls/docs81/webapp/web_xml.html
  {;; :build-dir ; default: "build";  gradle compatibility: "build/exploded-app"
   ;; :sdk-root ; default: ~/.appengine-sdk; gradle compatibility: "~/.gradle/appengine-sdk"
   :list-tasks true
   ;; :verbose true
   :module "foo"
   })

(require '[migae.boot-gae :as gae]
         ;; '[boot-miraj :as mrj]
         #_'[boot.task.built-in :as builtin])

(task-options!
 ;; gae/config-appengine {:config-syms #{'appengine/config
 ;;                                      'appstats/config
 ;;                                      'version/config}}
 ;; gae/config-webapp {:config-syms #{'appstats/config
 ;;                                   'filters/config
 ;;                                   'security/config
 ;;                                   'servlets/config
 ;;                                   'webapp/config
 ;;                                   'version/config}}

 pom  {:project     +project+
       :version     +version+
       :description "Example code, boot, miraj, GAE"
       :license     {"EPL" "http://www.eclipse.org/legal/epl-v10.html"}})

(deftask dev
  "dev build and save"
  []
  (comp (gae/dev)
        (target)))
