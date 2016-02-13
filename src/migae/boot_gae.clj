(ns migae.boot-gae
  {:boot/export-tasks true}
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.set :as set]
            [stencil.core :as stencil]
            [boot.pod :as pod]
            [boot.core :as core]
            [boot.util :as util]
            [boot.task.built-in :as builtin]
            )
  (:import [com.google.appengine.tools KickStart]
           [java.io File]
           [java.net URL URLClassLoader]))

            ;; [deraen.boot-less.version :refer [+version+]]))

;; (def ^:private deps
;;   [['deraen/less4clj +version+]])

;; for multiple subprojects:
(def root-dir "")
(def root-project "")

(def project-dir (System/getProperty "user.dir"))
(def build-dir (str/join "/" [project-dir "build"]))

(def lib-dir  "build/WEB-INF/lib")
;;(def classes-dir  "build/WEB-INF/lib")

(defn output-classes-dir [] (str/join "/" [build-dir "WEB-INF" "classes"]))
(defn output-webapp-dir [] (str/join "/" [build-dir]))
;; (defn output-classes-dir [nm] (str/join "/" [build-dir "WEB-INF" "classes" nm]))
(defn output-libs-dir [nm] (str/join "/" [build-dir "libs"]))
(defn output-resources-dir [nm] (str/join "/" [build-dir "resources" nm]))

(defn java-source-dir [nm] (str/join "/" [project-dir "src" nm "java"]))
(defn input-resources-dir [nm] (str/join "/" [project-dir "src" nm "resources"]))

(defn gae-app-dir [] "build")

(def sdk-root-property "appengine.sdk.root")
(def java-classpath-sys-prop-key "java.class.path")
(def sdk-root-sys-prop-key "appengine.sdk.root")

(defn dump-props []
  (println "project-dir: " project-dir)
  (println "build-dir: " build-dir)
  (println "output-classes-dir: " (output-classes-dir nil))
  (println "output-resources-dir: " (output-resources-dir nil))
  (println "java-source-dir: " (java-source-dir nil))
  (println "input-resources-dir: " (input-resources-dir nil))
  )

(defn dump-env []
  (let [e (core/get-env)]
    (println "ENV:")
    (util/pp* e)))

#_(deftask copy-dir
  "Copy dir"
  [i input-dir PATH str     "The input directory path."
   o output-dir PATH str     "The output directory path."]
  (let [out-dir (io/file output-dir)]
    (with-pre-wrap fileset
      (let [in-files (->> fileset
                          output-files
                          (by-re matching)
                          (map (juxt tmppath tmpfile)))]
        (doseq [[path in-file] in-files]
          (let [out-file (doto (io/file out-dir path) io/make-parents)]
            (util/info "Copying %s to %s...\n" path out-dir)
            (io/copy in-file out-file)))
        fileset))))

(defn exploded-sdk-dir []
  (let [dir (str (System/getenv "HOME")
                 ;;FIXME: don't hardcode "/.gradle"
                 "/.gradle/appengine-sdk")
        fname (.getName
               (io/as-file (pod/resolve-dependency-jar
                            (core/get-env)
                            '[com.google.appengine/appengine-java-sdk "LATEST" :extension "zip"])))
        dirname (subs fname 0 (str/last-index-of fname "."))]
    ;; (println "SDK: " (str dir "/" dirname))
    (str dir "/" dirname)))

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

(defn- find-mainfiles [fs]
  (->> fs
       core/input-files
       (core/by-ext [".clj"])))


(defn get-tools-jar []
  (let [file-sep (System/getProperty "file.separator")
        _ (println "File Sep: " file-sep)
        tools-api-jar (str/join file-sep [(exploded-sdk-dir) "lib" "appengine-tools-api.jar"])]
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
    (println "Java classpath: " (System/getProperty java-classpath-sys-prop-key))

    ;; Adding appengine-tools-api.jar to context ClassLoader
    (let [;; ClassLoader rootClassLoader = ClassLoader.systemClassLoader.parent
          root-class-loader (.getParent (ClassLoader/getSystemClassLoader))
          ;;URLClassLoader appengineClassloader
          ;;  = new URLClassLoader([new File(appEngineToolsApiJar).toURI().toURL()] as URL[], rootClassLoader)
          gae-class-loader (let [tools-jar-url [(.toURL (.toURI (io/as-file tools-api-jar)))]]
                                 (URLClassLoader. (into-array tools-jar-url) root-class-loader))
          _ (println "GAE Class Loader: " gae-class-loader)]
      ;; Thread.currentThread().setContextClassLoader(appengineClassloader)
      (.setContextClassLoader (Thread/currentThread) gae-class-loader))))

(core/deftask config
  "config gae webapp"
  [s servlets SERVLETS edn   "Servlets map"]
  (println "TASK: boot-gae/config " servlets)
  (doseq [servlet servlets]
    (println "servlet: " servlet)
    #_(stencil/render-file "servlets.stencil" {:servlets servlets})))

(core/deftask install-sdk
  "Unpack and install the SDK zipfile"
  []
  ;;FIXME- support --sdk-root, --sdk-version
  ;;NB: java property expected by kickstart is "appengine.sdk.root"
  (print "EXPLODE-SDK: ")
  (let [jar-path (pod/resolve-dependency-jar (core/get-env)
                                             '[com.google.appengine/appengine-java-sdk "1.9.32"
                                               :extension "zip"])
                prev        (atom nil)]
    (core/with-pre-wrap fileset
      (let [tmpfiles (core/not-by-re [#"~$"] (core/input-files fileset))
            ;; _ (doseq [tf tmpfiles] (println (core/tmp-file tf)))
            src (->> fileset
                     (core/fileset-diff @prev)
                     core/input-files
                     (core/by-ext [".clj"]))
            sdk-dir (io/as-file (exploded-sdk-dir))]
        (reset! prev fileset)
        (if (.exists sdk-dir)
          (do
            (let [file-sep (System/getProperty "file.separator")
                  tools-api-jar (str/join file-sep [(exploded-sdk-dir) "lib" "appengine-tools-api.jar"])]
              (if (not (.exists (io/as-file tools-api-jar)))
                (do
                  (println "Found sdk-dir but not its contents; re-exploding")
                  (core/empty-dir! sdk-dir)
                  (println "Exploding SDK\n from: " jar-path "\n to: " (.getPath sdk-dir))
                  (pod/unpack-jar jar-path (.getParent sdk-dir)))
                ;; if verbose
                (println "SDK already exploded to: " (.getPath sdk-dir)))))
          (do
            ;; if verbose
            (println "Exploding SDK\n from: " jar-path "\n to: " (.getPath sdk-dir))
            (pod/unpack-jar jar-path (.getParent sdk-dir))))
    fileset))))

(defn dump-tmpfiles
  [lbl tfs]
  (println "\n" lbl ":")
  (doseq [tf tfs] (println (core/tmp-file tf))))

(defn dump-tmpdirs
  [lbl tds]
  (println "\n" lbl ":")
  (doseq [td tds] (println td)))

(defn dump-fs
  [fileset]
  (doseq [f (:dirs fileset)] (println "DIR: " f))
  (doseq [f (:tree fileset)] (println "F: " f)))
  ;; (dump-tmpfiles "INPUTFILES" (core/input-files fileset))
  ;; (dump-tmpdirs "INPUTDIRS" (core/input-dirs fileset))
  ;; (dump-tmpdirs "OUTPUTFILESET" (core/output-files (core/output-fileset fileset)))
  ;; (dump-tmpfiles "OUTPUTFILES" (core/output-files fileset))
  ;; (dump-tmpdirs "OUTPUTDIRS" (core/output-dirs fileset))
  ;; (dump-tmpfiles "USERFILES" (core/user-files fileset))
  ;; (dump-tmpdirs "USERDIRS" (core/user-dirs fileset)))

(core/deftask webapp
  "copy webapp to build"
  []
  (println "TASK: webapp")
  (comp
   (builtin/sift :include #{#".*.clj$"}
                 :invert true)
   (builtin/target :dir #{(output-webapp-dir)}
                   :no-clean true)))

#_(core/deftask clj-cp
  "Copy files from the fileset to another directory.
  Example: (copy jar files to /home/foo/jars):
     $ boot build copy -m '\\.jar$' -o /home/foo/jars"
  [o output-dir PATH str     "The output directory path."
   m matching REGEX #{regex} "The set of regexes matching paths to backup."]
  (println "TASK: clj-cp")
  (let [out-dir (if output-dir (io/file output-dir) (output-classes-dir))
        matching (if matching matching #{#"\.clj$"})]
    (core/with-pre-wrap fileset
      (let [in-files (->> fileset
                          core/input-files
                          (core/by-re matching)
                          (map (juxt core/tmp-path core/tmp-file)))]
        (doseq [[path in-file] in-files]
          (let [out-file (doto (io/file out-dir path) io/make-parents)]
            (util/info "Copying %s to %s...\n" path out-dir)
            (io/copy in-file out-file)))
        fileset))))

(core/deftask clj-cp
  "Copy source .clj files to <build>/WEB-INF/classes"
  []
  (println "TASK: clj-cp " (output-classes-dir))
  (comp
   (builtin/sift :include #{#".*.clj$"})
   (builtin/target :dir #{(output-classes-dir)}
                   :no-clean true)))

(core/deftask deps
  "Install dependency jars in <build>/WEB-INF/lib"
  []
  (println "TASK: libs")
  (core/with-pre-wrap fileset
  (let [tmp (core/tmp-dir!)
        prev (atom nil)
        jars (pod/jars-in-dep-order (core/get-env))
        out-dir (doto (io/file lib-dir)
                  io/make-parents)]
    (reset! prev fileset)
    (doseq [jar jars]
      (let [out-file (io/file out-dir (.getName jar))]
        ;; (println "Copying jar: " (.getName jar)
        ;;          " to: " (.getPath out-file))
        (io/make-parents out-file)
        (io/copy jar (io/as-file (.getPath out-file)))))
    fileset)))

#_(core/deftask clj-devx
  "clojure"
  []
  (println "TASK: clj-dev")
  (core/with-pre-wrap fileset
  (let [tmp (core/tmp-dir!)
        prev (atom nil)
        src (->> fileset
                 (core/fileset-diff @prev)
                 core/input-files
                 (core/not-by-re [#"~$"])
                 (core/by-ext [".clj"])
                 #_(map (juxt core/tmp-path core/tmp-file)))]
    (reset! prev fileset)

    (doseq [f src]
      (let [p (.getPath (core/tmp-file f))
            relpath (core/tmp-path f)
            ;; _ (println "relpath: " relpath (type relpath))
            of (str gae-app-dir "/WEB-INF/classes" "/" relpath)]
        (println "Copying " p " to " of)
        (io/copy (io/as-file p) (io/as-file of))))

    ;;(println "TMP: " tmp)
    ;; (doseq [in src]
    ;;   (println "SRC: " in)
    ;;   (let [in-file  (c/tmp-file in)
    ;;         in-path  (c/tmp-path in)
    ;;         out-file (doto (io/file tmp in-path) io/make-parents)]
    ;;     (compile-lc! in-file out-file)))
  #_(-> fileset
      (core/add-resource tmp)
      core/commit!)

    ;; (dump-props)
    ;; (dump-env)
    ;; (println "FILES keys: " (keys fileset))
    ;; (util/pp* fileset)

    fileset)))

 ;; (let [hls (->> fileset
 ;;                     (boot/fileset-diff @prev-fileset)
 ;;                     boot/input-files
 ;;                     (boot/by-ext [file-ext])
 ;;                     (map (juxt boot/tmp-path boot/tmp-file)))]
 ;;        (reset! prev-fileset fileset)
 ;;        (doseq [[p in] hls]
 ;;          (let [out (doto (io/file tmp-files p) io/make-parents)]
 ;;            (->> in slurp (inline-code start end) (spit out)))))
 ;;      (-> fileset (boot/add-source tmp-files)  boot/commit!))))

(core/deftask gae-aot "compile clj" []
  (println "TASK: gae-aot")
  ;; (core/with-pre-wrap fileset
  ;;   (let [fs fileset]
      (comp
            (builtin/aot :namespace #{'migae.servlets})
            (builtin/sift :include #{#".*\.class" #".*\.clj"})
            (builtin/target :dir #{(output-classes-dir)}
                            :no-clean true)
            #_(builtin/sift :add-resource #{"src/main/clojure"}
                          :include #{#".*~$"}
                          :invert true)))

;; NB: explode-war comes from the gae gradle plugin - not necessary with boot!
;; (core/deftask explode-war
;;   "explode war

;; The default behavior of the War task is to copy the content of src/main/webapp to the root of the archive. Your webapp directory may of course contain a WEB-INF sub-directory, which may contain a web.xml file. Your compiled classes are compiled to WEB-INF/classes. All the dependencies of the runtime [24] configuration are copied to WEB-INF/lib.  https://docs.gradle.org/current/userguide/war_plugin.html"
;;   []
;;   )

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
    (println "MERGE: " (pr-str r))
    r))

(def app-cfg-ns "com.google.appengine.tools.admin.AppCfg")

(core/deftask deploy
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
  (validate-tools-api-jar)
  (println "PARAMS: " *opts*)
  (let [opts (merge {:sdk-root (exploded-sdk-dir) :use-java7 true :build-dir "build"}
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
        app-cfg (Class/forName app-cfg-ns true class-loader)]

  ;; def appCfg = classLoader.loadClass(APPENGINE_TOOLS_MAIN)
  ;; appCfg.main(params as String[])
    (def method (first (filter #(= (. % getName) "main") (. app-cfg getMethods))))
    (println "MAIN: " method)

    ;; (let [jargs ["--enable_jar_splitting"
    ;;              (str "--sdk_root=" (exploded-sdk-dir))
    ;;              "update"]
    ;;       jargs (into-array String jargs)]
    ;;   (def invoke-args (into-array Object [jargs]))

    (def invoke-args (into-array Object [params]))
    (if (:verbose *opts*)
      (doseq [arg params]
        (println (str "INVOKE ARG: " arg))))
    (. method invoke nil invoke-args))
    )

(core/deftask run
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

  (let [ks-params *opts* #_(merge runtask-params-defaults *opts*)
        ]
    (println "*OPTS*: " *opts*)
    (println "KS-PARAMS: " ks-params)

    ;;FIXME: build a java string array from ks-params
    ;; first arg in gradle plugin: MAIN_CLASS = 'com.google.appengine.tools.development.DevAppServerMain'

    (let [args (->args ks-params)
          _ (println "ARGS: " args)
          main-class "com.google.appengine.tools.development.DevAppServerMain"
          ;; jargs (list* main-class args)
          ;; jargs (into-array String (conj jargs "build/exploded-app"))

          jargs ["com.google.appengine.tools.development.DevAppServerMain"
                 (str "--sdk_root=" (exploded-sdk-dir))
                 (gae-app-dir)]
          jargs (into-array String jargs)]

      (println "jargs: " jargs (type jargs))
      (doseq [a jargs] (println "JARG: " a))
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

    ;; ClassLoader classLoader = Thread.currentThread().contextClassLoader
      (let [;;class-loader (. (Thread/currentThread) getContextClassLoader)
            class-loader (-> (Thread/currentThread) (.getContextClassLoader))
            cl (.getParent class-loader)
            _ (println "class-loader: " class-loader (type class-loader))
            ;; Class kickStart = Class.forName('com.google.appengine.tools.KickStart', true, classLoader)
            kick-start (Class/forName "com.google.appengine.tools.KickStart" true class-loader)
            ]
      (println "kick-start: " kick-start (type kick-start))

      #_(doseq [j (pod/get-classpath)]
        (let [url (java.net.URL. (str j))]
          (println "URL: " url)
          (-> cl (.addURL url))))

      ;; (pod/with-eval-in @pod
        (def method (first (filter #(= (. % getName) "main") (. kick-start getMethods))))
        (def invoke-args (into-array Object [jargs]))
        (. method invoke nil invoke-args)
        ;; )

    ))))


  ;; In AppEnginePlugin.groovy:
    ;; static File getExplodedSdkDirectory(Project project) {
    ;;     new File(project.gradle.gradleUserHomeDir, 'appengine-sdk')
    ;; static File getExplodedAppDirectory(Project project) {
    ;;     getBuildSubDirectory(project, 'exploded-app')
    ;; static File getStagedAppDirectory(Project project) {
    ;;     getBuildSubDirectory(project, "staged-app")
    ;; static File getDownloadedAppDirectory(Project project) {
    ;;     getBuildSubDirectory(project, 'downloaded-app')
    ;; static File getDiscoveryDocDirectory(Project project) {
    ;;     getBuildSubDirectory(project, 'discovery-docs')
    ;; static File getEndpointsClientLibDirectory(Project project) {
    ;;     getBuildSubDirectory(project, 'client-libs')
    ;; static File getGenDir(Project project) {
    ;;     getBuildSubDirectory(project, 'generated-source')
    ;; static File getEndpointsExpandedSrcDir(Project project) {
    ;;     new File(getGenDir(project),'endpoints/java')
    ;; static File getBuildSubDirectory(Project project, String subDirectory) {
    ;;     def subDir = new StringBuilder()
    ;;     subDir <<= project.buildDir
    ;;     subDir <<= System.getProperty('file.separator')
    ;;     subDir <<= subDirectory
    ;;     new File(subDir.toString())

  ;; )
