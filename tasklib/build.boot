(def +project+ 'migae/boot-gae)
(def +version+ "0.1.0-SNAPSHOT")

(set-env!
 :source-paths #{"src"}
 :resource-paths #{"src"}
 ;; :asset-paths #{"src"}
 :repositories {"clojars" "https://clojars.org/repo"
                #_["maven-central" "http://mvnrepository.com"]
                "central" "http://repo1.maven.org/maven2/"}
 :dependencies   '[[org.clojure/clojure "1.8.0" :scope "provided"]
                   [boot/core "2.5.5" :scope "provided"]
                   [stencil "0.5.0"]
                   [adzerk/boot-test "1.0.7" :scope "test"]
                   ;; this is for the GAE runtime (NB: scope provided):
                   [com.google.appengine/appengine-java-sdk LATEST :scope "provided" :extension "zip"]
                   ;; ;; we need this so we can import KickStart for the run task:
                   [com.google.appengine/appengine-tools-sdk RELEASE]
                   ])

(task-options!
 target {:dir "build"}
 pom  {:project     +project+
       :version     +version+
       :description "Boot for GAE"
       :url         "https://github.com/migae/boot-gae"
       :scm         {:url "https://github.com/migae/boot-gae"}
       :license     {"EPL" "http://www.eclipse.org/legal/epl-v10.html"}})
