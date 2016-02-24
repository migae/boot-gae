(def +project+ 'migae/gae-hello-boot)
(def +version+ "0.1.0-SNAPSHOT")

(set-env!
 ;; :asset-paths #{"assets"}
 :source-paths #{"src"}
 :resource-paths #{"resources/public" "src"}
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
   :aot #{'hello.servlets}
   :app-id (clojure.string/replace +project+ #"/" ".")
   :module "foo"
   :version (clojure.string/lower-case (clojure.string/replace +version+ #"\." "-"))
   :display-name {:name "hello app"}
   :descr {:text "description of this web app, for web.xml etc."}
   :appengine {:thread-safe true
               ;; :public-root "/static"
               :system-properties {:props [{:name "myapp.maximum-message-length" :value "140"}
                                           {:name "myapp.notify-every-n-signups" :value "1000"}
                                           {:name"myapp.notify-url"
                                            :value "http://www.example.com/supnotfy"}]}
               ;; :env-vars [{:name "FOO" :value "BAR"}]
               :logging {:jul {:name "java.util.logging.config.file"
                               :value "WEB-INF/logging.properties"}}
               ;; #_{:log4j {:name "java.util.logging.config.file"
               ;;          :value "WEB-INF/classes/log4j.properties"}}}
               :sessions true
               :ssl true
               :async-session-persistence {:enabled "true" :queue-name "myqueue"}
               :inbound-services [{:service :mail} {:service :warmup}]
               :precompilation true
               :scaling {:basic {:max-instances 11 :idle-timeout "10m"
                                 :instance-class "B2"}
                         :manual {:instances 5
                                  :instance-class "B2"}
                         :automatic {:instance-class "F2"
                                     :idle-instances {:min 5
                                                      ;; ‘automatic’ is the default value.
                                                      :max "automatic"}
                                     :pending-latency {:min "30ms" :max "automatic"}
                                     :concurrent-requests {:max 50}}}
               ;; :resource-files {:include [{:path "**.xml"
               ;;                            :expiration "4d h5"
               ;;                            :http-header {:name "Access-Control-Allow-Origin"
               ;;                                          :value "http://example.org"}}]
               ;;                  :exclude [{:path "feed/**.xml"}]}
               ;; :static-files {:include {:path "foo/**.png"
               ;;                          :expiration "4d h5"
               ;;                          :http-header {:name "Access-Control-Allow-Origin"
               ;;                                        :value "http://example.org"}}
               ;;                :exclude {:path "bar/**.zip"}}
               }
   ;;see http://www.opensource.apple.com/source/JBoss/JBoss-739/jakarta-tomcat-LE-jdk14/conf/web.xml
   :mime-mappings [{:ext "abs" :type "audio/x-mpeg"}
                   {:ext "gz"  :type "application/x-gzip"}
                   {:ext "htm" :type "text/html"}
                   {:ext "html" :type "text/html"}
                   {:ext "svg" :type "image/svg+xml"}
                   {:ext "txt" :type "text/plain"}
                   {:ext "xml" :type "text/xml"}
                   {:ext "xsl" :type "text/xsl"}
                   {:ext "zip" :type "application/zip"}]
   :welcome {:file "index.html"}
   :errors [{:code 404 :url "/404.html"}] ;; use :code, or:type, e.g 'java.lang.String
   :servlet-ns 'hello.servlets
   :servlets [{:ns 'hello.echo  ;; = servlet-class
               ;; :jsp - alternative to :ns, for using java servlet pages
               :name "echo-servlet"
               :display {:name "Awesome Echo Servlet"}
               :desc {:text "blah blah"}
               :url "/echo/*"
               :params [{:name "greeting" :val "Hello"}]
               :load-on-startup {:order 3}}

              {:ns 'hello.math      ;; REQUIRED
               :name "math-servlet"  ;; REQUIRED
               :url "/math/*"      ;; REQUIRED
               :params [{:name "op" :val "+"}
                        {:name "arg1" :val 3}
                        {:name "arg2" :val 2}]}]
   :appstats {:admin-console {:url "/appstats" :name "Appstats"}
              :name "appstats"
              :desc {:text "Google Appstats Service"}
              :url "/admin/appstats/*"
              :security-role "admin"
              :filter {:display {:name "Google Appstats"}
                       :desc {:text "Google Appstats Filter"}
                       :url "/*"
                       :params [{:name "logMessage"
                                 :val "Appstats available: /appstats/details?time={ID}"}
                                {:name "calculateRpcCosts"
                                 :val true}]}
              :servlet {:display {:name "Google Appstats"}}}
   :filters [{:ns 'hello.reloader   ; REQUIRED
              :name "reloader"      ; REQUIRED
              :display {:name "Clojure reload filter"} ; OPTIONAL
              :urls [{:url "/echo/*"}
                     {:url "/math/*"}]
              :desc {:text "clojure reload filter"}}]
   :security [{:resource {:name "foo" :desc {:text "Foo resource security"}
                          :url "/foo/*"}
               :role "admin"}]})

(require '[migae.boot-gae :as gae]
         '[boot-miraj :as mrj]
         #_'[boot.task.built-in :as builtin])

(task-options!
 mrj/config {:root "build/exploded-app"
             :configs {'components.greetings
                       {:ns 'miraj.greetings :resources "miraj_components"}}}
 pom  {:project     +project+
       :version     +version+
       :description "Example code, boot, miraj, GAE"
       :license     {"EPL" "http://www.eclipse.org/legal/epl-v10.html"}})

(deftask build
  "run all the boot-gae prep tasks"
  []
  (comp (gae/libs)
        (gae/logging)
        (gae/config)
        (sift :move {#"(.*\.clj$)" "WEB-INF/classes/$1"})
        (gae/servlets)
        ;; (sift :include #{#"class$"} ;; retain transient clj files
        ;;               :move {#"(.*class$)" "WEB-INF/classes/$1"})
        ;; (target :no-clean true)
        #_(gae/run)))
