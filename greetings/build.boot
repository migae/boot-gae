(def +project+ 'tmp.greetings/boot-test)
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
       :module "greetings"
       :version +version+}
 :asset-paths #{"resources/public"}
 :resource-paths #{"src/clj" "filters"}
 :source-paths #{"config" "src/java"}

 :repositories {"clojars" "https://clojars.org/repo"
                "central" "http://repo1.maven.org/maven2/"
                "maven-central" "http://mvnrepository.com"}
 :dependencies   '[[org.clojure/clojure "1.8.0" :scope "runtime"]
                   [javax.servlet/servlet-api "2.5" :scope "runtime"]
                   [migae/boot-gae "0.1.0-SNAPSHOT" :scope "test"]

                   ;; this is for the GAE runtime (NB: scope provided):
                   [com.google.appengine/appengine-java-sdk LATEST :scope "provided" :extension "zip"]

                   ;; this is for the GAE services (NB: scope runtime):
                   [com.google.appengine/appengine-api-1.0-sdk LATEST :scope "runtime"]

                   ;; ;; this is required for gae appstats:
                   [com.google.appengine/appengine-api-labs LATEST :scope "provided"]

                   ;; [org.mobileink/migae.datastore "0.3.3-SNAPSHOT" :scope "runtime"]

                   [hiccup/hiccup "1.0.5"]
                   [cheshire/cheshire "5.3.1"]

                   [compojure/compojure "1.4.0"]
                   [ring/ring-core "1.4.0"]
                   [ring/ring-devel "1.4.0"]
                   [ring/ring-servlet "1.4.0"]
                   [ring/ring-defaults "0.1.5"]
                   [ns-tracker/ns-tracker "0.3.0" :scope "test"]
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
