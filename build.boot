(def +project+ 'migae/boot-gae)
(def +version+ "0.2.1-SNAPSHOT")

(set-env!
 :source-paths #{"src"}
 :resource-paths #{"src"}
 ;; :asset-paths #{"src"}
 :repositories #(conj % ["maven-central" {:url "https://mvnrepository.com"}]
                      ["central" "https://repo1.maven.org/maven2/"])
 :dependencies   '[[org.clojure/clojure "1.10.0" :scope "provided"]
                   [boot/core "2.8.3" :scope "provided"]
                   [boot/pod "2.8.3" :scope "provided"]
                   [me.raynes/fs "1.4.6"]
                   [stencil "0.5.0"]
                   [adzerk/boot-test "1.0.7" :scope "test"]
                   ;; ;; we need this so we can import KickStart for the run task:
                   ;; FIXME: what if user wants a different sdk version?
                   ;; [com.google.appengine/appengine-tools-sdk RELEASE]
                   ])

(task-options!
 pom  {:project     +project+
       :version     +version+
       :description "Boot for GAE"
       :url         "https://github.com/migae/boot-gae"
       :scm         {:url "https://github.com/migae/boot-gae"}
       :license     {"EPL" "http://www.eclipse.org/legal/epl-v10.html"}}
 push {:repo "clojars"})

(deftask monitor
  "watch etc."
  []
  (comp (watch)
        (notify :audible true)
        (pom)
        (jar)
        (install)))
