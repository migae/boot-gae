(ns migae.boot-gae
  {:boot/export-tasks true}
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [boot.pod :as pod]
            [boot.core :as core]
            [boot.util :as util])
  (:import [com.google.appengine.tools KickStart]
           [java.io File]
           [java.net URL URLClassLoader]))

            ;; [deraen.boot-less.version :refer [+version+]]))

;; (def ^:private deps
;;   [['deraen/less4clj +version+]])

;; TODO: support --sdk-root option
(def sdk-root-property "appengine.sdk.root")
(def java-classpath-sys-prop-key "java.class.path")
(def sdk-root-sys-prop-key "appengine.sdk.root")
(def build-dir "build")

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
    (if (not (.contains jcp tools-api-jar))
      (System/setProperty java-classpath-sys-prop-key (str/join path-sep [jcp tools-api-jar])))
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

(core/deftask explode-sdk
  "Explode SDK jar"
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

(core/deftask explode-war
  "explode war

The default behavior of the War task is to copy the content of src/main/webapp to the root of the archive. Your webapp directory may of course contain a WEB-INF sub-directory, which may contain a web.xml file. Your compiled classes are compiled to WEB-INF/classes. All the dependencies of the runtime [24] configuration are copied to WEB-INF/lib.  https://docs.gradle.org/current/userguide/war_plugin.html"
  []
  )

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

  (let [ks-params *opts* #_(merge runtask-params-defaults *opts*)]
    (println "*OPTS*: " *opts*)
    (println "KS-PARAMS: " ks-params)

    ;;FIXME: build a java string array from ks-params
    ;; first arg in gradle plugin: MAIN_CLASS = 'com.google.appengine.tools.development.DevAppServerMain'

    (let [args (->args ks-params)
          main-class "com.google.appengine.tools.development.DevAppServerMain"
          jargs (into [] (cons main-class args))
          jargs (into-array String (conj jargs "build/exploded-bapp"))

          jargs ["com.google.appengine.tools.development.DevAppServerMain"
                 (str "--sdk_root=" (exploded-sdk-dir))
                 "build/exploded-app"]
          jargs (into-array String jargs)]

      (println "JARGS: " jargs (type jargs))
      (doseq [a jargs] (println "JARG: " a))
      ;; implicit (System) params: java.class.path
      ;; (System/setProperty sdk-root-property sdk-root)
      ;; DEFAULT_SERVER = "appengine.google.com";

      (validate-tools-api-jar)

    ;; ClassLoader classLoader = Thread.currentThread().contextClassLoader
      (let [class-loader (. (Thread/currentThread) getContextClassLoader)
            _ (println "class-loader: " class-loader (type class-loader))
            ;; Class kickStart = Class.forName('com.google.appengine.tools.KickStart', true, classLoader)
            kick-start (Class/forName "com.google.appengine.tools.KickStart" true class-loader)
            ]
      (println "kick-start: " kick-start (type kick-start))
      (println "classpath: " (System/getenv "java.class.path"))
      (def method (first (filter #(= (. % getName) "main") (. kick-start getMethods))))
      (def invoke-args (into-array Object [jargs]))
      (. method invoke nil invoke-args)

        ;; kickStart.main(params as String[])
        ;; (. com.google.appengine.tools.KickStart main jargs)
;;        (. (eval kick-start) main jargs)
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
