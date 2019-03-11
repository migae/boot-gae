(ns migae.boot-gae
  {:boot/export-tasks true}
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.set :as set]
            [clojure.pprint :as pp]
            [me.raynes.fs :as fs]
            [stencil.core :as stencil]
            [boot.user]
            [boot.pod :as pod]
            [boot.core :as boot]
            [boot.util :as util]
            [boot.task.built-in :as builtin]
            [boot.file            :as file]
            [boot.from.digest     :as digest])
  (:import [com.google.appengine.tools KickStart]
           [java.io File]
           [java.net URL URLClassLoader]))

(def boot-version "2.7.1")

(def boot-config-edn "_boot_config.edn")
(def webapp-edn "webapp.edn")
(def appengine-edn "appengine.edn")
;;(def appstats-edn "appstats.edn")
(def jul-edn "jul.edn")
(def log4j-edn "log4j.edn")

(def meta-inf-dir "META-INF")
(def web-inf-dir "WEB-INF")
  ;; (let [mod (-> (boot/get-env) :gae :module :name)]
  ;;   (str (if mod (str mod "/")) "WEB-INF")))

;; http://stackoverflow.com/questions/2751033/clojure-program-reading-its-own-manifest-mf
(defn manifest-map
  "Returns the mainAttributes of the manifest of the passed in class as a map."
  [j]
  (println "JAR: " j (type j))
  (->> (str "jar:file:" j "!/META-INF/MANIFEST.MF")
       clojure.java.io/input-stream
       java.util.jar.Manifest.
       .getMainAttributes
       (map (fn [[k v]] [(str k) v]))
       (into {})))

