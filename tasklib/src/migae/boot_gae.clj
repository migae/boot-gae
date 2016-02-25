(ns migae.boot-gae
  {:boot/export-tasks true}
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.set :as set]
            [clojure.pprint :as pp]
            [stencil.core :as stencil]
            [boot.user]
            [boot.pod :as pod]
            [boot.core :as boot]
            [boot.util :as util]
            [boot.task.built-in :as builtin])
  (:import [com.google.appengine.tools KickStart]
           [java.io File]
           [java.net URL URLClassLoader]))

            ;; [deraen.boot-less.version :refer [+version+]]))

;; (def ^:private deps
;;   [['deraen/less4clj +version+]])

(defn expand-home [s]
  (if (or (.startsWith s "~") (.startsWith s "$HOME"))
    (str/replace-first s "~" (System/getProperty "user.home"))
    s))

#_(doseq [dep (filter #(str/starts-with? (.getName %) "appengine-java-sdk")
                    (pod/resolve-dependency-jars (boot/get-env) true))]
  (println "DEP: " (.getName dep)))

(def sdk-string (let [jars (pod/resolve-dependency-jars (boot/get-env) true)
                   zip (filter #(str/starts-with? (.getName %) "appengine-java-sdk") jars)
                   fname (first (for [f zip] (.getName f)))
                    sdk-string (subs fname 0 (str/last-index-of fname "."))]
               ;; (println "sdk-string: " sdk-string)
               sdk-string))

(def config-map (merge {:build-dir "target"
                        :sdk-root (let [dir (str (System/getenv "HOME") "/.appengine-sdk")]
                                    (str dir "/" sdk-string))}
                       (reduce (fn [altered-map [k v]] (assoc altered-map k
                                                              (if (= k :sdk-root)
                                                                (str (expand-home v) "/" sdk-string)
                                                                v)))
                               {} boot.user/gae)))
                               ;; {} (:gae (boot/get-env)))))

;;(str (:build-dir config-map)
(def web-inf-dir "WEB-INF")

(def classes-dir (str web-inf-dir "/classes"))

(defn lib-dir [] "WEB-INF/lib")

(def appcfg-class "com.google.appengine.tools.admin.AppCfg")

;; for multiple subprojects:
(def root-dir "")
(def root-project "")

(def project-dir (System/getProperty "user.dir"))
(def build-dir (str/join "/" [project-dir "target"]))

(defn output-libs-dir [nm] (str/join "/" [build-dir "libs"]))
(defn output-resources-dir [nm] (str/join "/" [build-dir "resources" nm]))

(defn java-source-dir [nm] (str/join "/" [project-dir "src" nm "java"]))
(defn input-resources-dir [nm] (str/join "/" [project-dir "src" nm "resources"]))

(defn gae-app-dir [] "target")

(def sdk-root-property "appengine.sdk.root")
(def java-classpath-sys-prop-key "java.class.path")
(def sdk-root-sys-prop-key "appengine.sdk.root")

(defn print-task
  [task opts]
  (if (or (:verbose opts) (:verbose config-map) (:list-tasks config-map))
    (println "TASK: " task)))

(defn dump-props []
  (println "project-dir: " project-dir)
  (println "build-dir: " build-dir)
  (println "classes-dir: " classes-dir)
  (println "output-resources-dir: " (output-resources-dir nil))
  (println "java-source-dir: " (java-source-dir nil))
  (println "input-resources-dir: " (input-resources-dir nil))
  )

(defn dump-env []
  (let [e (boot/get-env)]
    (println "ENV:")
    (util/pp* e)))


(defn dump-tmpfiles
  [lbl tfs]
  (println "\n" lbl ":")
  (doseq [tf tfs] (println (boot/tmp-file tf))))

(defn dump-tmpdirs
  [lbl tds]
  (println "\n" lbl ":")
  (doseq [td tds] (println td)))

(defn dump-fs
  [fileset]
  (doseq [f (:dirs fileset)] (println "DIR: " f))
  (doseq [f (:tree fileset)] (println "F: " f)))
  ;; (dump-tmpfiles "INPUTFILES" (boot/input-files fileset))
  ;; (dump-tmpdirs "INPUTDIRS" (boot/input-dirs fileset))
  ;; (dump-tmpdirs "OUTPUTFILESET" (boot/output-files (boot/output-fileset fileset)))
  ;; (dump-tmpfiles "OUTPUTFILES" (boot/output-files fileset))
  ;; (dump-tmpdirs "OUTPUTDIRS" (boot/output-dirs fileset))
  ;; (dump-tmpfiles "USERFILES" (boot/user-files fileset))
  ;; (dump-tmpdirs "USERDIRS" (boot/user-dirs fileset)))

