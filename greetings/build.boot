(def +project+ 'tmp/hello-gae)
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
 :gae {:app-id "boot-test"
       :version "0-1-0-snapshot"}
 :asset-paths #{"resources/public"}
 :source-paths #{"config" "src/clj" "filters" "src/java"}
 :repositories {"clojars" "https://clojars.org/repo"
                "maven-central" "http://mvnrepository.com"
                "central" "http://repo1.maven.org/maven2/"}
 :dependencies   '[[org.clojure/clojure "1.8.0" :scope "provided"]
                   [boot/core "2.5.2" :scope "provided"]
                   [migae/boot-gae "0.1.0-SNAPSHOT" :scope "test"]

                   ;; appengine setup: api version 1.0, sdk version LATEST
                   [com.google.appengine/appengine-api-1.0-sdk LATEST :scope "provided"]
                   [com.google.appengine/appengine-java-sdk LATEST :scope "provided" :extension "zip"]
                   ;; this is required for gae appstats:
                   [com.google.appengine/appengine-api-labs LATEST] ;; <!-- <scope>test</scope> -->
                   ;; we need this so we can import KickStart:
                   [com.google.appengine/appengine-tools-sdk LATEST :scope "test"]

                   [javax.servlet/servlet-api "2.5" :scope "provided"]
                   [compojure/compojure "1.4.0"]
                   [ring/ring-core "1.4.0"]
                   [ring/ring-devel "1.4.0"]
                   [ring/ring-servlet "1.4.0"]
                   [ring/ring-defaults "0.1.5"]
                   [ns-tracker/ns-tracker "0.3.0"]
                   ])

(require '[migae.boot-gae :as gae]
         '[boot.task.built-in :as builtin])

(task-options!
 pom  {:project     +project+
       :version     +version+
       :description "Example code, boot, miraj, GAE"
       :license     {"EPL" "http://www.eclipse.org/legal/epl-v10.html"}})

(deftask dev
  "dev build and save"
  [k keep bool "keep"]
  (comp (javac)
        (gae/dev)))

(deftask bldtest
  "make a dev build - including reloader"
  [k keep bool "keep intermediate .clj and .edn files"
   v verbose bool "verbose"]
  (comp (gae/install-sdk :verbose verbose)
        (gae/libs :verbose verbose)
        (gae/logging :verbose verbose)
        (builtin/show :fileset true)
        (builtin/sift :to-asset #{#"(.*\.clj$)"}
                      :move {#"(.*\.clj$)" "WEB-INF/classes/$1"})
        ;; (clj)
        ;; (appstats)
        ;; (filters :keep keep)
        ;; (servlets :keep keep)
        ;; (reloader :keep keep)
        ;; (webxml)
        ;;(appengine)
        ))
