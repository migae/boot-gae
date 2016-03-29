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
                   [boot/core "2.5.2" :scope "provided"]
                   [stencil "0.5.0"]
                   [adzerk/boot-test "1.0.7" :scope "test"]
                   ;; [com.google.appengine/appengine-java-sdk LATEST :scope "runtime" :extension "zip"]
                   ;; [com.google.appengine/appengine-java-sdk "LATEST" :scope "compile" :extension "zip"]
                   ;; ;; we need this so we can import KickStart:
                   [com.google.appengine/appengine-tools-sdk "LATEST"]
                   ])

(task-options!
 target {:dir "build"}
 pom  {:project     +project+
       :version     +version+
       :description "Boot for GAE"
       :url         "https://github.com/migae/boot-gae"
       :scm         {:url "https://github.com/migae/boot-gae"}
       :license     {"EPL" "http://www.eclipse.org/legal/epl-v10.html"}})
