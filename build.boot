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
                    [cpmcdaniel/boot-copy "1.0" :scope "provided"]
                    ;;[cpmcdaniel/boot-copy "<version>" :scope "provided"]
                    [com.google.appengine/appengine-java-sdk "LATEST" ;; "1.9.32"
                     :scope "provided" :extension "zip"]
                    ;; we need this so we can import KickStart:
                    [com.google.appengine/appengine-tools-sdk "LATEST"] ;;"1.9.32"]
                    ;; ;; Webjars-locator uses logging
                    ;; [org.slf4j/slf4j-nop "1.7.12" :scope "test"]
                    ;; [org.webjars/webjars-locator "0.29"]
                    ;; ;; For testing the webjars asset locator implementation
                    ;; [org.webjars/bootstrap "3.3.6" :scope "test"]
                    ])

(def +version+ "0.1.0-SNAPSHOT")

;(require '[stencil.core :as stencil])

(task-options!
 target {:dir "build"}
  pom  {:project     'migae/boot-gae
        :version     +version+
        :description "Boot for GAE"
        :url         "https://github.com/migae/boot-gae"
        :scm         {:url "https://github.com/migae/boot-gae"}
        :license     {"EPL" "http://www.eclipse.org/legal/epl-v10.html"}})