(def config-props
  {;; https://docs.gradle.org/current/userguide/war_plugin.html
   :web-app-dir-name "src/main/webapp" ;; String
   ;; :web-app-dir "project-dir/web-app-dir-name" ;; File
   })

#_(def runtask-params-defaults
  {;; :http-address 127.0.0.1
   ;; :http-port 8080
   ;; :daemon false
   ;; :disable-update-check false
   ;; :disable-datagram false
   ;; :jvm-flags []
   :allow-remote-shutdown true})
   ;; :download-sdk false

(def kw->opt
  {
   :allow-remote-shutdown "--allow_remote_shutdown"
   :default-gcs-bucket "--default_gcs_bucket"
   :disable-filesapi-warning "-disable_filesapi_warning"
   :disable_restricted_check "--disable_restricted_check"
   :disable-update-check "--disable_update_check"
   :enable_filesapi "--enable_filesapi"
   :enable-jacoco "--enable_jacoco"
   :jacoco-agent-jar "--jacoco_agent_jar"
   :jacoco-agent-args "--jacoco_agent_args"
   :jacoco-exec "--jacoco_exec"
   :external-resource-dir "--external_resource_dir"
   :generated-war-dir "--generated_dir" ;; "Set the directory where generated files are created."
   :generate-war "--generate_war"
   :http-address "--address"
   :http-port "--port"
   :instance-port "--instance_port"
   :jvm-flags "--jvm_flags"
   :no-java-agent "--no_java_agent"
   :property "--property" ;; ????
   :sdk-server "--server"  ;; DevAppServer param
   :sdk-root "--sdk_root"
   :start-on-first-thread "--startOnFirstThread"})

(defn ->args [param-map]
  (let [r (flatten (for [[k v] param-map]
                      (if (= k :jvm-flags)
                        (let [flags (str/split (first v) #" ")
                              fargs (into []
                                          (for [flag flags] (str "--jvm-flag=\"" flag "\"")))]
                          (do ;(println "FLAGS: " flags (type flags) (type (first flags)))
                              ;(println "FARGS: " fargs (type fargs))
                              (seq fargs)))
                        (str (get kw->opt k) "=" v))))]
    #_(println "MERGE: " (pr-str r))
    r))

(defn- find-mainfiles [fs]
  (->> fs
       boot/input-files
       (boot/by-ext [".clj"])))


(defn get-tools-jar []
  (let [file-sep (System/getProperty "file.separator")
        ;; _ (println "File Sep: " file-sep)
        tools-api-jar (str/join file-sep [(:sdk-root config-map) "lib" "appengine-tools-api.jar"])]
    (if (not (.exists (io/as-file tools-api-jar)))
      (throw (Exception. (str "Required library 'appengine-tools-api.jar' could not be found in specified path: " tools-api-jar "!"))))
    tools-api-jar))

(defn validate-tools-api-jar []
  (let [tools-api-jar (get-tools-jar)
        path-sep (File/pathSeparator)
        jcp (System/getProperty java-classpath-sys-prop-key)]
    #_(if (not (.contains jcp tools-api-jar))
        (System/setProperty java-classpath-sys-prop-key (str/join path-sep [jcp tools-api-jar])))
    (System/setProperty java-classpath-sys-prop-key tools-api-jar)
    #_(println "Java classpath: " (System/getProperty java-classpath-sys-prop-key))

    ;; Adding appengine-tools-api.jar to context ClassLoader
    ;; see also http://stackoverflow.com/questions/194698/how-to-load-a-jar-file-at-runtime?lq=1
    (let [;; ClassLoader rootClassLoader = ClassLoader.systemClassLoader.parent
          root-class-loader (.getParent (ClassLoader/getSystemClassLoader))
          ;;URLClassLoader appengineClassloader
          ;;  = new URLClassLoader([new File(appEngineToolsApiJar).toURI().toURL()] as URL[], rootClassLoader)
          gae-class-loader (let [tools-jar-url [(.toURL (.toURI (io/as-file tools-api-jar)))]]
                                 (URLClassLoader. (into-array tools-jar-url) root-class-loader))]
      ;; Thread.currentThread().setContextClassLoader(appengineClassloader)
      (.setContextClassLoader (Thread/currentThread) gae-class-loader))))