(defn get-target-dir
  [fileset servlet]
  (if servlet
    "target"
    ;; (let [boot-config-edn-files (->> (boot/input-files fileset)
    ;;                                  (boot/by-name [boot-config-edn]))
    ;;       boot-config-edn-f (condp = (count boot-config-edn-files)
    ;;                           0 (throw (Exception. (str boot-config-edn " file not found")))
    ;;                           1 (first boot-config-edn-files)  ;; this is a boot.tmpdir.TmpFile
    ;;                           (throw (Exception.
    ;;                                   (str "only one _boot_config.edn file allowed, found "
    ;;                                        (count boot-config-edn-files)))))
    ;;       boot-config-edn-map (if boot-config-edn-f
    ;;                             (-> (boot/tmp-file boot-config-edn-f) slurp read-string)
    ;;                             nil)]
    ;;   (if boot-config-edn-map
    ;;     (str "target/" (-> boot-config-edn-map :module :name))
        (let [appengine-edn-fs (->> fileset
                                    boot/input-files
                                    (boot/by-name [appengine-edn]))

              appengine-edn-f (condp = (count appengine-edn-fs)
                                0 (throw (Exception. (str appengine-edn " file not found")))
                                1 (first appengine-edn-fs)
                                (throw (Exception. (str "Only one " appengine-edn " file allowed"))))

              appengine-config-map (-> (boot/tmp-file appengine-edn-f) slurp read-string)]
          (if servlet "target" (if (-> appengine-config-map :services)
                                 "target/default"
                                 (str "target/" (-> appengine-config-map :module :name)))))))

(defn get-module-name
  [fileset servlet]
  (if servlet
    "default"
    ;; (let [boot-config-edn-files (->> (boot/input-files fileset)
    ;;                                  (boot/by-name [boot-config-edn]))
    ;;       boot-config-edn-f (condp = (count boot-config-edn-files)
    ;;                           0 (throw (Exception. boot-config-edn " file not found"))
    ;;                           1 (first boot-config-edn-files)  ;; this is a boot.tmpdir.TmpFile
    ;;                           (throw (Exception.
    ;;                                   (str "only one _boot_config.edn file allowed, found "
    ;;                                        (count boot-config-edn-files)))))
    ;;       boot-config-edn-map (if boot-config-edn-f
    ;;                             (-> (boot/tmp-file boot-config-edn-f) slurp read-string)
    ;;                             nil)]
    ;;   (if boot-config-edn-map
    ;;           (-> boot-config-edn-map :module :name)
        (let [appengine-edn-fs (->> fileset
                                    boot/input-files
                                    (boot/by-name [appengine-edn]))
              appengine-edn-f (condp = (count appengine-edn-fs)
                                0 (throw (Exception. appengine-edn " file not found"))
                                1 (first appengine-edn-fs)
                                (throw (Exception. (str "Only one " appengine-edn " file allowed"))))

              appengine-config-map (-> (boot/tmp-file appengine-edn-f) slurp read-string)
              module (if servlet "default"
                         (if (-> appengine-config-map :services)
                           "default"
                           (-> appengine-config-map :module :name)))]
          module)))

(defn expand-home [s]
  (if (or (.startsWith s "~") (.startsWith s "$HOME"))
    (str/replace-first s "~" (System/getProperty "user.home"))
    s))

(def sdk-string (let [jars (pod/resolve-dependency-jars (boot/get-env) true)
                      zip (filter #(.startsWith (.getName %) "appengine-java-sdk") jars)]
                  (if (empty? zip)
                    (println "appengine-java-sdk zipfile not found")
                    (let [fname (first (for [f zip] (.getName f)))
                          sdk-string (subs fname 0 (.lastIndexOf fname "."))]
                      ;; (println "sdk-string: " sdk-string)
                      sdk-string))))

(def config-map (merge {:build-dir "target"
                        :sdk-root (let [dir (str (System/getenv "HOME") "/.appengine-sdk")]
                                    (str dir "/" sdk-string))}
                       (reduce (fn [altered-map [k v]] (assoc altered-map k
                                                              (if (= k :sdk-root)
                                                                (str (expand-home v) "/" sdk-string)
                                                                v)))
                               {}
                               (:gae (boot/get-env)))))

;;(str (:build-dir config-map)

(def classes-dir (str web-inf-dir "/classes"))

(def lib-dir (str web-inf-dir "/lib"))

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

(defn gae-app-dir []
  (let [mod (str (-> (boot/get-env) :gae :module :name))]
    (str "target" (if mod (str "/" mod)))))

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

;; (boot/deftask foo "" []
;;   (boot/with-pre-wrap [fs]
;;     (println "FS: " (type fs))
;;     ;; (let [asset-files (->> fs boot/output-files (boot/by-ext [".clj"]))]
;;     ;;      (doseq [asset asset-files] (println "foo" asset)))
;;     fs))

;; (boot/deftask clj
;;   "Assetize and mv source .clj files to <build-dir>/WEB-INF/classes"
;;   []
;;   (builtin/sift :move {#"(.*\.clj$)" (str web-inf-dir "/classes/$1")}
;;                 :to-resource #{#"clj$"}))

;; (boot/deftask aot
;;   "Built-in aot does not allow custom *compile-path*"

;;   [a all          bool   "Compile all namespaces."
;;    d dir PATH str "where to place generated class files"
;;    n namespace NS #{sym} "The set of namespaces to compile."]

;;   (let [tgt         (boot/tmp-dir!)
;;         dir         (if dir dir "")
;;         out-dir     (io/file tgt dir)
;;         foo         (doto (io/file out-dir "foo") io/make-parents)
;;         pod-env     (update-in (boot/get-env) [:directories] conj (.getPath out-dir))
;;         compile-pod (future (pod/make-pod pod-env))]
;;     (boot/with-pre-wrap [fs]
;;       (boot/empty-dir! tgt)
;;       (let [all-nses (->> fs boot/fileset-namespaces)
;;             nses     (->> all-nses (set/intersection (if all all-nses namespace)) sort)]
;;         (pod/with-eval-in @compile-pod
;;           (require '[clojure.java.io :as io])
;;           (let [foo ~(.getPath foo)]
;;             (io/make-parents (io/as-file foo))
;;             ;;(pod/add-classpath (io/as-file outdir))
;;             (binding [*compile-path* ~(.getPath out-dir)]
;;               (doseq [[idx ns] (map-indexed vector '~nses)]
;;                 (boot.util/info "Compiling %s/%s %s...\n" (inc idx) (count '~nses) ns)
;;                 (compile ns)))))
;;         (-> fs (boot/add-resource tgt) boot/commit!)))))

(boot/deftask appengine
  "generate gae appengine-web.xml"
  [c config-syms SYMS #{sym} "namespaced symbols bound to meta-config data"
   d dir DIR str "output dir"
   k keep bool "keep intermediate .clj files"
   m module MODULE str "module dirpath"
   s servlet bool "generate config for servlet app instead of service component DEPRECATED"
   u unit-test bool "configure for unit testing (module: 'default')"
   v verbose bool "Print trace messages."]
  ;; boolean hasUppercase = !password.equals(password.toLowerCase());
  (let [workspace (boot/tmp-dir!)
        config-workspace (boot/tmp-dir!)
        prev-pre (atom nil)
        dir (if dir dir web-inf-dir)]
    (boot/with-pre-wrap fileset
       (let [boot-config-edn-files (->> (boot/fileset-diff @prev-pre fileset)
                                    boot/input-files
                                    (boot/by-re [(re-pattern (str boot-config-edn "$"))]))
             boot-config-edn-f (condp = (count boot-config-edn-files)
                             0 (do (if verbose (util/info (str "Creating " boot-config-edn "\n")))
                                   (io/file boot-config-edn)) ;; this creates a java.io.File
                             1 (first boot-config-edn-files)  ;; this is a boot.tmpdir.TmpFile
                             (throw (Exception.
                                     (str "only one " boot-config-edn " file allowed; found "
                                          (count boot-config-edn-files)))))
             boot-config-edn-map (if (instance? boot.tmpdir.TmpFile boot-config-edn-f)
                                   (-> (boot/tmp-file boot-config-edn-f) slurp read-string)
                                   {})

             appengine-edn-fs (->> (boot/fileset-diff @prev-pre fileset)
                                   boot/input-files
                                   (boot/by-name [appengine-edn]))

             appengine-edn-f (condp = (count appengine-edn-fs)
                               0 (throw (Exception. appengine-edn " file not found"))
                               1 (first appengine-edn-fs)
                               (throw (Exception. (str "Only one " appengine-edn " file allowed"))))

             appengine-config-map (-> (boot/tmp-file appengine-edn-f) slurp read-string)

             module (if unit-test "default" (get-module-name fileset servlet))
             #_(if servlet "default"
                        (if (-> appengine-config-map :services)
                          "default"
                          (if (-> appengine-config-map :module)
                            (if (-> appengine-config-map :module :default)
                              "default"
                              (if-let [m (-> appengine-config-map :module :name)]
                                m
                                (throw (Exception.
                                        (str ":module :name required in "
                                             appengine-edn)))))
                            (throw (Exception.
                                    (str ":module clause required in"
                                         appengine-edn))))))

             appengine-config-map (assoc-in appengine-config-map
                                            [:module :name] module)

             app-id (:app-id appengine-config-map)

             version (-> appengine-config-map :module :version)
             _ (if version
                 (if (not= version  (.toLowerCase version))
                   (throw (Exception. (str "Upper-case not allowed in GAE version string: " version))))
                 (throw (Exception. ":module :version string required in " appengine-edn)))

             appengine-config-map (assoc
                                   (-> appengine-config-map
                                       (assoc-in [:system-properties]
                                                 (into
                                                  (:system-properties appengine-config-map)
                                                  {:props (:system-properties boot-config-edn-map)})))
                                   :app-id app-id)

             ;; inject appengine map into master map
             master-config (conj boot-config-edn-map appengine-config-map)
             master-config (with-out-str (pp/pprint master-config))
             boot-config-edn-out-file (io/file config-workspace
                                               (if (instance? boot.tmpdir.TmpFile boot-config-edn-f)
                                                 (boot/tmp-path boot-config-edn-f)
                                                 boot-config-edn-f))
             ]
         ;;(println "master config: " master-config)
         ;;(println "appengine-config-map 2: " appengine-config-map)
         (let [content (stencil/render-file
                        "migae/boot_gae/xml.appengine-web.mustache"
                        appengine-config-map)]
           ;;(if verbose (println content))
           (if verbose (util/info "Configuring appengine-web.xml\n"))
           (let [xml-out-path (str dir "/appengine-web.xml")
                 xml-out-file (doto (io/file workspace xml-out-path) io/make-parents)
                 ]
             (spit boot-config-edn-out-file master-config)
             (spit xml-out-file content))))
    (-> fileset
        (boot/add-source config-workspace)
        (boot/add-resource workspace)
        boot/commit!))))

;; (defn- add-appstats!
;;   [reloader-ns urls in-file out-file]
;;   (let [spec (-> in-file slurp read-string)]
;;     (util/info "Adding :appstats to %s...\n" (.getName in-file))
;;     (io/make-parents out-file)
;;     (let [m (-> spec
;;                 (assoc-in [:reloader]
;;                           {:ns reloader-ns
;;                            :name "reloader"
;;                            :display {:name "Clojure reload filter"}
;;                            :urls (if (empty? urls) [{:url "./*"}]
;;                                      (merge (for [url urls] {:url url})))
;;                            :desc {:text "Clojure reload filter"}}))
;;           s (with-out-str (pp/pprint m))]
;;     (spit out-file s))))

;; appstats not supported for java 8
#_(boot/deftask appstats
  "enable GAE Appstats"
  [k keep bool "keep intermediate .clj files"
   ;; n gen-reloader-ns NS sym "ns for gen-reloader"
   ;; r reloader-impl-ns NS sym "ns for reloader"
   w web-inf WEB-INF str "WEB-INF dir, default: WEB-INF"
   v verbose bool "Print trace messages."]
  (let [workspace (boot/tmp-dir!)
        prev-pre (atom nil)
        web-inf (if web-inf web-inf web-inf-dir)
        ]
    (comp
     (boot/with-pre-wrap [fileset]
       (let [boot-config-edn-files (->> (boot/user-files fileset)
                                        (boot/by-re [(re-pattern (str boot-config-edn "$"))]))
             ;; (boot/by-name [boot-config-edn]))
             boot-config-edn-f (condp = (count boot-config-edn-files)
                                 0 (do (util/info (str "Creating " boot-config-edn "\n"))
                                       (io/file boot-config-edn)) ;; this creates a java.io.File
                                 1 (first boot-config-edn-files)  ;; this is a boot.tmpdir.TmpFile
                                 (throw (Exception. "only one _boot_config.edn file allowed")))
             boot-config-edn-map (if (instance? boot.tmpdir.TmpFile boot-config-edn-f)
                                   (-> (boot/tmp-file boot-config-edn-f) slurp read-string)
                                   {})]
         ;; (println "boot-config-edn-map: " boot-config-edn-map)

         (if (:appstats boot-config-edn-map)
           fileset
           (do
             ;; step 1: read the edn config file
             (let [appstats-fs (->> (boot/input-files fileset)
                                    (boot/by-name [appstats-edn]))]
               (if (= (count appstats-fs) 0)
                 (throw (Exception. (str appstats-edn " file not found"))))
               (if (> (count appstats-fs) 1)
                 (throw (Exception. (str "only one " appstats-edn " file allowed"))))
               (let [appstats-edn-f (first appstats-fs)
                     appstats-config (-> (boot/tmp-file appstats-edn-f) slurp read-string)]

                 ;; step 2: inject appstats config map into master config map
                 (if verbose (util/info (str "Elaborating " boot-config-edn " with :appstats stanza\n")))
                 (let [m (-> boot-config-edn-map
                             (assoc-in [:appstats] (:appstats appstats-config)))
                       boot-config-edn-s (with-out-str (pp/pprint m))
                       ;; _ (println "boot-config-edn-s: " boot-config-edn-s)
                       boot-config-edn-out-file (io/file workspace
                                                         (if (instance? boot.tmpdir.TmpFile boot-config-edn-f)
                                                           (boot/tmp-path boot-config-edn-f)
                                                           boot-config-edn-f))]
                   (io/make-parents boot-config-edn-out-file)
                   (spit boot-config-edn-out-file boot-config-edn-s)))))))
       (reset! prev-pre
               (-> fileset
                   (boot/add-resource workspace)
                   boot/commit!)))
     (if keep
       (builtin/sift :to-resource #{(re-pattern boot-config-edn)})
       identity)
     )))

(declare earxml filters install-sdk libs logging reloader servlets target webxml)

(boot/deftask assemble
  "assemble a service-based app (ear)"
  [v verbose bool "Print trace messages."]
  (fn middleware [next-handler]
    (fn handler [fileset]
      (if verbose (util/info "Assembling application"))
      (let [workspace (boot/tmp-dir!)
            appengine-config-edn-files (->> (boot/input-files fileset)
                                            (boot/by-re [(re-pattern (str appengine-edn "$"))]))
            ;; (if (> (count services-fs) 1) (throw (Exception. "Only one " appengine-edn " file allowed")))
            ;; (if (= (count services-fs) 0)
            appengine-config-edn-f (condp = (count appengine-config-edn-files)
                                     0 (throw (Exception. appengine-edn " file not found"))
                                     1 (first appengine-config-edn-files)
                                     (throw (Exception.
                                             (str "Only one " appengine-edn " file allowed; found "
                                                  (count appengine-config-edn-files)))))

            appengine-cfg (-> (boot/tmp-file appengine-config-edn-f) slurp read-string)
            services (:services appengine-cfg)

            target-middleware identity
            target-handler (target-middleware next-handler)]
        (doseq [service services]
          (let [coord (pod/map->coord service)
                jar-path (pod/resolve-dependency-jar (boot/get-env) coord)
                out-dir (io/file workspace)]
                ;;(doto (io/file workspace (:name service)) io/make-parents)]
            (pod/unpack-jar jar-path out-dir)))
        (target-handler (-> fileset ;; (boot/new-fileset)
                            (boot/add-asset workspace :exclude #{(re-pattern (str "/META-INF/.*"))})
                            (boot/commit!)))))))

(boot/deftask build-sift
  []
  ;; [u unit-test bool "sift for unit-test config"]
  (fn middleware [next-handler]
    (fn handler [fileset]
      (let [;; module (if unit-test "default" (get-module-name fileset false))
            module (get-module-name fileset false)
            target-middleware (comp
                               (builtin/sift :move {#"(.*clj$)" (str classes-dir "/$1")})
                               (builtin/sift :move {#"(.*\.class$)" (str classes-dir "/$1")})
                               )
            target-handler (target-middleware next-handler)]
        (target-handler fileset)))))

(boot/deftask build
  "Configure and build servlet or service app"
  [k keep bool "keep intermediate .clj and .edn files"
   p prod bool "production build, without reloader"
   s servlet bool "build a servlet-based app DEPRECATED"
   v verbose bool "verbose"]
  (let [keep (or keep false)
        verbose (or verbose false)]
        ;; mod (str (-> (boot/get-env) :gae :module :name))]
    ;; (println "MODULE: " mod)
    (comp (install-sdk)
          (libs :verbose verbose)
          ;; (builtin/javac :options ["-source" "1.7", "-target" "1.7"])
          (if prod identity (reloader :keep keep :servlet servlet :verbose verbose))
          (filters :keep keep :verbose verbose)
          (servlets :keep keep :verbose verbose)
          (logging :verbose verbose)
          (webxml :verbose verbose)
          (appengine :verbose verbose)
          (builtin/sift :move {#"(.*clj$)" (str classes-dir "/$1")})
          (builtin/sift :move {#"(.*\.class$)" (str classes-dir "/$1")})
          (if servlet
            identity
            (comp
             (builtin/pom)
             (builtin/jar)))
          #_(target :servlet servlet :verbose verbose)
          #_(builtin/sift :include #{(re-pattern (str mod ".*jar"))})
          #_(builtin/install)
          #_(target :verbose verbose)
          ;; (builtin/sift :move {#"(^.*)" (str mod "/$1")})
          ;; FIXME: use cache instead?
          ;; (builtin/target :dir #{(str "target/" mod)})
          )))

(boot/deftask cache
  "control cache: retrieve, save, clean"
  [c clean bool "clean config - clear cache first"
   t trace bool "dump cache to stdout if verbose=true"
   r retrieve bool "retrieve cached master edn config file and sync to fileset (default)"
   s save bool "save master edn config file from fileset to cache"
   v verbose bool "verbose"]
  ;; default to retrieve
  (if (and retrieve save)
    (util/exit-error
     (util/fail "boot-gae/cache: only one of :retrieve and :save may be specified\n")))
  (let [retrieve (or retrieve (not save))
        save (or (not retrieve) save)]
    (boot/with-pre-wrap fileset
      (let [cache-dir (boot/cache-dir! :boot-gae/build)]
        (if verbose (println "cache-dir: " (.getPath cache-dir)))
        (if clean
          (do (if verbose (util/info (str "Clearing boot-gae cache\n")))
              (boot/empty-dir! cache-dir)
              (boot/commit! fileset))
          (if retrieve
            (do (println "RETRIEVING " (type cache-dir))
                (let [;;fs (boot/add-cached-asset fileset (io/file "boot-gae/build") identity)
                      fs (boot/add-asset fileset cache-dir)]
                  #_(println "FS: " fs)
                  (boot/commit! fs)))
            (if save
              (let [_ (println "SAVING")
                    fs (into [] (boot/output-dirs fileset))]
                ;;(println "fs: " fs (type fs))
                (apply boot/sync! cache-dir fs)
                fileset)
              fileset)))))))

(boot/deftask config-service
  "generate xml config files for service war"
  [d dir DIR str "output dir"
   c config-syms SYMS #{sym} "namespaced symbols bound to meta-config data"
   k keep bool "keep intermediate .clj files"
   u unit-test bool "configure for unit testing (module = 'default')"
   v verbose bool "Print trace messages."]
  (comp
   (webxml :verbose verbose)
   (appengine :unit-test unit-test :verbose verbose)))

(boot/deftask config-app
  "generate xml config files for microservices app"
  [d dir DIR str "output dir"
   c config-syms SYMS #{sym} "namespaced symbols bound to meta-config data"
   k keep bool "keep intermediate .clj files"
   v verbose bool "Print trace messages."]
  (let [workspace (boot/tmp-dir!)
        prev-pre (atom nil)
        odir (if dir dir meta-inf-dir)]
    (boot/with-pre-wrap [fileset]
      (let [appengine-config-edn-files (->> (boot/fileset-diff @prev-pre fileset)
                                            boot/input-files
                                            (boot/by-re [(re-pattern (str appengine-edn "$"))]))
            appengine-config-edn-f (condp = (count appengine-config-edn-files)
                                     0 (throw (Exception. appengine-edn " file not found"))
                                     1 (first appengine-config-edn-files)
                                     (throw (Exception.
                                             (str "Only one " appengine-edn " file allowed; found "
                                                  (count appengine-config-edn-files)))))

            appengine-cfg (-> (boot/tmp-file appengine-config-edn-f) slurp read-string)
            ;; path     (boot/tmp-path appengine-config-edn-f)
            ;; in-file  (boot/tmp-file appengine-config-edn-f)
            ;; out-file (io/file workspace path)
            appengine-application-cfg (stencil/render-file
                                       "migae/boot_gae/appengine-application.mustache"
                                       appengine-cfg)
            services-app-cfg (stencil/render-file
                             "migae/boot_gae/services-app.mustache"
                             (-> appengine-cfg
                                 (assoc-in [:services]
                                           (conj (seq (:services appengine-cfg))
                                                 {:name "default"}))))

            manifest-cfg (stencil/render-file
                          "migae/boot_gae/MANIFEST.MF.mustache"
                          appengine-cfg)]
        (if verbose
          (do (println appengine-application-cfg)
              (println services-app-cfg)
              (println manifest-cfg)))
        (let [appengine-application-out-path (str odir "/appengine-application.xml")
              appengine-application-out-file (doto (io/file workspace appengine-application-out-path)
                                               io/make-parents)
              services-app-out-path (str odir "/application.xml")
              services-app-out-file (doto (io/file workspace services-app-out-path) io/make-parents)
              manifest-out-path (str odir "/MANIFEST.MF")
              manifest-out-file (doto (io/file workspace manifest-out-path) io/make-parents)
              ]
          (if verbose (util/info "Configuring services application\n"))
          (spit appengine-application-out-file appengine-application-cfg)
          (spit services-app-out-file services-app-cfg)
          (spit manifest-out-file manifest-cfg)))
      (-> fileset ;; (boot/new-fileset)
          (boot/add-resource workspace)
          boot/commit!))))

(boot/deftask deploy
  "Installs a new version of the application onto the server, as the default version for end users."
  ;; options from AppCfg.java, see also appcfg.sh --help
  [s server SERVER str "--server"
   _ service SERVICE str "deploy a service component"
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
  (if verbose (println "TASK: boot-gae/deploy"))
  (validate-tools-api-jar)
  ;; (println "PARAMS: " *opts*)

  (fn middleware [next-handler]
    (fn handler [fileset]
      (let [target-dir (get-target-dir fileset false)
            mod      (-> (boot/get-env) :gae :module :name)
            app-dir  (-> (boot/get-env) :gae :app :dir)

            opts (merge {:sdk-root (:sdk-root config-map)
                         :build-dir (get-target-dir fileset false)}
                        ;;(str "target/" (if service mod))}
                        ;; build-dir build-dir (gae-app-dir))} ;; (:build-dir config-map)}
                        *opts*)
            _ (println "OPTS: " opts)
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
            app-cfg (Class/forName appcfg-class true class-loader)
            target-middleware identity
            target-handler (target-middleware next-handler)]
        ;; def appCfg = classLoader.loadClass(APPENGINE_TOOLS_MAIN)
        ;; appCfg.main(params as String[])
        (def method (first (filter #(= (. % getName) "main") (. app-cfg getMethods))))
        (def invoke-args (into-array Object [params]))
        (if verbose ;; (or (:verbose *opts*) (:verbose config-map))
          (do (println "CMD: AppCfg")
              (doseq [arg params]
                (println "\t" arg))))
        (. method invoke nil invoke-args)
        (target-handler fileset)))))


(defn- normalize-filter-configs
  [configs]
  {:filters
   (vec (flatten (for [config (:filters configs)]
          (let [urls (into [] (for [url (:urls config)]
                                {:path (str url)}))
                ns (if (:ns config) (:ns config) (:class config)) ]
            (merge config {:urls urls
                           :filter {:ns ns}})))))})

;; (boot/deftask filters
;;   "generate filters class files"

;;   [k keep bool "keep intermediate .clj files"
;;    n gen-filters-ns NS str "namespace to generate and aot; default: 'filters"
;;    w web-inf WEB-INF str "WEB-INF dir, default: WEB-INF"
;;    v verbose bool "Print trace messages."]
;;   (let [edn-tmp-dir (boot/tmp-dir!)
;;         prev-pre (atom nil)
;;         ;; config-sym (if config-sym config-sym 'filters/config)
;;         ;; config-ns (symbol (namespace config-sym))
;;         web-inf (if web-inf web-inf web-inf-dir)
;;         filters-edn "filters.edn"
;;         gen-filters-tmp-dir (boot/tmp-dir!)
;;         gen-filters-ns (if gen-filters-ns (symbol gen-filters-ns) (gensym "filtersgen"))
;;         gen-filters-path (str gen-filters-ns ".clj")]
;;     (comp
;;      (boot/with-pre-wrap [fileset]
;;        (let [boot-config-edn-files (->> (boot/fileset-diff @prev-pre fileset)
;;                                     boot/input-files
;;                                     (boot/by-re [(re-pattern (str boot-config-edn "$"))]))
;;              boot-config-edn-f (condp = (count boot-config-edn-files)
;;                              0 (do (if verbose (util/info (str "Creating " boot-config-edn "\n")))
;;                                    (io/file boot-config-edn)) ;; this creates a java.io.File
;;                              1 (first boot-config-edn-files)  ;; this is a boot.tmpdir.TmpFile
;;                              (throw (Exception.
;;                                      (str "only one " boot-config-edn " file allowed; found "
;;                                           (count boot-config-edn-files)))))
;;              boot-config-edn-map (if (instance? boot.tmpdir.TmpFile boot-config-edn-f)
;;                                    (-> (boot/tmp-file boot-config-edn-f) slurp read-string)
;;                                    {})]

;;          ;; (if (> (count boot-config-edn-files) 1)
;;          ;;   (throw (Exception. "only one _boot_config.edn file allowed")))
;;          ;; (if (= (count boot-config-edn-files) 0)
;;          ;;   (throw (Exception. "cannot find _boot_config.edn")))

;;          ;; (let [boot-config-edn-f (first boot-config-edn-files)
;;          ;;       boot-config-edn-map (-> (boot/tmp-file boot-config-edn-f) slurp read-string)]
;;            (if (:filters boot-config-edn-map)
;;              fileset
;;              (do
;;                ;; step 0: read the edn files
;;                (let [filters-edn-files (->> (boot/input-files fileset)
;;                                              (boot/by-name [filters-edn]))
;;                      filters-edn-f (condp = (count filters-edn-files)
;;                                         0 (throw (Exception. (str "Cannot find " filters-edn " file.")))
;;                                         1 (first filters-edn-files)
;;                                         ;; > 1
;;                                         (throw (Exception.
;;                                                 (str "Only one " filters-edn "file allowed; found "
;;                                                      (count filters-edn-files)))))
;;                      filters-edn-map (-> (boot/tmp-file filters-edn-f) slurp read-string)
;;                      filters-config-map (normalize-filter-configs filters-edn-map)
;;                      filters-config-map {:filters (filter #(nil? (:class %))
;;                                                             (:filters filters-config-map))}

;;                ;; (let [filters-edn-files (->> (boot/input-files fileset)
;;                ;;                              (boot/by-name [filters-edn]))]
;;                ;;   (if (> (count filters-edn-files) 1)
;;                ;;     (throw (Exception. "only one filters.edn file allowed")))
;;                  ;; (if (= (count filters-edn-files) 0)
;;                  ;;   (throw (Exception. "cannot find filters.edn")))
;;                  (let [edn-filters-f (first filters-edn-files)
;;                        filter-configs (-> (boot/tmp-file edn-filters-f) slurp read-string)
;;                        smap (-> boot-config-edn-map (assoc-in [:filters]
;;                                                         (:filters filter-configs)))
;;                        boot-config-edn-s (with-out-str (pp/pprint smap))]
;;                    ;; step 1:  inject filter config stanza to _boot_config.edn
;;                    (let [path     (boot/tmp-path boot-config-edn-f)
;;                          boot-config-edn-in-file  (boot/tmp-file boot-config-edn-f)
;;                          boot-config-edn-out-file (io/file edn-tmp-dir path)]
;;                      (io/make-parents boot-config-edn-out-file)
;;                      (spit boot-config-edn-out-file boot-config-edn-s))

;;                    ;; step 2: gen filters
;;                    (let [gen-filters-content (stencil/render-file "migae/boot_gae/gen-filters.mustache"
;;                                                                    (assoc filter-configs
;;                                                                           :gen-filters-ns
;;                                                                           gen-filters-ns))
;;                          gen-filters-out-file (doto
;;                                                    (io/file gen-filters-tmp-dir gen-filters-path)
;;                                                  io/make-parents)]
;;                      (spit gen-filters-out-file gen-filters-content))))))) ;;)
;;        (util/info "Configuring filters...\n")

;;        ;; step 3: commit files to fileset
;;        (reset! prev-pre
;;                (-> fileset
;;                    (boot/add-source edn-tmp-dir)
;;                    (boot/add-source gen-filters-tmp-dir)
;;                    boot/commit!)))
;;      (builtin/aot :namespace #{gen-filters-ns}) ;; :dir (str web-inf "/classes"))
;;      (if keep identity
;;        (builtin/sift :include #{(re-pattern (str gen-filters-ns ".*.class"))}
;;                      :invert true))
;;      (if keep
;;        (comp
;;         (builtin/sift :to-resource #{(re-pattern boot-config-edn)})
;;         (builtin/sift :to-asset #{(re-pattern gen-filters-path)}))
;;        identity)
;;      )))

(defn- install-jarfile-internal
  [jarname]
  "Install jarfile from fileset"
  (fn middleware [next-handler]
    (fn handler [fileset]
      (let [jar-tmpfile (first (->> (boot/output-files fileset)
                                    (boot/by-re [(re-pattern (str ".*" jarname))])))
            jar-jfile (boot/tmp-file jar-tmpfile)
            jar-path (.getCanonicalPath jar-jfile)
            jar-name (.getName jar-jfile)
            target-middleware (comp
                               (builtin/install :file jar-path)
                               (builtin/sift :to-source #{(re-pattern (str ".*META-INF.*"))})
                               (builtin/sift :to-source #{(re-pattern (str ".*" jar-name))}))
            target-handler (target-middleware next-handler)]
        (target-handler fileset)))))

(boot/deftask install-service
  "Install service component"
  [p project PROJECT sym "project name"
   r version VERSION str "project version string"
   v verbose bool "Print trace messages"]
  (fn middleware [next-handler]
    (fn handler [fileset]
      (let [e (boot/get-env)
            ;; target-dir (get-target-dir fileset false)
            jarname (util/jarname project version)
            ;; jarpath (str target-dir "/" jarname)
            target-middleware (comp
                               (builtin/pom)
                               (builtin/jar)
                               (install-jarfile-internal jarname))
            target-handler (target-middleware next-handler)]
        (target-handler fileset)))))

(boot/deftask install-sdk
  "Unpack and install the SDK zipfile"
  [r release SDKREL str "SDK verion"
   v verbose bool "Print trace messages"]
  ;;NB: java property expected by kickstart is "appengine.sdk.root"
  ;; (print-task "install-sdk" *opts*)
  (let [release (or release "RELEASE")
        coords (vector 'com.google.appengine/appengine-java-sdk
                       "RELEASE"
                       :extension "zip")
        jar-path (pod/resolve-dependency-jar (boot/get-env) coords)
        sdk-dir (io/as-file (:sdk-root config-map))
        prev        (atom nil)]
    (boot/with-pre-wrap fileset
      (if (.exists sdk-dir)
        (do
          (let [file-sep (System/getProperty "file.separator")
                tools-api-jar (str/join file-sep [(:sdk-root config-map) "lib" "appengine-tools-api.jar"])]
            (if (not (.exists (io/as-file tools-api-jar)))
              (do
                (println (str "Found sdk-dir " sdk-dir " but not its contents; re-exploding"))
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

(boot/deftask keep-config
  "Retain master config file"
  []
  (builtin/sift :to-resource #{(re-pattern (str ".*" boot-config-edn))}))

;; FIXME:  for dev phase, we want to include deps in test scope
(boot/deftask libs
  "Install dependency jars in WEB-INF/lib"
  [v verbose bool "Print trace messages."]
  (let [checkout-vec (-> (boot/get-env) :checkouts)
        cos (map #(assoc (apply hash-map %) :coords [(first %) (second %)]) checkout-vec)

        dfl-scopes #{"compile" "runtime" "provided"}
        scopes dfl-scopes
        ;; scopes     (-> dfl-scopes
        ;;                (set/union include-scope)
        ;;                (set/difference exclude-scope))
        scope?     #(contains? scopes (:scope (util/dep-as-map %)))
        checkouts  (->> (boot/get-checkouts)
                        (filter (comp scope? :dep val))
                        (into {}))
        cache      (boot/cache-dir! ::uber :global true)
        ]
    (if (empty? cos)
      (do
        ;;(println "NO CHECKOUTS")
          (comp
           (builtin/uber :as-jars true :exclude-scope #{"provided"})
           ;; (builtin/sift :include #{#"zip$"} :invert true)
           ;; (builtin/sift :include #{#".*appengine-api-.*jar$"} :invert true)
           ;; (builtin/sift :include #{#".*jar$"})
           (builtin/sift :move {#"(.*\.jar$)" (str lib-dir "/$1")})))
      (boot/with-pre-wrap [fileset]
        ;;(println "CHECKOUTS")
        (let [tmpdir     (boot/tmp-dir!)]
          (doseq [co cos]
            (let [mod (if (:default co) "default"
                          (if-let [mod (:module co)]
                            mod "default"))
                  coords (:coords co)
                  pod-env (update-in (dissoc (boot/get-env) :checkouts)
                                     [:dependencies] #(identity %2)
                                     (concat '[[boot/core "2.7.1"] ;; ~(str boot-version)]
                                               [boot/pod "2.7.1"]] ;; ~(str boot-version)]]
                                             (vector coords)))
                  pod (future (pod/make-pod pod-env))]
              (if verbose (println "MODULE: " mod))
              (pod/with-eval-in @pod
                (require '[boot.pod :as pod] '[boot.util :as util]
                         '[boot.core :as boot] '[boot.file :as file]
                         '[boot.task.built-in :as builtin] '[boot.from.digest :as digest]
                         '[clojure.java.io :as io])
                (let [dfl-scopes #{"compile" "runtime" "test"}
                      scopes dfl-scopes
                      scope? #(contains? scopes (:scope (util/dep-as-map %)))
                      co-jar (pod/resolve-dependency-jar (boot/get-env) '~coords)
                      jars   (remove #(= (str co-jar) (.getPath %))
                                     (-> pod/env
                                         (update-in [:dependencies] (partial filter scope?))
                                         (pod/resolve-dependency-jars)))]
                  (doseq [jar jars]
                    (let [hash (digest/md5 jar)
                          name (str hash "-" (.getName jar))
                          tmpjar-path (str ~mod "/WEB-INF/lib/" (.getName jar))
                          cached-jar  (io/file ~(.getPath cache) hash)]
                      (when-not (.exists cached-jar)
                        (if ~verbose (util/info "Caching jar %s...\n" name))
                        (file/copy-atomically jar cached-jar))
                      (if ~verbose (util/info "Adding cached jar %s...\n" tmpjar-path))
                      (file/hard-link cached-jar (doto (io/file ~(.getPath tmpdir) tmpjar-path)
                                                   io/make-parents))))))))
          (boot/commit! (boot/add-resource fileset tmpdir)))))))

                  ;; (recur (rest jars)
                  ;;        fs

              ;;     ;;(println "dest: " dest)
              ;;     (file/hard-link jar dest)
              ;;     #_(file/copy-atomically jar dest)))
              ;; ;;~(buber out-dir) ;; :as-jars true :exclude-scope #{"provided"})
              ;; ))))))

            ;; #_(pod/copy-dependency-jar-entries pod/env
            ;;                                    ~(.getPath out-dir)
            ;;                                    '~coords
            ;;                                    #"^.*clj$"
            ;;                                    )))))))

    ;; #_(comp
    ;;  (builtin/uber :as-jars true :exclude-scope #{"provided"})
    ;;  ;; (builtin/sift :include #{#"zip$"} :invert true)
    ;;  ;; (builtin/sift :include #{#".*appengine-api-.*jar$"} :invert true)
    ;;  (builtin/sift :move {#"(.*\.jar$)" (str mod "/" lib-dir "/$1")})
    ;;  (builtin/sift :include #{#".*jar$"}))))

;; FIXME: drive this from logging.edn
(boot/deftask logging
  "configure gae logging; default: log4j"
  [;;l log LOG kw ":log4j or :jul"
   j jul bool "use java.util.logging instead of default log4j"
   v verbose bool "Print trace messages."
   o odir ODIR str "output dir"]
  ;; (print-task "logging" *opts*)
  (fn middleware [next-handler]
    (fn handler [fileset]
      (if verbose (util/info (format "Configuring logging for %s\n" (if jul "jul" "log4j"))))
      (let [workspace (boot/tmp-dir!)
            log4j-edn-files (->> fileset
                                 boot/input-files
                                 (boot/by-re [(re-pattern (str log4j-edn "$"))]))
            log4j-edn-f (condp = (count log4j-edn-files)
                          0 (throw (Exception. (str log4j-edn " file not found")))
                          1 (first log4j-edn-files)
                          (throw (Exception.
                                  (str "Only one " log4j-edn " file allowed; found "
                                       (count log4j-edn-files)))))
            log4j-cfg (-> (boot/tmp-file log4j-edn-f) slurp read-string)

            jul-edn-files (->> fileset
                               boot/input-files
                               (boot/by-re [(re-pattern (str jul-edn "$"))]))
            jul-edn-f (condp = (count jul-edn-files)
                        0 (throw (Exception. (str jul-edn " file not found")))
                        1 (first jul-edn-files)
                        (throw (Exception.
                                (str "Only one " jul-edn " file allowed; found "
                                     (count jul-edn-files)))))
            jul-cfg (-> (boot/tmp-file jul-edn-f) slurp read-string)

            boot-config-edn-files (->> fileset
                                       boot/input-files
                                       (boot/by-re [(re-pattern (str boot-config-edn "$"))]))
            boot-config-edn-f (condp = (count boot-config-edn-files)
                                0 (throw (Exception. (str boot-config-edn " file not found")))
                                ;; 0 (do (util/info (str "Creating " boot-config-edn "\n"))
                                ;;       (io/file boot-config-edn)) ;; this creates a java.io.File
                                1 (first boot-config-edn-files)  ;; this is a boot.tmpdir.TmpFile
                                (throw (Exception.
                                        (str "Only one " boot-config-edn " file allowed, found "
                                             (count boot-config-edn-files)))))
            boot-config-edn-map (if (instance? boot.tmpdir.TmpFile boot-config-edn-f)
                                  (-> (boot/tmp-file boot-config-edn-f) slurp read-string)
                                  {})

            content (stencil/render-file
                     (if jul
                       "migae/boot_gae/logging.properties.mustache"
                       "migae/boot_gae/log4j.properties.mustache")
                     (merge boot-config-edn-map (if jul jul-cfg log4j-cfg)))

            odir (if odir odir (if jul web-inf-dir classes-dir))

            out-path (if jul "logging.properties" "log4j.properties")

            mv-arg {(re-pattern out-path) (str odir "/$1")}

            target-middleware identity
            target-handler (target-middleware next-handler)

            out-file (doto (io/file workspace (str odir "/" out-path)) io/make-parents)]

        (spit out-file content)
        (target-handler (-> fileset (boot/add-resource workspace) boot/commit!))))))

(boot/deftask monitor
  "watch etc. for gae project"
  [d dir DIR str "target dir"
   s servlet bool "servlet"
   u unit-test bool "monitor unit test config"
   v verbose bool "verbose"]
  (fn middleware [next-handler]
    (fn handler [fileset]
      (let [servlet true
            target-dir (get-target-dir fileset servlet)
            target-dir (if unit-test "target/default" (if dir (str dir "/" target-dir) target-dir))
            _ (println "TARGET DIR: " target-dir)
            target-middleware (comp (builtin/watch)
                                    (builtin/notify :audible true)
                                    (builtin/sift :move {#"(.*\.clj$)" (str classes-dir "/$1")})
                                    (if servlet
                                      (target :no-clean true :servlet servlet)
                                      (target :no-clean true :servlet servlet :dir target-dir)))
            target-handler (target-middleware next-handler)]
        (target-handler fileset)))))

(boot/deftask rollback
  "appcfg rollback"
  [v verbose bool "Print trace messages."]
  )

(boot/deftask reloader
  "generate reloader servlet filter"
  [k keep bool "keep intermediate .clj files"
   m module MODULE str "module pfx"
   n gen-reloader-ns NS sym "ns for gen-reloader"
   r reloader-impl-ns NS sym "ns for reloader"
   s servlet bool "build as servlet"
   u unit-test bool "configure for unit testing"
   w web-inf WEB-INF str "WEB-INF dir, default: WEB-INF"
   v verbose bool "Print trace messages."]
  (let [workspace (boot/tmp-dir!)
        prev-pre (atom nil)
        web-inf (if web-inf web-inf web-inf-dir)
        gen-reloader-ns (if gen-reloader-ns (symbol gen-reloader-ns) (gensym "reloadergen"))
        gen-reloader-path (str gen-reloader-ns ".clj")
        ;;reloader-impl-path (str reloader-impl-ns ".clj")
        ]
    (comp
     (boot/with-pre-wrap [fileset]
       (let [boot-config-edn-files (->> (boot/fileset-diff @prev-pre fileset)
                                        boot/input-files
                                        (boot/by-re [(re-pattern (str boot-config-edn "$"))]))
             ;; (boot/by-name [boot-config-edn]))
             boot-config-edn-f (condp = (count boot-config-edn-files)
                                 0 (do (util/info (str "Creating " boot-config-edn "\n"))
                                       (io/file boot-config-edn)) ;; this creates a java.io.File
                                 1 (first boot-config-edn-files)  ;; this is a boot.tmpdir.TmpFile
                                 (throw (Exception.
                                         (str "only one _boot_config.edn file allowed, found "
                                              (count boot-config-edn-files)))))
             boot-config-edn-map (if (instance? boot.tmpdir.TmpFile boot-config-edn-f)
                                   (-> (boot/tmp-file boot-config-edn-f) slurp read-string)
                                   {})

             module (get-module-name fileset false)
             reloader-impl-path (str (if reloader-impl-ns
                                       reloader-impl-ns
                                       (str
                                        (str/replace module #"-" "_")
                                        "/reloader.clj")))
             reloader-name (str (str/replace module #"-" "_") ".reloader")
             reloader-impl-ns (if reloader-impl-ns (symbol reloader-impl-ns) (symbol (str module ".reloader")))

             ;;module (if unit-test "./" (get-module-name fileset false))
             #_(if-let [module (-> boot-config-edn-map :module :name)]
                      module
                      (let [appengine-config-edn-files
                            (->> (boot/fileset-diff @prev-pre fileset)
                                 boot/input-files
                                 (boot/by-re [(re-pattern (str appengine-edn "$"))]))

                            appengine-config-edn-f
                            (condp = (count appengine-config-edn-files)
                              0 (throw (Exception. appengine-edn " file not found"))
                              1 (first appengine-config-edn-files)
                              (throw (Exception.
                                      (str "Only one " appengine-edn " file allowed, found "
                                           (count appengine-config-edn-files)))))

                            appengine-config-edn-map
                            (-> (boot/tmp-file boot-config-edn-f) slurp read-string)]
                        (-> appengine-config-edn-map :module :name)))

             ]

         (if (:reloader boot-config-edn-map)
           fileset
           (do
             ;; (add-reloader! reloader-impl-ns urls in-file out-file)
             ;; step 1: create config map for reloader, inject into master config file
             (let [urls (flatten (concat (map #(% :urls) (:servlets boot-config-edn-map))))
                   m (-> boot-config-edn-map (assoc-in [:reloader]
                                                       {:ns reloader-impl-ns
                                                        :name "reloader"
                                                        :class (str (str/replace module #"-" "_") ".reloader")
                                                        :module (str/replace module #"-" "_")
                                                        :display {:name "Clojure reload filter"}
                                                        ;; :urls (if (empty? urls) [{:url "/*"}]
                                                        ;;           (vec urls))
                                                        :desc {:text "Clojure reload filter"}}))
                   boot-config-edn-s (with-out-str (pp/pprint m))
                   boot-config-edn-out-file (io/file workspace
                                                     (if (instance? boot.tmpdir.TmpFile boot-config-edn-f)
                                                       (boot/tmp-path boot-config-edn-f)
                                                       boot-config-edn-f))]
               (io/make-parents boot-config-edn-out-file)
               (spit boot-config-edn-out-file boot-config-edn-s))

             (let [reloader-impl-content (stencil/render-file "migae/boot_gae/reloader-impl.mustache"
                                                              {:reloader-ns reloader-impl-ns
                                                               :module module})

                   gen-reloader-content (stencil/render-file "migae/boot_gae/gen-reloader.mustache"
                                                             {:gen-reloader-ns gen-reloader-ns
                                                              :reloader-impl-ns reloader-impl-ns
                                                              :reloader-name reloader-name})
                   _ (if verbose (println "impl ns: " reloader-impl-ns))
                   _ (if verbose (println "class name: " reloader-name))
                   _ (if verbose (println "impl path: " reloader-impl-path))
                   ;; _ (if verbose (println "impl: " reloader-impl-content))
                   ;; _ (if verbose (println "gen: " gen-reloader-path))
                   ;; _ (if verbose (println "gen: " gen-reloader-content))

                   aot-tmp-dir (boot/tmp-dir!)
                   aot-out-file (doto (io/file aot-tmp-dir gen-reloader-path) io/make-parents)
                   impl-tmp-dir (boot/tmp-dir!)
                   impl-out-file (doto (io/file impl-tmp-dir reloader-impl-path) io/make-parents)]
               (spit aot-out-file gen-reloader-content)
               (spit impl-out-file reloader-impl-content)
               (if verbose (util/info "Configuring reloader\n"))
               (reset! prev-pre
                       (-> fileset
                           (boot/add-source workspace)
                           (boot/add-source aot-tmp-dir)
                           (boot/add-asset impl-tmp-dir)
                           boot/commit!)))))))
     (builtin/aot :namespace #{gen-reloader-ns})
     ;; keep gen reloader?
     (if keep identity
         (builtin/sift :include #{(re-pattern (str gen-reloader-ns ".*.class$"))}
                       :invert true))
     (if keep (builtin/sift :to-resource #{(re-pattern gen-reloader-path)})
         identity)
     (if keep (builtin/sift :to-resource #{(re-pattern ".*_boot_config.edn$")})
         identity)
     )))

;; (boot/deftask run
;;   "Run devappserver"
;;   [;; DevAppServerMain.java
;;    u unit-test bool "run in unit-testing config"
;;    _ sdk-server VAL str "--server"
;;    _ http-address VAL str "The address of the interface on the local machine to bind to (or 0.0.0.0 for all interfaces).  Default: 127.0.0.1"
;;    _ http-port VAL int "The port number to bind to on the local machine. Default: 8080"
;;    _ disable-update-check bool "Disable the check for newer SDK versions. Default: true"
;;    _ generated-dir DIR str "Set the directory where generated files are created."
;;    ;; GENERATED_DIR_PROPERTY = "appengine.generated.dir";
;;    _ default-gcs-bucket VAL str  "Set the default Google Cloud Storage bucket name."
;;    _ instance-port bool "--instance_port"
;;    _ disable-filesapi-warning bool "-disable_filesapi_warning"
;;    _ enable-filesapi bool "--enable_filesapi"

;;    ;; SharedMain.java
;;    _ sdk-root PATH str "--sdk_root"
;;    _ disable-restricted-check bool "--disable_restricted_check"
;;    _ external-resource-dir VAL str "--external_resource_dir"
;;    _ allow-remote-shutdown bool "--allow_remote_shutdown"
;;    a java-agent bool "use javaagaent (default: false).\n\t\tCAVEAT: setting to true may result in a dramatic increase in servlet startup time; setting false removes some security checks" ;; --no_java_agent

;;    ;; Kickstart.java
;;    _ address VAL str "address; default: localhost"
;;    _ generate-war bool "--generate_war"
;;    _ generated-war-dir PATH str "Set the directory where generated files are created."
;;    _ jvm-flags FLAG #{str} "--jvm_flags"
;;    _ start-on-first-thread bool "--startOnFirstThread"
;;    _ enable-jacoco bool "--enable_jacoco"
;;    _ jacoco-agent-jar VAL str"--jacoco_agent_jar"
;;    _ jacoco-agent-args VAL str"--jacoco_agent_args"
;;    _ jacoco-exec VAL str "--jacoco_exec"

;;    _ modules MODULES edn "modules map"

;;    s servlet bool "servlet app DEPRECATED"
;;    v verbose bool "verbose"]

;;   (boot/with-pre-wrap [fileset]
;;     (let [appengine-edn-fs (->> fileset
;;                                 boot/input-files
;;                                 (boot/by-name [appengine-edn]))

;;           appengine-edn-f (condp = (count appengine-edn-fs)
;;                             0 (throw (Exception. appengine-edn " file not found"))
;;                             1 (first appengine-edn-fs)
;;                             (throw (Exception. (str "Only one " appengine-edn " file allowed"))))

;;           appengine-config-map (-> (boot/tmp-file appengine-edn-f) slurp read-string)


;;           ;; (println "*OPTS*: " *opts*)
;;           args (->args *opts*)
;;           ;; _ (println "ARGS: " args)
;;           ;; jargs (into-array String (conj jargs "build/exploded-app"))

;;           target-dir (if unit-test "target/default" "target") ;; (get-target-dir fileset servlet))
;;           #_(if servlet "target"
;;                                     (if (-> appengine-config-map :services)
;;                                       "target/default"
;;                                       (str "target/" (-> appengine-config-map :module :name))))

;;           checkout-vec (-> (boot/get-env) :checkouts)
;;           cos (map #(assoc (apply hash-map %) :coords [(first %) (second %)]) checkout-vec)
;;           default-mod (filter #(or (:default %) (not (:module %))) cos)
;;           _ (if (> (count default-mod) 1)
;;               (throw (Exception. "Only one default module allowed" (count default-mod))))


;;           mod-ports (remove nil?
;;                             (into [] ;; (for [[mod port] (-> (boot/get-env) :gae :modules)]
;;                                   (map #(if (:module %)
;;                                           (str "--jvm_flag=-Dcom.google.appengine.devappserver_module."
;;                                                (:module %)
;;                                                ".port="
;;                                                (str (:port %)))
;;                                           nil)
;;                                        cos)))
;;                             ;; modules))

;;           _ (println "MOD PORTS: " mod-ports)

;;           http-port (or http-port (if-let [p (:port (first default-mod))] p nil))
;;           _ (println "HTTP-PORT: " http-port)

;;           jvm-flags (for [flag jvm-flags] (str "--jvm_flag=" flag))
;;           jargs (concat ["com.google.appengine.tools.development.DevAppServerMain"
;;                          (str "--sdk_root=" (:sdk-root config-map))
;;                          "--jvm_flag=-Dappengine.fullscan=1"]
;;                         mod-ports
;;                         jvm-flags
;;                         (if disable-restricted-check ["--disable_restricted_check"])
;;                         ;;(if (not java-agent) ["--no_java_agent"])
;;                         (if http-port [(str "--port=" http-port)])
;;                         (if http-address [(str "--address=" http-address)])
;;                         ;; KickStarter gets the abs path only for the last arg (the target dir)
;;                         ;; to support multiple services we need to call (.getAbsolutePath (io/file dir))
;;                         [target-dir])
;;           jargs (into-array String jargs)]

;;       (if verbose
;;         (do (println "jargs:")
;;             (doseq [a jargs] (println "\t" a))))
;;       (validate-tools-api-jar)
;;       ;; (println "system classpath: " (System/getenv "java.class.path"))

;;       (let [class-loader (-> (Thread/currentThread) (.getContextClassLoader))
;;             cl (.getParent class-loader)
;;             ;; _ (println "class-loader: " class-loader (type class-loader))
;;             kick-start (Class/forName "com.google.appengine.tools.KickStart" true class-loader)
;;             ]
;;         ;; (println "kick-start: " kick-start (type kick-start))
;;         (def method (first (filter #(= (. % getName) "main") (. kick-start getMethods))))
;;         ;;(let [parms (.getParameterTypes method)] (println "param types: " parms))
;;         (def invoke-args (into-array Object [jargs]))
;;         (. method invoke nil invoke-args)
;;         ))
;;     fileset))

(boot/deftask run
  "Run cloud devserver"
  [;; DevAppServerMain.java
   ;;w wardirs WARDIRS [str] "wardirs"
   u unit-test bool "run in unit-testing config"
   _ sdk-server VAL str "--server"
   _ http-address VAL str "The address of the interface on the local machine to bind to (or 0.0.0.0 for all interfaces).  Default: 127.0.0.1"
   _ http-port VAL int "The port number to bind to on the local machine. Default: 8080"

   _ generated-dir DIR str "Set the directory where generated files are created."
   ;; GENERATED_DIR_PROPERTY = "appengine.generated.dir";
   _ default-gcs-bucket VAL str  "Set the default Google Cloud Storage bucket name."
   _ instance-port bool "--instance_port"
   _ disable-filesapi-warning bool "-disable_filesapi_warning"
   _ enable-filesapi bool "--enable_filesapi"

   ;; SharedMain.java
   _ sdk-root PATH str "--sdk_root"
   _ disable-restricted-check bool "--disable_restricted_check"
   _ external-resource-dir VAL str "--external_resource_dir"
   _ allow-remote-shutdown bool "--allow_remote_shutdown"
   a java-agent bool "use javaagaent (default: false).\n\t\tCAVEAT: setting to true may result in a dramatic increase in servlet startup time; setting false removes some security checks" ;; --no_java_agent

   ;; Kickstart.java, DevAppServerMain.java
   _ generate-war bool "--generate_war"
   _ generated-war-dir PATH str "Set the directory where generated files are created."
   _ jvm-flags FLAG #{str} "--jvm_flags"
   _ start-on-first-thread bool "--startOnFirstThread"
   _ enable-jacoco bool "--enable_jacoco"
   _ jacoco-agent-jar VAL str"--jacoco_agent_jar"
   _ jacoco-agent-args VAL str"--jacoco_agent_args"
   _ jacoco-exec VAL str "--jacoco_exec"

   _ services SERVICES edn "services map, keys :service, :port"

   s servlet bool "servlet app DEPRECATED"
   v verbose bool "verbose"]

  (boot/with-pre-wrap [fileset]
    (let [;; (println "*OPTS*: " *opts*)
          args (->args *opts*)
          ;; _ (println "ARGS: " args)
          ;; jargs (into-array String (conj jargs "build/exploded-app"))

          ;;target-dir (if unit-test "target/default" "target") ;; (get-target-dir fileset servlet))
          #_(if servlet "target"
                                    (if (-> appengine-config-map :services)
                                      "target/default"
                                      (str "target/" (-> appengine-config-map :module :name))))

          checkout-vec (-> (boot/get-env) :checkouts)
          cos (map #(assoc (apply hash-map %) :coords [(first %) (second %)]) checkout-vec)
          default-mod (filter #(or (:default %) (not (:module %))) cos)
          _ (if (> (count default-mod) 1)
              (throw (Exception. "Only one default module allowed" (count default-mod))))

          ;; FIXME: get service name from config/appengine.edn for each service?
          service-ports (remove nil?
                            (into [] (map #(if (:name %)
                                             (str "--jvm_flag=-Dcom.google.appengine.devappserver_module."
                                                  (:name %)
                                                  ".port="
                                                  (str (:port %)))
                                             nil)
                                          services)))
                            ;; modules))

          wardirs (remove nil?
                          (into [] (map #(if (:wardir %)
                                           (.getAbsolutePath (io/file (:wardir %)))
                                           nil)
                                          services)))

          _ (println "WARDIRS: " wardirs)

          http-port (or http-port (if-let [p (:port (first default-mod))] p nil))
          _ (println "HTTP-PORT: " http-port)

          jvm-flags (for [flag jvm-flags] (str "--jvm_flag=" flag))
          ;;war-dirs (for [wardir wardirs] (.getAbsolutePath (io/file wardir)))

          jargs (concat ["com.google.appengine.tools.development.DevAppServerMain"
                         (str "--sdk_root=" (:sdk-root config-map))
                         ;; "--jvm_flag=-Dappengine.fullscan.seconds=1"
                         ]
                        service-ports
                        jvm-flags
                        (if disable-restricted-check ["--disable_restricted_check"])
                        ;; (if (not java-agent) ["--no_java_agent"])
                        (if http-port [(str "--port=" http-port)])
                        (if http-address [(str "--address=" http-address)])
                        wardirs)
          jargs (into-array String jargs)]

      (if verbose
        (do (println "jargs:")
            (doseq [a jargs] (println "\t" a))))
      (validate-tools-api-jar)
      ;; (println "system classpath: " (System/getenv "java.class.path"))

      (let [class-loader (-> (Thread/currentThread) (.getContextClassLoader))
            cl (.getParent class-loader)
            ;; _ (println "class-loader: " class-loader (type class-loader))
            kick-start (Class/forName "com.google.appengine.tools.KickStart" true class-loader)
            ]
        ;; (println "kick-start: " kick-start (type kick-start))
        (def method (first (filter #(= (. % getName) "main") (. kick-start getMethods))))
        ;;(let [parms (.getParameterTypes method)] (println "param types: " parms))
        (def invoke-args (into-array Object [jargs]))
        (. method invoke nil invoke-args)
        ))
    fileset))

(defn- normalize-servlet-configs
  [configs]
  {:servlets
   (vec (flatten (for [config (:servlets configs)]
          (let [urls (into [] (for [url (:urls config)]
                                {:path (str url)}))
                ns (if (:ns config) (:ns config) (:class config)) ]
            (merge config {:urls urls
                           :servlet {:ns ns}})))))})

(boot/deftask servlets
  "generate servlets class files"
  [k keep bool "keep intermediate .clj files"
   ;; d odir DIR str "output dir for generated class files"
   ;; c config-sym SYM sym "namespaced symbol bound to meta-config data"
   n gen-servlets-ns NS str "namespace to generate and aot; default: 'servlets"
   w web-inf WEB-INF str "WEB-INF dir, default: WEB-INF"
   v verbose bool "Print trace messages."]
  (let [workspace (boot/tmp-dir!)
        prev-pre (atom nil)
        ;; config-sym (if config-sym config-sym 'servlets/config)
        ;; config-ns (symbol (namespace config-sym))
        web-inf (if web-inf web-inf web-inf-dir)
        servlets-edn "servlets.edn"
        gen-servlets-tmp-dir (boot/tmp-dir!)
        gen-servlets-ns (if gen-servlets-ns (symbol gen-servlets-ns) (gensym "servletsgen"))
        gen-servlets-path (str gen-servlets-ns ".clj")]
    (comp
     (boot/with-pre-wrap [fileset]
       (let [boot-config-edn-files (->> (boot/fileset-diff @prev-pre fileset)
                                    boot/input-files
                                    (boot/by-re [(re-pattern (str boot-config-edn "$"))]))
             boot-config-edn-f (condp = (count boot-config-edn-files)
                             0 (do (if verbose (util/info (str "Creating " boot-config-edn "\n")))
                                   (io/file boot-config-edn)) ;; this creates a java.io.File
                             1 (first boot-config-edn-files)  ;; this is a boot.tmpdir.TmpFile
                             (throw (Exception.
                                     (str "only one " boot-config-edn " file allowed; found "
                                          (count boot-config-edn-files)))))
             boot-config-edn-map (if (instance? boot.tmpdir.TmpFile boot-config-edn-f)
                                   (-> (boot/tmp-file boot-config-edn-f) slurp read-string)
                                   {})]
         ;; (println "boot-config-edn-map: " boot-config-edn-map)

         (if (:servlets boot-config-edn-map)
             fileset
             (do
               ;; step 1: read the edn config file and construct map
               (let [servlets-edn-files (->> (boot/input-files fileset)
                                             (boot/by-name [servlets-edn]))
                     servlets-edn-f (condp = (count servlets-edn-files)
                                        0 (throw (Exception. (str "Cannot find " servlets-edn " file.")))
                                        1 (first servlets-edn-files)
                                        ;; > 1
                                        (throw (Exception.
                                                (str "Only one " servlets-edn "file allowed; found "
                                                     (count servlets-edn-files)))))
                     servlets-edn-map (-> (boot/tmp-file servlets-edn-f) slurp read-string)
                     servlets-config-map (normalize-servlet-configs servlets-edn-map)
                     servlets-config-map {:servlets (filter #(nil? (:class %))
                                                            (:servlets servlets-config-map))}

                     ;; step 2: inject servlets config map into master config map
                     master-config (-> boot-config-edn-map (assoc-in [:servlets]
                                                                     (:servlets servlets-config-map)))
                     master-config (with-out-str (pp/pprint master-config))
                     ;; step 3: create new master config file
                     boot-config-edn-out-file (io/file workspace
                                                       (if (instance? boot.tmpdir.TmpFile boot-config-edn-f)
                                                         (boot/tmp-path boot-config-edn-f)
                                                         boot-config-edn-f))
                     ;; step 4: create servlet generator
                     gen-servlets-content (stencil/render-file "migae/boot_gae/gen-servlets.mustache"
                                                               (assoc servlets-config-map
                                                                      :gen-servlets-ns
                                                                      gen-servlets-ns))
                     gen-servlets-out-file (doto (io/file gen-servlets-tmp-dir gen-servlets-path)
                                             io/make-parents)]
                 ;; step 5: write new files
                 (io/make-parents boot-config-edn-out-file)
                 (spit boot-config-edn-out-file master-config)
                 (spit gen-servlets-out-file gen-servlets-content)))))

       (if verbose (util/info "Configuring servlets...\n"))

       ;; step 6: commit files to fileset
       (reset! prev-pre
               (-> fileset
                   (boot/add-source workspace)
                   (boot/add-source gen-servlets-tmp-dir)
                   boot/commit!)))

     (builtin/aot :namespace #{gen-servlets-ns})

     (if keep
       identity
       (builtin/sift :include #{(re-pattern (str gen-servlets-ns ".*.class"))}
                     :invert true))
     (if keep
       (comp
        (builtin/sift :to-resource #{(re-pattern boot-config-edn)})
        (builtin/sift :to-asset #{(re-pattern gen-servlets-path)}))
       identity)
     )))

(boot/deftask filters
  "generate filters class files"
  [k keep bool "keep intermediate .clj files"
   ;; d odir DIR str "output dir for generated class files"
   ;; c config-sym SYM sym "namespaced symbol bound to meta-config data"
   n gen-filters-ns NS str "namespace to generate and aot; default: 'filters"
   w web-inf WEB-INF str "WEB-INF dir, default: WEB-INF"
   v verbose bool "Print trace messages."]
  (let [workspace (boot/tmp-dir!)
        prev-pre (atom nil)
        ;; config-sym (if config-sym config-sym 'filters/config)
        ;; config-ns (symbol (namespace config-sym))
        web-inf (if web-inf web-inf web-inf-dir)
        filters-edn "filters.edn"
        gen-filters-tmp-dir (boot/tmp-dir!)
        gen-filters-ns (if gen-filters-ns (symbol gen-filters-ns) (gensym "filtersgen"))
        gen-filters-path (str gen-filters-ns ".clj")]
    (comp
     (boot/with-pre-wrap [fileset]
       (let [boot-config-edn-files (->> (boot/fileset-diff @prev-pre fileset)
                                    boot/input-files
                                    (boot/by-re [(re-pattern (str boot-config-edn "$"))]))
             boot-config-edn-f (condp = (count boot-config-edn-files)
                             0 (do (if verbose (util/info (str "Creating " boot-config-edn "\n")))
                                   (io/file boot-config-edn)) ;; this creates a java.io.File
                             1 (first boot-config-edn-files)  ;; this is a boot.tmpdir.TmpFile
                             (throw (Exception.
                                     (str "only one " boot-config-edn " file allowed; found "
                                          (count boot-config-edn-files)))))
             boot-config-edn-map (if (instance? boot.tmpdir.TmpFile boot-config-edn-f)
                                   (-> (boot/tmp-file boot-config-edn-f) slurp read-string)
                                   {})]
         ;; (println "boot-config-edn-map: " boot-config-edn-map)

         (if (:filters boot-config-edn-map)
             fileset
             (do
               ;; step 1: read the edn config file and construct map
               (let [filters-edn-files (->> (boot/input-files fileset)
                                             (boot/by-name [filters-edn]))
                     filters-edn-f (condp = (count filters-edn-files)
                                        0 (throw (Exception. (str "Cannot find " filters-edn " file.")))
                                        1 (first filters-edn-files)
                                        ;; > 1
                                        (throw (Exception.
                                                (str "Only one " filters-edn "file allowed; found "
                                                     (count filters-edn-files)))))
                     filters-edn-map (-> (boot/tmp-file filters-edn-f) slurp read-string)
                     filters-config-map (normalize-filter-configs filters-edn-map)
                     filters-config-map {:filters (filter #(nil? (:class %))
                                                            (:filters filters-config-map))}

                     ;; step 2: inject filters config map into master config map
                     master-config (-> boot-config-edn-map (assoc-in [:filters]
                                                                     (:filters filters-config-map)))
                     master-config (with-out-str (pp/pprint master-config))
                     ;; step 3: create new master config file
                     boot-config-edn-out-file (io/file workspace
                                                       (if (instance? boot.tmpdir.TmpFile boot-config-edn-f)
                                                         (boot/tmp-path boot-config-edn-f)
                                                         boot-config-edn-f))
                     ;; step 4: create filter generator
                     gen-filters-content (stencil/render-file "migae/boot_gae/gen-filters.mustache"
                                                               (assoc filters-config-map
                                                                      :gen-filters-ns
                                                                      gen-filters-ns))
                     gen-filters-out-file (doto (io/file gen-filters-tmp-dir gen-filters-path)
                                             io/make-parents)]
                 ;; step 5: write new files
                 (io/make-parents boot-config-edn-out-file)
                 (spit boot-config-edn-out-file master-config)
                 (spit gen-filters-out-file gen-filters-content)))))

       (if verbose (util/info "Configuring filters...\n"))

       ;; step 6: commit files to fileset
       (reset! prev-pre
               (-> fileset
                   (boot/add-source workspace)
                   (boot/add-source gen-filters-tmp-dir)
                   boot/commit!)))

     (builtin/aot :namespace #{gen-filters-ns})

     (if keep
       identity
       (builtin/sift :include #{(re-pattern (str gen-filters-ns ".*.class"))}
                     :invert true))
     (if keep
       (comp
        (builtin/sift :to-resource #{(re-pattern boot-config-edn)})
        (builtin/sift :to-asset #{(re-pattern gen-filters-path)}))
       identity)
     )))

(boot/deftask target
  "target, using module name"
  [C no-clean bool "Don't clean target before writing project files"
   d dir DIR str "target dir"
   s servlet bool "building as servlet"
   v verbose bool "verbose"]
  (fn middleware [next-handler]
    (fn handler [fileset]
      (let [target-dir (or dir (get-target-dir fileset servlet))
            target-middleware (builtin/target :dir #{target-dir} :no-clean no-clean)
            target-handler (target-middleware next-handler)]
        (target-handler fileset)))))

(boot/deftask webxml
  "generate gae web.xml"
  [d dir DIR str "output dir"
   c config-syms SYMS #{sym} "namespaced symbols bound to meta-config data"
   k keep bool "keep intermediate .clj files"
   r reloader bool "install reloader filter"
   v verbose bool "Print trace messages."]
;;  (println "TASK: config-webapp")
  (let [workspace (boot/tmp-dir!)
        prev-pre (atom nil)
        odir (if dir dir web-inf-dir)]
    ;; (println "ODIR: " odir)
    (boot/with-pre-wrap fileset
       (let [boot-config-edn-files (->> (boot/fileset-diff @prev-pre fileset)
                                    boot/input-files
                                    (boot/by-re [(re-pattern (str boot-config-edn "$"))]))
             boot-config-edn-f (condp = (count boot-config-edn-files)
                             0 (do (if verbose (util/info (str "Creating " boot-config-edn "\n")))
                                   (io/file boot-config-edn)) ;; this creates a java.io.File
                             1 (first boot-config-edn-files)  ;; this is a boot.tmpdir.TmpFile
                             (throw (Exception.
                                     (str "only one " boot-config-edn " file allowed; found "
                                          (count boot-config-edn-files)))))
             boot-config-edn-map (if (instance? boot.tmpdir.TmpFile boot-config-edn-f)
                                   (-> (boot/tmp-file boot-config-edn-f) slurp read-string)
                                   {})
             path     (boot/tmp-path boot-config-edn-f)
             in-file  (boot/tmp-file boot-config-edn-f)
             out-file (io/file workspace path)

             webapp-edn-files (->> (boot/fileset-diff @prev-pre fileset)
                                        boot/input-files
                                        (boot/by-re [(re-pattern (str webapp-edn "$"))]))
             webapp-edn-f (condp = (count webapp-edn-files)
                            0 (throw (Exception. (str "Cannot find " webapp-edn " file.")))
                            1 (first webapp-edn-files)
                            (throw (Exception.
                                    (str "only one " webapp-edn " file allowed; found "
                                         (count webapp-edn-files)))))
             webapp-edn-map (-> (boot/tmp-file webapp-edn-f) slurp read-string)

             ;; inject webapp config map into master config map
             master-config (merge boot-config-edn-map webapp-edn-map)
             ;; _ (println "master config: " master-config)

             content (stencil/render-file
                      "migae/boot_gae/xml.web.mustache"
                      master-config)

             ;; step 3: create new master config file
             boot-config-edn-out-file (io/file workspace (boot/tmp-path boot-config-edn-f))

             xml-out-path (str odir "/web.xml")
             xml-out-file (doto (io/file workspace xml-out-path) io/make-parents)]

         ;; web.xml always
         ;; (if (.exists reloader)
         ;;   nop
         ;;   create empty reloader
         (if verbose (util/info "Configuring web.xml\n"))
         (io/make-parents boot-config-edn-out-file)
         (spit boot-config-edn-out-file master-config)
         (spit xml-out-file content))
      (-> fileset (boot/add-resource workspace) boot/commit!))))

;; (boot/deftask mods
;;   "modules"
;;   [k keep bool "keep intermediate .clj and .edn files"
;;    m module MODULE str "module dir"
;;    v verbose bool "verbose"]
;;   (println "*opts* " *opts*)
;;   (let [env (boot/get-env)
;;         assets (:asset-paths env)
;;         new-asset-paths (set (for [p assets] (str module "/" p)))
;;         resources (:resource-paths env)
;;         new-resource-paths (set (for [p resources] (str module "/" p)))
;;         sources (:source-paths env)
;;         new-source-paths (set (for [p sources] (str module "/" p)))
;;         tgt (:target-path env)
;;         new-target-path (str module "/" tgt)
;;         ]
;;     (println "env: ")
;;     (pp/pprint env)
;;     (println "new :asset-paths " new-asset-paths)
;;     (println "new :resource-paths " new-resource-paths)
;;     (println "new :source-paths " new-source-paths)
;;     (println "new :target-path " new-target-path)
;;     (let [mod-env (update-in (boot/get-env) [:asset-paths] (fn [old new] new) new-asset-paths)
;;           mod-env (update-in mod-env [:resource-paths] (fn [old new] new) new-resource-paths)
;;           mod-env (update-in mod-env [:source-paths] (fn [old new] new) new-source-paths)
;;           mod-env (update-in mod-env [:target-path] (fn [old new] new) new-target-path)
;;           mod-env (update-in mod-env [:dependencies] conj '[migae/boot-gae "0.1.0-SNAPSHOT" :scope "test"])
;;           mod-pod (future (pod/make-pod mod-env))]
;;       (pod/with-eval-in @mod-pod
;;         (require '[boot.core :as boot]
;;                  '[boot.pod :as pod]
;;                  '[migae.boot-gae :as gae])
;;         (println "pod :asset-paths " (:asset-paths pod/env))
;;         (println "pod :resource-paths " (:resource-paths pod/env))
;;         (println "pod :source-paths " (:source-paths pod/env))
;;         (println "pod :target-path " (:target-path pod/env))
;;         (let [tmp-dir (boot/tmp-dir!)
;;         prev-pre (atom nil)
;;         dir (if dir dir web-inf-dir)]
