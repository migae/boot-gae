(def +project+ 'migae/boot-gae)
(def +version+ "0.1.1-SNAPSHOT")

(set-env!
 :source-paths #{"src"}
 :resource-paths #{"src"}
 ;; :asset-paths #{"src"}
 :repositories [["clojars" {:url "https://clojars.org/repo/"}]
                ["central" {:url "http://repo1.maven.org/maven2/"}]]
 :dependencies   '[[org.clojure/clojure "1.8.0" :scope "provided"]
                   [boot/core "2.7.1" :scope "provided"]
                   [boot/pod "2.7.1" :scope "provided"]
                   [me.raynes/fs "1.4.6"]
                   [stencil "0.5.0"]
                   [adzerk/boot-test "1.0.7" :scope "test"]
                   ;; ;; we need this so we can import KickStart for the run task:
                   ;; FIXME: what if user wants a different sdk version?
                   [com.google.appengine/appengine-tools-sdk RELEASE]
                   ])

(task-options!
 pom  {:project     +project+
       :version     +version+
       :description "Boot for GAE"
       :url         "https://github.com/migae/boot-gae"
       :scm         {:url "https://github.com/migae/boot-gae"}
       :license     {"EPL" "http://www.eclipse.org/legal/epl-v10.html"}}
 push {:repo "clojars"})