#_(boot/deftask foo "" []
  (boot/with-pre-wrap [fs]
    (println "FS: " (type fs))
    (let [asset-files (->> fs boot/output-files (boot/by-ext [".clj"]))]
         (doseq [asset asset-files] (println "foo" asset)))
    fs))

;; (boot/deftask assets
;;   "copy assets to build-dir"
;;   [v verbose bool "Print trace messages"
;;    t type TYPE kw "type to move"
;;    o odir ODIR str "output dir. default depends on file type."]
;;   (let [regex (re-pattern (condp = type
;;                             :clj  #"(.*clj$)"
;;                             :cljs  #"(.*cljs$)"
;;                             :css  #"(.*css$)"
;;                             :html #"(.*html$)"
;;                             :ico  #"(.*ico$)"
;;                             :js  #"(.*js$)"
;;                             ""))
;;         dest (if odir odir
;;                  (condp = type
;;                    :clj "WEB-INF/classes"
;;                    "./"))
;;         arg {regex (str dest "/$1")}]
;;     (builtin/sift :move arg)))

;; (boot/deftask clj-cp
;;   "Copy source .clj files to <build-dir>/WEB-INF/classes"
;;   []
;;   (builtin/sift :move {#"(.*\.clj$)" "WEB-INF/classes/$1"}))

(boot/deftask config-appengine
  "generate gae appengine-web.xml"
  [d dir DIR str "output dir"
   c config-syms SYMS #{sym} "namespaced symbols bound to meta-config data"
   k keep bool str "keep intermediate .clj files"
   v verbose bool "Print trace messages."]
  ;; (println "TASK: config-webapp")
  (let [config-syms (if config-syms config-syms #{'appengine/config})
        ;; _ (println "config-syms: " config-syms)
        odir (if dir dir web-inf-dir)
        config-map
        (into {} (for [config-sym (seq config-syms)]
                 (let [config-ns (symbol (namespace config-sym))]
                   ;; (println "CONFIG-NS: " config-ns)
                   (require config-ns)
                   ;; (doseq [[ivar isym] (ns-interns config-ns)] (println "interned: " ivar isym))
                   (if (not (find-ns config-ns)) (throw (Exception. (str "can't find appengine config ns"))))
                   (let [config-var (if-let [v (resolve config-sym)]
                                      v (throw
                                         (Exception.
                                          (str "can't find config var for: " config-sym))))
                         configs (deref config-var)]
                     configs))))]
    ;; (println "meta-config map :")
    ;; (pp/pprint config-map)
    (let [content (stencil/render-file
                         "migae/boot_gae/xml.appengine-web.mustache"
                         config-map)]
      (if verbose (println content))
      (comp
       (boot/with-pre-wrap fileset
         (let [tmp-dir (boot/tmp-dir!)
               xml-out-path (str odir "/appengine-web.xml")
               xml-out-file (doto (io/file tmp-dir xml-out-path) io/make-parents)
               ]
           (spit xml-out-file content)
           (-> fileset (boot/add-resource tmp-dir) boot/commit!)))))))

(boot/deftask config-webapp
  "generate gae web.xml"
  [d dir DIR str "output dir"
   c config-syms SYMS #{sym} "namespaced symbols bound to meta-config data"
   k keep bool str "keep intermediate .clj files"
   v verbose bool "Print trace messages."]
;;  (println "TASK: config-webapp")
  (let [config-syms (if config-syms config-syms #{'webapp/config})
        ;; _ (println "config-syms: " config-syms)
        odir (if dir dir web-inf-dir)

        config-map
        (into {} (for [config-sym (seq config-syms)]
                 (let [config-ns (symbol (namespace config-sym))]
                   ;; (println "CONFIG-NS: " config-ns)
                   (require config-ns)
                   ;; (doseq [[ivar isym] (ns-interns config-ns)] (println "interned: " ivar isym))
                   (if (not (find-ns config-ns)) (throw (Exception. (str "can't find appengine config ns"))))
                   (let [config-var (if-let [v (resolve config-sym)]
                                      v (throw
                                         (Exception.
                                          (str "can't find config var for: " config-sym))))
                         configs (deref config-var)]
                     configs))))]
    ;; (println "meta-config map :")
    ;; (pp/pprint config-map)
    (let [content (stencil/render-file
                         "migae/boot_gae/xml.web.mustache"
                         config-map)]
      (if verbose (println content))
      (comp
       (boot/with-pre-wrap fileset
         (let [tmp-dir (boot/tmp-dir!)
               xml-out-path (str odir "/web.xml")
               xml-out-file (doto (io/file tmp-dir xml-out-path) io/make-parents)
               ]
           (spit xml-out-file content)
           (-> fileset (boot/add-resource tmp-dir) boot/commit!)))))))

;; (boot/deftask config
;;   "generate gae web.xml"
;;   [d dir DIR str "output dir"
;;    k keep bool str "keep intermediate .clj files"
;;    v verbose bool "Print trace messages."]
;;   ;; (print-task "config" *opts*)

;;   ;; TODO: implement defaults
;;   (let [web-xml (stencil/render-file "migae/boot_gae/xml.web.mustache" config-map)
;;         content (stencil/render-file "migae/boot_gae/xml.appengine-web.mustache" config-map)
;;         odir (if dir dir web-inf-dir)]
;;     ;; (println "STENCIL: " web-xml)
;;     (comp
;;      ;; step 1: process template, put result in new Fileset
;;      (boot/with-pre-wrap fileset
;;        (let [tmp-dir (boot/tmp-dir!)
;;              web-xml-out-path (str odir "/web.xml")
;;              web-xml-out-file (doto (io/file tmp-dir web-xml-out-path) io/make-parents)
;;              xml-out-path (str odir "/appengine-web.xml")
;;              xml-out-file (doto (io/file tmp-dir xml-out-path) io/make-parents)
;;              ]
;;          (spit web-xml-out-file web-xml)
;;          (spit xml-out-file content)
;;          (-> fileset (boot/add-resource tmp-dir) boot/commit!))))))

     ;; ;; step 3: commit new .xml
     ;; (builtin/target :dir #{(str (:build-dir config-map) "/WEB-INF")}
     ;;                 :no-clean true))))

(boot/deftask deploy
  "Installs a new version of the application onto the server, as the default version for end users."
  ;; options from AppCfg.java, see also appcfg.sh --help
  [s server SERVER str "--server"
   e email EMAIL str   "The username to use. Will prompt if omitted."
   H host  HOST  str   "Overrides the Host header sent with all RPCs."
   p proxy PROXYHOST str "'PROXYHOST[:PORT]'.  Proxies requests through the given proxy server."
   _ proxy-https PROXYHOST str "'PROXYHOST[:PORT].  Proxies HTTPS requests through the given proxy server."
   _ no-cookies bool      "Do not save/load access credentials to/from disk."
   _ sdk-root ROOT str   "Overrides where the SDK is located."
   b build-dir BUILD str "app build dir"
   _ passin   bool          "Always read the login password from stdin."
   A application APPID str "application id"
   M module MODULE str      "module"
   V version VERSION str   "(major) version"
   _ oauth2 bool            "Ignored (OAuth2 is the default)."
   _ noisy bool             "Log much more information about what the tool is doing."
   _ enable-jar-splitting bool "Split large jar files (> 10M) into smaller fragments."
   _ jar-splitting-excludes SUFFIXES str "list of files to be excluded from all jars"
   _ disable-jar-jsps bool "Do not jar the classes generated from JSPs."
   _ enable-jar-classes bool "Jar the WEB-INF/classes content."
   _ delete-jsps bool "Delete the JSP source files after compilation."
   _ retain-upload-dir bool "Do not delete temporary (staging) directory used in uploading."
   _ compile-encoding ENC str "The character encoding to use when compiling JSPs."
   _ num-days NUM_DAYS int "number of days worth of log data to get"
   _ severity SEVERITY int "Severity of app-level log messages to get"
   _ include-all bool   "Include everything in log messages."
   a append bool          "Append to existing file."
   _ num-runs NUM-RUNS int "Number of scheduled execution times to compute."
   f force bool           "Force deletion of indexes without being prompted."
   _ no-usage-reporting bool "Disable usage reporting."
   D auto-update-dispatch bool "Include dispatch.yaml in updates"
   _ sdk-help bool "Display SDK help screen"
   v verbose bool          "Print invocation args"]
  (if (or (:list-tasks config-map) (:verbose config-map))
    (println "TASK: boot-gae/deploy"))
  (validate-tools-api-jar)
  ;; (println "PARAMS: " *opts*)
  (let [opts (merge {:sdk-root (:sdk-root config-map)
                     :use-java7 true
                     :build-dir (:build-dir config-map)}
                    *opts*)
        params (into [] (for [[k v] (remove (comp nil? second)
                                            (dissoc opts :build-dir :verbose :sdk-help))]
                          (str "--" (str/replace (name k) #"-" "_")
                               (if (not (instance? Boolean v)) (str "=" v)))))
        params (if (:sdk-help *opts*)
                 ["help" "update"]
                 (conj params "update" (:build-dir opts)))
        params (into-array String params)
        ;; ClassLoader classLoader = Thread.currentThread().contextClassLoader
        class-loader (-> (Thread/currentThread) (.getContextClassLoader))
        cl (.getParent class-loader)
        app-cfg (Class/forName appcfg-class true class-loader)]

  ;; def appCfg = classLoader.loadClass(APPENGINE_TOOLS_MAIN)
  ;; appCfg.main(params as String[])
    (def method (first (filter #(= (. % getName) "main") (. app-cfg getMethods))))
    (def invoke-args (into-array Object [params]))
    (if (or (:verbose *opts*) (:verbose config-map))
      (do (println "CMD: AppCfg")
          (doseq [arg params]
            (println "\t" arg))))
    #_(. method invoke nil invoke-args))
    )

;; (boot/deftask deps
;;   "Install dependency jars in <build-dir>/WEB-INF/lib"
;;   [v verbose bool "Print traces"]
;;   (print-task "deps" *opts*)
;;   (println "libdir" (lib-dir))
;;   (comp (builtin/uber :as-jars true)
;;         #_(builtin/sift :include #{#"jar$"})))
;;         ;; (builtin/target :dir #{(lib-dir)}
;;         ;;                 :no-clean true)))
;; ;;  (boot/reset-fileset))

(boot/deftask install-sdk
  "Unpack and install the SDK zipfile"
  [v verbose bool "Print trace messages"]
  ;;NB: java property expected by kickstart is "appengine.sdk.root"
  ;; (print-task "install-sdk" *opts*)
  (let [jar-path (pod/resolve-dependency-jar (boot/get-env)
                                             '[com.google.appengine/appengine-java-sdk "1.9.32"
                                               :extension "zip"])
        sdk-dir (io/as-file (:sdk-root config-map))
        prev        (atom nil)]
    (boot/with-pre-wrap fileset
      (if (.exists sdk-dir)
        (do
          (let [file-sep (System/getProperty "file.separator")
                tools-api-jar (str/join file-sep [(:sdk-root config-map) "lib" "appengine-tools-api.jar"])]
            (if (not (.exists (io/as-file tools-api-jar)))
              (do
                (println "Found sdk-dir but not its contents; re-exploding")
                (boot/empty-dir! sdk-dir)
                (println "Exploding SDK\n from: " jar-path "\n to: " (.getPath sdk-dir))
                (pod/unpack-jar jar-path (.getParent sdk-dir)))
              (if (or (:verbose *opts*) (:verbose config-map))
                (println "SDK already installed at: " (.getPath sdk-dir))))))
        (do
          (if (or (:verbose *opts*) (:verbose config-map))
            (println "Installing unpacked SDK to: " (.getPath sdk-dir)))
          (pod/unpack-jar jar-path (.getParent sdk-dir))))
      fileset)))

(boot/deftask libs
  "" []
  (comp
   (builtin/uber :as-jars true)
   (builtin/sift :include #{#"zip$"} :invert true)
   (builtin/sift :move {#"(.*\.jar$)" "WEB-INF/lib/$1"})))

(boot/deftask logging
  "configure gae logging"
  [l log LOG kw ":log4j or :jul"
   v verbose bool "Print trace messages."
   o odir ODIR str "output dir"]
  ;; (print-task "logging" *opts*)
  (let [content (stencil/render-file
                 (if (= log :log4j)
                   "migae/boot_gae/log4j.properties.mustache"
                   "migae/boot_gae/logging.properties.mustache")
                   config-map)
        odir (if odir odir
                 (condp = log
                   :jul web-inf-dir
                   :log4j classes-dir
                   nil web-inf-dir
                   (throw (IllegalArgumentException. (str "Unrecognized :log value: " log)))))
        out-path (condp = log
                   :log4j "log4j.properties"
                   :jul "logging.properties"
                   nil  "logging.properties")
        mv-arg {(re-pattern out-path) (str odir "/$1")}]
    ;; (println "STENCIL: " content)
    ;; (println "mv pattern: " mv-arg)
    (comp
     (boot/with-pre-wrap fs
       (let [tmp-dir (boot/tmp-dir!)
             ;; _ (println "odir: " odir)
             out-file (doto (io/file tmp-dir (str odir "/" out-path)) io/make-parents)]
         (spit out-file content)
         (-> fs (boot/add-resource tmp-dir) boot/commit!))))))

(boot/deftask reloader
  "generate reloader servlet filter"
  [c config-sym CFG sym "config sym"
   k keep bool "keep intermediate .clj files"
   v verbose bool "Print trace messages."]
  ;; (print-task "logging" *opts*)
  (let [reloader-aot-ns 'reloader-aot
        reloader-aot-path "reloader_aot.clj"
        reloader-aot-content (stencil/render-file "migae/boot_gae/reloader-aot.mustache"
                                                  {:reloader-ns reloader-aot-ns
                                                   :reloader-impl-ns "reloader"})
        reloader-impl-path "reloader.clj"
        reloader-impl-content (stencil/render-file "migae/boot_gae/reloader-impl.mustache" nil)]
    (if verbose (println "impl: " reloader-impl-content))
    (if verbose (println "aot: " reloader-aot-content))
    (comp
     (boot/with-pre-wrap fs
       ;; 1. generate filter aot master
       (let [aot-tmp-dir (boot/tmp-dir!)
             aot-out-file (doto (io/file aot-tmp-dir reloader-aot-path) io/make-parents)
             impl-tmp-dir (boot/tmp-dir!)
             impl-out-file (doto (io/file impl-tmp-dir reloader-impl-path) io/make-parents)]
         (spit aot-out-file reloader-aot-content)
         (spit impl-out-file reloader-impl-content)
         (-> fs
             (boot/add-source aot-tmp-dir)
             (boot/add-asset impl-tmp-dir)
             boot/commit!)))
     (builtin/aot :namespace #{reloader-aot-ns})
     (builtin/sift :include #{(re-pattern "reloader_aot.*.class")}
                   :invert true)
     ;; (if keep
     ;;   (builtin/sift :to-resource #{(re-pattern reloader-aot-path)})
     ;;   identity)
     ;; (builtin/sift :move {#"(.*class$)" "WEB-INF/classes/$1"})
     )))

(boot/deftask run
  "Run devappserver"
  [;; DevAppServerMain.java
   _ sdk-server VAL str "--server"
   _ http-address VAL str "The address of the interface on the local machine to bind to (or 0.0.0.0 for all interfaces).  Default: 127.0.0.1"
   _ http-port VAL int "The port number to bind to on the local machine. Default: 8080"
   _ disable-update-check bool "Disable the check for newer SDK versions. Default: true"
   _ generated-dir DIR str "Set the directory where generated files are created."
   ;; GENERATED_DIR_PROPERTY = "appengine.generated.dir";
   _ default-gcs-bucket VAL str  "Set the default Google Cloud Storage bucket name."
   _ instance-port bool "--instance_port"
   _ disable-filesapi-warning bool "-disable_filesapi_warning"
   _ enable_filesapi bool "--enable_filesapi"

   ;; SharedMain.java
   _ sdk-root PATH str "--sdk_root"
   _ disable_restricted_check bool "--disable_restricted_check"
   _ external-resource-dir VAL str "--external_resource_dir"
   _ allow-remote-shutdown bool "--allow_remote_shutdown"
   _ no-java-agent bool "--no_java_agent"

   ;; Kickstart.java
   _ generate-war bool "--generate_war"
   _ generated-war-dir PATH str "Set the directory where generated files are created."
   _ jvm-flags FLAG #{str} "--jvm_flags"
   _ start-on-first-thread bool "--startOnFirstThread"
   _ enable-jacoco bool "--enable_jacoco"
   _ jacoco-agent-jar VAL str"--jacoco_agent_jar"
   _ jacoco-agent-args VAL str"--jacoco_agent_args"
   _ jacoco-exec VAL str "--jacoco_exec"]

   ;; _ exploded-war-directory VAL str "--exploded_war_directory"

  (let [ks-params *opts* #_(merge runtask-params-defaults *opts*)]
    ;; (println "*OPTS*: " *opts*)
    ;; (println "KS-PARAMS: " ks-params)

    ;;FIXME: build a java string array from ks-params
    ;; first arg in gradle plugin: MAIN_CLASS = 'com.google.appengine.tools.development.DevAppServerMain'

    (let [args (->args ks-params)
          ;; _ (println "ARGS: " args)
          main-class "com.google.appengine.tools.development.DevAppServerMain"
          ;; jargs (list* main-class args)
          ;; jargs (into-array String (conj jargs "build/exploded-app"))

          jargs ["com.google.appengine.tools.development.DevAppServerMain"
                 (str "--sdk_root=" (:sdk-root config-map))
                 (gae-app-dir)]
          jargs (into-array String jargs)]

      ;; (println "jargs: " jargs (type jargs))
      ;; (doseq [a jargs] (println "JARG: " a))
      ;; implicit (System) params: java.class.path
      ;; (System/setProperty sdk-root-property sdk-root)
      ;; DEFAULT_SERVER = "appengine.google.com";

      (validate-tools-api-jar)

      ;; (pod/add-classpath "build/exploded-app/WEB-INF/classes/*")
      ;; (pod/add-classpath "build/exploded-app/WEB-INF/lib/*")
      ;; (doseq [j (pod/get-classpath)] (println "pod classpath: " j))

      ;; (System/setProperty "java.class.path"
      ;;                     (str/join ":" (into [] (for [j (pod/get-classpath)] (str j)))))

      ;; (println "system classpath: " (System/getenv "java.class.path"))

      (let [class-loader (-> (Thread/currentThread) (.getContextClassLoader))
            cl (.getParent class-loader)
            ;; _ (println "class-loader: " class-loader (type class-loader))
            ;; Class kickStart = Class.forName('com.google.appengine.tools.KickStart', true, classLoader)
            kick-start (Class/forName "com.google.appengine.tools.KickStart" true class-loader)
            ]
      ;; (println "kick-start: " kick-start (type kick-start))

        ;; (doseq [j (pod/get-classpath)]
        ;;   (let [url (java.net.URL. (str j))]
        ;;     (println "URL: " url)
        ;;     (-> cl (.addURL url))))

      ;; (pod/with-eval-in @pod
        ;; (def method (.getMethod kick-start "main" (class ???)))
        (def method (first (filter #(= (. % getName) "main") (. kick-start getMethods))))
        ;;(let [parms (.getParameterTypes method)] (println "param types: " parms))
        (def invoke-args (into-array Object [jargs]))
        (. method invoke nil invoke-args)
        ;; )
    ))))

(boot/deftask servlets
  "aot compile master servlet file"
  [k keep bool "keep intermediate .clj files"
   ;; d odir DIR str "output dir for generated class files"
   c config-sym SYM sym "namespaced symbol bound to meta-config data"
   n aot-ns NS str "namespace to generate and aot; default: 'servlets"
   v verbose bool "Print trace messages."]
  ;; (print-task "servlets" *opts*)
  (let [config-sym (if config-sym config-sym 'servlets/config)
        config-ns (symbol (namespace config-sym))
        servlet-ns (if aot-ns (symbol aot-ns) (gensym "servlets")) ;; (:servlet-ns config-map))
        servlet-filename (str (str/replace servlet-ns #"\.|-" {"." "/" "-" "_"}) ".clj")]
    (println "CONFIG SYM: " config-sym)
    (println "SERVLET NS: " servlet-ns (type servlet-ns))
    (println "SERVLET FN: " servlet-filename)
    (require config-ns)
    ;; (doseq [[ivar isym] (ns-interns config-ns)] (println "interned: " ivar isym))
    (if (not (find-ns config-ns)) (throw (Exception. (str "can't find appengine config ns"))))
    (let [config-var (if-let [v (resolve config-sym)]
                       v (throw (Exception. (str "can't find config var for: " config-sym))))
          servlet-configs (deref config-var)
          _ (println "servlet-configs: " servlet-configs)
          content (stencil/render-file "migae/boot_gae/servlets.mustache"
                                       (assoc servlet-configs
                                              :servlet-ns servlet-ns))]
      (if verbose (println content))
      (comp
       (boot/with-pre-wrap fileset
         (let [tmp-dir (boot/tmp-dir!)
               out-file (doto (io/file tmp-dir servlet-filename) io/make-parents)]
           (spit out-file content)
           (-> fileset (boot/add-source tmp-dir) boot/commit!)))
       (builtin/aot :namespace #{servlet-ns})
       (builtin/sift :include #{(re-pattern (str servlet-ns ".*.class"))}
                     :invert true)
       ;;FIXME: do not hardcode WEB-INF/classes
       (if keep
         (comp (builtin/sift :to-asset #{(re-pattern servlet-filename)})
               #_(builtin/sift :move {(re-pattern (str "(" servlet-filename ")")) "WEB-INF/classes/$1"}))
         identity)
       (builtin/sift :move {#"(.*class$)" "WEB-INF/classes/$1"})))))


(boot/deftask stencil
  "Process stencil templates"
  [s stencil STENCIL str "Path to a stencil (mustache) template. Must be on classpath."
   d data DATA edn "A data map."
   o outpath OUTPATH str "File path for output."
   v verbose bool "Print trace messages."]
  (print-task "stencil" *opts*)
  (let [content (stencil/render-file stencil data)]
    ;; (println "STENCIL: " content)
    (comp
     ;; step 1: process template, put result in new Fileset
     (boot/with-pre-wrap fileset
       (let [tmp-dir (boot/tmp-dir!)
             ;; out-path (str (str/replace outpath #"\.|-" {"." "/" "-" "_"}))
             out-file (doto (io/file tmp-dir outpath) io/make-parents)]
         (spit out-file content)
         (-> (boot/new-fileset) (boot/add-resource tmp-dir) boot/commit!))))))

(boot/deftask watch
  "watch for gae project"
  []
  (comp (builtin/watch)
        ;; (builtin/sift :to-resource #{#".*\.clj$"})
        (builtin/sift :move {#"(.*\.clj$)" "WEB-INF/classes/$1"})
        (builtin/target :no-clean)))

(boot/deftask dev
  "make a dev build - including reloader"
  [k keep bool "keep intermediate .clj files"]
  (comp (install-sdk)
        (libs)
        (logging)
        (config-appengine)
        (config-webapp)
        (reloader)
        (servlets :keep keep)
;;        (builtin/sift :to-resource #{#".*\.clj$"})
        (builtin/sift :move {#"(.*\.clj$)" "WEB-INF/classes/$1"})
        ))
