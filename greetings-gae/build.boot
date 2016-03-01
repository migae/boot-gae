(def +project+ 'tmp/hello-gae)
(def +version+ "0.1.0-SNAPSHOT")

(set-env!
 :gae {:app-id "hello-gae-id"
       :version "0-1-0-SNAPSHOT"}
 :asset-paths #{"resources/public"}
 :source-paths #{"config" "src/clj" "filters"}
 :repositories {"clojars" "https://clojars.org/repo"
                "maven-central" "http://mvnrepository.com"
                "central" "http://repo1.maven.org/maven2/"}
 :dependencies   '[[org.clojure/clojure "1.8.0" :scope "provided"]
                   [boot/core "2.5.2" :scope "provided"]
                   [migae/boot-gae "0.1.0-SNAPSHOT" :scope "test"]
                   [com.google.appengine/appengine-java-sdk "1.9.32" :scope "test" :extension "zip"]
                   ;; we need this so we can import KickStart:
                   [com.google.appengine/appengine-tools-sdk "1.9.32" :scope "test"]
                   [javax.servlet/servlet-api "2.5"]
                   [org.clojure/clojure "1.7.0"]
                   [compojure/compojure "1.4.0"]
                   [ring/ring-core "1.4.0"]
                   [ring/ring-devel "1.4.0"]
                   [ring/ring-servlet "1.4.0"]
                   [ring/ring-defaults "0.1.5"]
                   [ns-tracker/ns-tracker "0.3.0"]
                   ])

(require '[migae.boot-gae :as gae]
         #_'[boot.task.built-in :as builtin])

(task-options!
 pom  {:project     +project+
       :version     +version+
       :description "Example code, boot, miraj, GAE"
       :license     {"EPL" "http://www.eclipse.org/legal/epl-v10.html"}})

(deftask dev
  "dev build and save"
  [k keep bool "keep"]
  (comp (gae/dev :keep)
        (target)))
