(def +project+ 'tmp/greetings)
(def +version+ "0.1.0-SNAPSHOT")

;; gae does not yet support java 1.8
;; to set java version on os x put this in ~/.bash_profile
;; function setjdk() {
;;   if [ $# -ne 0 ]; then
;;    removeFromPath '/System/Library/Frameworks/JavaVM.framework/Home/bin'
;;    if [ -n "${JAVA_HOME+x}" ]; then
;;     removeFromPath $JAVA_HOME
;;    fi
;;    export JAVA_HOME=`/usr/libexec/java_home -v $@`
;;    export PATH=$PATH:$JAVA_HOME/bin
;;   fi
;;  }
;; then:  $ setjdk 1.7

(set-env!
 :gae {:app-id +project+
       ;; :module "greetings"
       :version +version+}
 :asset-paths #{"resources/public"}
 :resource-paths #{"src/clj" "filters"}
 :source-paths #{"config" "src/java"}

 :repositories {"maven-central" "http://mvnrepository.com"
                "central" "http://repo1.maven.org/maven2/"
                "clojars" "https://clojars.org/repo"}

 :dependencies   '[[org.clojure/clojure "1.8.0" :scope "runtime"]
                   [migae/boot-gae "0.1.0-SNAPSHOT" :scope "test"]

                   [javax.servlet/servlet-api "2.5" :scope "provided"]

                   ;; this is for the GAE runtime (NB: scope provided):
                   [com.google.appengine/appengine-java-sdk RELEASE :scope "provided" :extension "zip"]

                   ;; ;; this is required for gae appstats (NB: scope runtime, not provided?):
                   [com.google.appengine/appengine-api-labs RELEASE :scope "runtime"]

                   ;; this is for the GAE services like datastore (NB: scope runtime):
                   ;; (required for appstats, which uses memcache)
                   [com.google.appengine/appengine-api-1.0-sdk RELEASE :scope "runtime"]

                   ;; [org.mobileink/migae.datastore "0.3.3-SNAPSHOT" :scope "runtime"]

                   [hiccup/hiccup "1.0.5"]
                   [cheshire/cheshire "5.3.1"]
                   [compojure/compojure "1.4.0"]
                   [ring/ring-core "1.4.0"]
                   [ring/ring-servlet "1.4.0"]
                   [ring/ring-defaults "0.1.5"]
                   [ring/ring-devel "1.4.0" :scope "test"]
                   [ns-tracker/ns-tracker "0.3.0"]
                   ])

(require '[migae.boot-gae :as gae]
         '[boot.task.built-in :as builtin])

(task-options!
 pom  {:project     +project+
       :version     +version+
       :description "Example code, boot, GAE"
       :license     {"EPL" "http://www.eclipse.org/legal/epl-v10.html"}})

(def web-inf-dir "WEB-INF")
(def classes-dir (str web-inf-dir "/classes"))

;; same as gae/build:
(deftask btest
  "assemble, configure, and build app"
  [k keep bool "keep intermediate .clj and .edn files"
   p prod bool "production build, without reloader"
   v verbose bool "verbose"]
  (comp (gae/install-sdk :verbose verbose)
        (gae/libs :verbose verbose)
        (gae/logging :verbose verbose)
        (gae/appstats :verbose verbose)
        (builtin/javac) ;; :options ["-verbose"])
        (if prod identity (gae/reloader :keep keep :verbose verbose))
        (gae/filters :keep keep :verbose verbose)
        (gae/servlets :keep keep :verbose verbose)
        (gae/webxml :verbose verbose)
        (gae/appengine :verbose verbose)
        (builtin/sift :move {#"(.*clj$)" (str classes-dir "/$1")})
        (builtin/sift :move {#"(.*\.class$)" (str classes-dir "/$1")})
        ))
