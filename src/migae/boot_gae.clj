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
(def appstats-edn "appstats.edn")

;; for microservices app:
(def services-edn "services.edn")


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
   s servlet-app SERVLET bool "generate config for servlet-app instead of service component"
   v verbose bool "Print trace messages."]
  ;; boolean hasUppercase = !password.equals(password.toLowerCase());
  (let [tmp-dir (boot/tmp-dir!)
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

             appengine-config-map (assoc-in appengine-config-map
                                            [:module :name]
                                            (if servlet-app
                                              "default"
                                              (if (-> appengine-config-map :module)
                                                (if (-> appengine-config-map :module :default)
                                                  "default"
                                                  (if-let [m (-> appengine-config-map :module :name)]
                                                    m
                                                    (throw (Exception.
                                                            (str ":module :name required in " appengine-edn)))))
                                                (throw (Exception. (str ":module clause required in" appengine-edn))))))

             app-id (name (-> (boot/get-env) :gae :app :id))

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
             ]

         (println "appengine-config-map 2: " appengine-config-map)
         (let [content (stencil/render-file
                        "migae/templates/xml.appengine-web.mustache"
                        appengine-config-map)]
           ;;(if verbose (println content))
           (if verbose (util/info "Configuring appengine-web.xml\n"))
           (let [xml-out-path (str dir "/appengine-web.xml")
                 xml-out-file (doto (io/file tmp-dir xml-out-path) io/make-parents)
                 ]
             (spit xml-out-file content))))
    (-> fileset (boot/add-resource tmp-dir) boot/commit!))))

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

(boot/deftask appstats
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
         (println "boot-config-edn-map: " boot-config-edn-map)

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
  (let [tmpdir (boot/tmp-dir!)
        dfl-scopes #{"compile" "runtime" "provided"}
        scopes dfl-scopes
        ;; scopes     (-> dfl-scopes
        ;;                (set/union include-scope)
        ;;                (set/difference exclude-scope))
        scope?     #(contains? scopes (:scope (util/dep-as-map %)))
        checkouts  (->> (boot/get-checkouts)
                        (filter (comp scope? :dep val))
                        (into {}))
        co-jars    (->> checkouts (map (comp :jar val)))
        co-dirs    (->> checkouts (map (comp :dir val)))

        checkout-vec (-> (boot/get-env) :checkouts)
        cos (map #(assoc (apply hash-map %) :coords [(first %) (second %)]) checkout-vec)
        ]
    (comp
     (earxml)
     (boot/with-pre-wrap [fileset]
       (doseq [co cos]
         (let [mod (if (:default co) "default" (if-let [mod (:module co)] mod "default"))]
           (let [corec (get checkouts (first (:coords co)))
                 co-dir (:dir corec)
                 jar-path (.getPath (:jar corec))
                 out-dir (doto (io/file tmpdir mod) io/make-parents)]
             ;; (println "co-dir: " co-dir)
             ;; (println "out-dir: " out-dir)
             (fs/copy-dir co-dir out-dir))))
       (-> fileset
           (boot/add-asset tmpdir :exclude #{(re-pattern (str "/META-INF/.*"))})
           (boot/commit!)))
     (target)
     )))

(boot/deftask build
  "Configure and build servlet or service app"
  [k keep bool "keep intermediate .clj and .edn files"
   p prod bool "production build, without reloader"
   s service bool "build a service"
   v verbose bool "verbose"]
  (let [keep (or keep false)
        verbose (or verbose false)]
        ;; mod (str (-> (boot/get-env) :gae :module :name))]
    ;; (println "MODULE: " mod)
    (comp (install-sdk)
          (libs :verbose verbose)
          (appstats :verbose verbose)
          ;; (builtin/javac :options ["-source" "1.7", "-target" "1.7"])
          (if prod identity (reloader :keep keep :service service :verbose verbose))
          (filters :keep keep :verbose verbose)
          (servlets :keep keep :verbose verbose)
          (logging :verbose verbose)
          (webxml :verbose verbose)
          (appengine :verbose verbose)
          (builtin/sift :move {#"(.*clj$)" (str classes-dir "/$1")})
          (builtin/sift :move {#"(.*\.class$)" (str classes-dir "/$1")})
          (if service
            (comp
             (builtin/pom)
             (builtin/jar))
            identity)
          (target :service service :verbose verbose)
          #_(builtin/sift :include #{(re-pattern (str mod ".*jar"))})
          #_(builtin/install)
          #_(target :verbose verbose)
          ;; (builtin/sift :move {#"(^.*)" (str mod "/$1")})
          ;; FIXME: use cache instead?
          ;; (builtin/target :dir #{(str "target/" mod)})
          )))

(boot/deftask target
  "target, using module name"
  [C no-clean bool "Don't clean target before writing project files"
   m monitor bool "monitoring - target service assembly"
   s service bool "building as service"
   v verbose bool "verbose"]
  (let [mod      (-> (boot/get-env) :gae :module :name)
        app-dir  (-> (boot/get-env) :gae :app :dir)
        dir      (if monitor
                   (str app-dir "/target/" mod)
                   (if service (str "target/" mod)
                       "target"))]
    (if service
      (if (not (and mod app-dir))
        (throw (Exception. "For service targets, both :name and :app :dir must be specified in :gae map of build.boot"))))
    (println "TARGET DIR: " dir)
    (builtin/target :dir #{dir}
                    :no-clean (or no-clean false))))

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

(boot/deftask earxml
  "generate xml config files for microservices app"
  [d dir DIR str "output dir"
   c config-syms SYMS #{sym} "namespaced symbols bound to meta-config data"
   k keep bool "keep intermediate .clj files"
   v verbose bool "Print trace messages."]
  (let [edn-tmp (boot/tmp-dir!)
        prev-pre (atom nil)
        odir (if dir dir meta-inf-dir)]
    ;; FIXME create services.edn map from :checkouts
    (boot/with-pre-wrap fileset
      (let [services-fs (->> (boot/fileset-diff @prev-pre fileset)
                                boot/input-files
                                (boot/by-re [(re-pattern (str services-edn "$"))]))]
            ;; (boot/by-name [services]))]
        (if (> (count services-fs) 1) (throw (Exception. "only one services.edn file allowed")))
        (if (= (count services-fs) 0) (throw (Exception. "services.edn file not found")))

        (let [services-f (first services-fs)
              services-cfg (-> (boot/tmp-file services-f) slurp read-string)
              path     (boot/tmp-path services-f)
              in-file  (boot/tmp-file services-f)
              out-file (io/file edn-tmp path)]
          (let [appengine-application-cfg (stencil/render-file
                                           "migae/templates/xml.appengine-application.mustache"
                                           services-cfg)
                application-cfg (stencil/render-file
                                 "migae/templates/xml.application.mustache"
                                 services-cfg)
                manifest-cfg (stencil/render-file
                              "migae/templates/MANIFEST.MF.mustache"
                              services-cfg)]
            (if verbose
              (do (println appengine-application-cfg)
                  (println application-cfg)
                  (println manifest-cfg)))
            (let [appengine-application-out-path (str odir "/appengine-application.xml")
                  appengine-application-out-file (doto
                                                     (io/file edn-tmp appengine-application-out-path)
                                                   io/make-parents)
                  application-out-path (str odir "/application.xml")
                  application-out-file (doto (io/file edn-tmp application-out-path)
                                         io/make-parents)
                  manifest-out-path (str odir "/MANIFEST.MF")
                  manifest-out-file (doto (io/file edn-tmp manifest-out-path)
                                      io/make-parents)
                  ]
              (util/info "Configuring microservices app\n")
              (spit appengine-application-out-file appengine-application-cfg)
              (spit application-out-file application-cfg)
              (spit manifest-out-file manifest-cfg)))))
      (-> fileset (boot/add-resource edn-tmp) boot/commit!))))

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
  (let [mod      (-> (boot/get-env) :gae :module :name)
        app-dir  (-> (boot/get-env) :gae :app :dir)
        ;; dir      (if service (str "target/" mod) "target")
        opts (merge {:sdk-root (:sdk-root config-map)
                     ;; :use-java7 true
                     :build-dir (str "target/" (if service mod))}
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
        app-cfg (Class/forName appcfg-class true class-loader)]

  ;; def appCfg = classLoader.loadClass(APPENGINE_TOOLS_MAIN)
  ;; appCfg.main(params as String[])
    (def method (first (filter #(= (. % getName) "main") (. app-cfg getMethods))))
    (def invoke-args (into-array Object [params]))
    (if verbose ;; (or (:verbose *opts*) (:verbose config-map))
      (do (println "CMD: AppCfg")
          (doseq [arg params]
            (println "\t" arg))))
    (. method invoke nil invoke-args)))

(boot/deftask filters
  "generate filters class files"

  [k keep bool "keep intermediate .clj files"
   n gen-filters-ns NS str "namespace to generate and aot; default: 'filters"
   w web-inf WEB-INF str "WEB-INF dir, default: WEB-INF"
   v verbose bool "Print trace messages."]
  (let [edn-tmp-dir (boot/tmp-dir!)
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
                                    (boot/by-re [(re-pattern (str boot-config-edn "$"))]))]
                    ;; (boot/by-name [boot-config-edn]))]
         (if (> (count boot-config-edn-files) 1)
           (throw (Exception. "only one _boot_config.edn file allowed")))
         (if (= (count boot-config-edn-files) 0)
           (throw (Exception. "cannot find _boot_config.edn")))

         (let [boot-config-edn-f (first boot-config-edn-files)
               boot-config-edn-map (-> (boot/tmp-file boot-config-edn-f) slurp read-string)]
           (if (:filters boot-config-edn-map)
             fileset
             (do
               ;; step 0: read the edn files
               (let [filters-edn-files (->> (boot/input-files fileset)
                                            (boot/by-name [filters-edn]))]
                 (if (> (count filters-edn-files) 1)
                   (throw (Exception. "only one filters.edn file allowed")))
                 (if (= (count filters-edn-files) 0)
                   (throw (Exception. "cannot find filters.edn")))
                 (let [edn-filters-f (first filters-edn-files)
                       filter-configs (-> (boot/tmp-file edn-filters-f) slurp read-string)
                       smap (-> boot-config-edn-map (assoc-in [:filters]
                                                        (:filters filter-configs)))
                       boot-config-edn-s (with-out-str (pp/pprint smap))]
                   ;; step 1:  inject filter config stanza to _boot_config.edn
                   (let [path     (boot/tmp-path boot-config-edn-f)
                         boot-config-edn-in-file  (boot/tmp-file boot-config-edn-f)
                         boot-config-edn-out-file (io/file edn-tmp-dir path)]
                     (io/make-parents boot-config-edn-out-file)
                     (spit boot-config-edn-out-file boot-config-edn-s))

                   ;; step 2: gen filters
                   (let [gen-filters-content (stencil/render-file "migae/templates/gen-filters.mustache"
                                                                   (assoc filter-configs
                                                                          :gen-filters-ns
                                                                          gen-filters-ns))
                         gen-filters-out-file (doto
                                                   (io/file gen-filters-tmp-dir gen-filters-path)
                                                 io/make-parents)]
                     (spit gen-filters-out-file gen-filters-content))))))))
       (util/info "Configuring filters...\n")

       ;; step 3: commit files to fileset
       (reset! prev-pre
               (-> fileset
                   (boot/add-source edn-tmp-dir)
                   (boot/add-source gen-filters-tmp-dir)
                   boot/commit!)))
     (builtin/aot :namespace #{gen-filters-ns}) ;; :dir (str web-inf "/classes"))
     (if keep identity
       (builtin/sift :include #{(re-pattern (str gen-filters-ns ".*.class"))}
                     :invert true))
     (if keep
       (comp
        (builtin/sift :to-resource #{(re-pattern boot-config-edn)})
        (builtin/sift :to-asset #{(re-pattern gen-filters-path)}))
       identity)
     )))

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

;;(core/deftask buber
;; FIXME: what was this for???
#_(defn buber
  [tgt]
  (println "BUBER ")
  (let [;; tgt        (boot/tmp-dir!)
        cache      (boot/cache-dir! ::uber :global true)
        dfl-scopes #{"compile" "runtime"}
        scopes     dfl-scopes
        ;; scopes     (-> dfl-scopes
        ;;                (set/union include-scope)
        ;;                (set/difference exclude-scope))
        _ (println "SCOPES: " dfl-scopes)
        scope?     #(contains? scopes (:scope (util/dep-as-map %)))
        jars       (-> pod/env ;; (boot/get-env)
                       (update-in [:dependencies] (partial filter scope?))
                       pod/resolve-dependency-jars)
        _ (println "JARS: " jars)
        jars       (remove #(.endsWith (.getName %) ".pom") jars)
        checkouts  (->> (boot/get-checkouts)
                        (filter (comp scope? :dep val))
                        (into {}))
        co-jars    (->> checkouts (map (comp :jar val)))
        ;; co-dirs    (->> checkouts (map (comp :dir val)))
        ;; exclude    (or exclude pod/standard-jar-exclusions)
        ;; merge      (or merge pod/standard-jar-mergers)
        ;; reducer    (fn [xs jar]
        ;;              (boot/add-cached-resource
        ;;                xs (digest/md5 jar) (partial pod/unpack-jar jar)
        ;;                :include include :exclude exclude :mergers merge))
        ;; co-reducer #(boot/add-resource
        ;;               %1 %2 :include include :exclude exclude :mergers merge)
        ]
    ;; (boot/with-pre-wrap [fs]
    ;; (println "co jars: " co-jars)
      (when (seq jars)
        (doseq [jar jars] ;; (reduce into [] [jars co-jars])]
          (let [dest (doto (io/file tgt (.getName jar)) io/make-parents)]
            (println "dest: " dest)
            (file/copy-atomically jar dest))))))

      ;;       (when-not (.exists src)
      ;;         (println "CACHING " name)
      ;;         (util/dbug* "Caching jar %s...\n" name)
      ;;         (file/copy-atomically jar src))
      ;;       (util/dbug* "Adding cached jar %s...\n" name)
      ;;       (println (format "Adding cached jar %s...\n" name))
      ;;       (file/hard-link src (io/file tgt name)))))
      ;; #_(boot/commit! (if as-jars
      ;;                 (boot/add-resource fs tgt)
      ;;                 (reduce co-reducer (reduce reducer fs jars) co-dirs)))))

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
  "configure gae logging"
  [l log LOG kw ":log4j or :jul"
   v verbose bool "Print trace messages."
   o odir ODIR str "output dir"]
  ;; (print-task "logging" *opts*)
  (let [workspace (boot/tmp-dir!)
        content (stencil/render-file
                 (if (= log :log4j)
                   "migae/templates/log4j.properties.mustache"
                   "migae/templates/logging.properties.mustache")
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
       (let [out-file (doto (io/file workspace (str odir "/" out-path)) io/make-parents)]
         (spit out-file content)
         (util/info "Configuring logging...\n")
         (-> fs (boot/add-resource workspace) boot/commit!))))))

(boot/deftask monitor
  "watch etc. for gae project"
  [s service bool "service"
   v verbose bool "verbose"]
  ;;(let [mod (str (-> (boot/get-env) :gae :module :name))]
  (comp (builtin/watch)
        (builtin/notify :audible true)
        (builtin/sift :move {#"(.*\.clj$)" (str classes-dir "/$1")})
        (if service
          (target :no-clean true :service service :monitor true)
          (target :no-clean true :service service :monitor false))))

;; FIXME: not sure what this was for.  Just moving clj sources to the right output dir?
;; replaced by sifting?
#_(boot/deftask make
  "dev build, source only"
  [v verbose bool "verbose"]
  (let [mod (str (-> (boot/get-env) :gae :module))
        prev (atom nil)
        cache-dir (boot/cache-dir! :boot-gae/build)
        tmpdir (boot/tmp-dir!)]
    ;; (println "MODULE: " mod)
    (boot/empty-dir! tmpdir)
    (comp
     (boot/with-pre-wrap [fileset]
       (let [changes (->> (boot/fileset-diff @prev fileset)
                          boot/output-files
                          (map :path))]
             ;; fs (boot/new-fileset)]
         (doseq [path changes]
           (if verbose (util/info (str "changed: " path "\n")))
           (let [in-file (boot/tmp-file (boot/tmp-get fileset path))
                 out-file (if (str/ends-with? path "clj")
                            (doto (io/file cache-dir classes-dir path) io/make-parents)
                            (doto (io/file cache-dir path) io/make-parents))
                 tmp-file (if (str/ends-with? path "clj")
                            (doto (io/file tmpdir classes-dir path) io/make-parents)
                            (doto (io/file tmpdir path) io/make-parents))]
             (io/copy in-file out-file)
             (io/copy in-file tmp-file)))
         (reset! prev fileset)))
     (builtin/sift :include #{#"^.*$"} :invert true)
     (boot/with-pre-wrap [fileset]
       (-> fileset
           (boot/add-asset tmpdir)
           boot/commit!))
     )))

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
   s service bool "build as service"
   w web-inf WEB-INF str "WEB-INF dir, default: WEB-INF"
   v verbose bool "Print trace messages."]
  (let [workspace (boot/tmp-dir!)
        prev-pre (atom nil)
        web-inf (if web-inf web-inf web-inf-dir)

        module (if service
                 (or module (-> (boot/get-env) :gae :module :name))
                 nil)

        gen-reloader-ns (if gen-reloader-ns (symbol gen-reloader-ns) (gensym "reloadergen"))
        gen-reloader-path (str gen-reloader-ns ".clj")
        reloader-impl-ns (if reloader-impl-ns (symbol reloader-impl-ns) "reloader")
        reloader-impl-path (str reloader-impl-ns ".clj")
        ]
    (comp
     (boot/with-pre-wrap [fileset]
       (let [
             ;; webapp-edn-files (->> (boot/fileset-diff @prev-pre fileset)
             ;;                       boot/input-files
             ;;                       (boot/by-name [webapp-edn]))
             ;; webapp-edn-f (condp = (count webapp-edn-files)
             ;;                0 nil
             ;;                1 (first webapp-edn-files)
             ;;                (throw (Exception. "only one webapp.edn file allowed")))
             ;; webapp-edn-map (if webapp-edn-f (-> (boot/tmp-file webapp-edn-f) slurp read-string) {})
             boot-config-edn-files (->> (boot/fileset-diff @prev-pre fileset)
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
             ;; boot-config-edn-map (merge boot-config-edn-map webapp-edn-map)
             ]
         ;; (println "boot-config-edn-map: " boot-config-edn-map)

         (if (:reloader boot-config-edn-map)
           fileset
           (do
             ;; (add-reloader! reloader-impl-ns urls in-file out-file)
             ;; step 1: create config map for reloader, inject into master config file
             (let [urls (flatten (concat (map #(% :urls) (:servlets boot-config-edn-map))))
                   m (-> boot-config-edn-map (assoc-in [:reloader]
                                                 {:ns reloader-impl-ns
                                                  :name "reloader"
                                                  :display {:name "Clojure reload filter"}
                                                  :urls (if (empty? urls) [{:url "/*"}]
                                                            (vec urls))
                                                  :desc {:text "Clojure reload filter"}}))
                   boot-config-edn-s (with-out-str (pp/pprint m))
                   boot-config-edn-out-file (io/file workspace
                                                     (if (instance? boot.tmpdir.TmpFile boot-config-edn-f)
                                                       (boot/tmp-path boot-config-edn-f)
                                                       boot-config-edn-f))]
               (io/make-parents boot-config-edn-out-file)
               (spit boot-config-edn-out-file boot-config-edn-s))

             (let [reloader-impl-content (stencil/render-file "migae/templates/reloader-impl.mustache"
                                                              {:reloader-ns reloader-impl-ns
                                                               :module (or module "./")})

                   gen-reloader-content (stencil/render-file "migae/templates/gen-reloader.mustache"
                                                             {:gen-reloader-ns gen-reloader-ns
                                                              :reloader-impl-ns reloader-impl-ns})
                   ;; _ (if verbose (println "impl: " reloader-impl-path))
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
   _ enable-filesapi bool "--enable_filesapi"

   ;; SharedMain.java
   _ sdk-root PATH str "--sdk_root"
   _ disable-restricted-check bool "--disable_restricted_check"
   _ external-resource-dir VAL str "--external_resource_dir"
   _ allow-remote-shutdown bool "--allow_remote_shutdown"
   a java-agent bool "use javaagaent (default: false).\n\t\tCAVEAT: setting to true may result in a dramatic increase in servlet startup time; setting false removes some security checks" ;; --no_java_agent

   ;; Kickstart.java
   _ generate-war bool "--generate_war"
   _ generated-war-dir PATH str "Set the directory where generated files are created."
   _ jvm-flags FLAG #{str} "--jvm_flags"
   _ start-on-first-thread bool "--startOnFirstThread"
   _ enable-jacoco bool "--enable_jacoco"
   _ jacoco-agent-jar VAL str"--jacoco_agent_jar"
   _ jacoco-agent-args VAL str"--jacoco_agent_args"
   _ jacoco-exec VAL str "--jacoco_exec"

   _ modules MODULES edn "modules map"

   s service bool "service"
   v verbose bool "verbose"]

  (boot/with-pre-wrap [fileset]
    (let [webapp-edn-files (->> fileset
                                boot/input-files
                                (boot/by-name [webapp-edn]))
          webapp-edn-f (condp = (count webapp-edn-files)
                         0 nil
                         1 (first webapp-edn-files)
                         (throw (Exception. "only one webapp.edn file allowed")))
          webapp-edn-map (if webapp-edn-f (-> (boot/tmp-file webapp-edn-f) slurp read-string) {})
          ;; (println "*OPTS*: " *opts*)
          args (->args *opts*)
          ;; _ (println "ARGS: " args)
          ;; jargs (into-array String (conj jargs "build/exploded-app"))

          target-dir (if service (str (-> (boot/get-env) :gae :app :dir)
                                      "/target/"
                                      (-> (boot/get-env) :gae :module :name))
                         "target")

          checkout-vec (-> (boot/get-env) :checkouts)
          cos (map #(assoc (apply hash-map %) :coords [(first %) (second %)]) checkout-vec)
          default-mod (filter #(or (:default %) (not (:module %))) cos)
          _ (if (> (count default-mod) 1)
              (throw (Exception. "Only one default module allowed" (count default-mod))))


          mod-ports (remove nil?
                            (into [] ;; (for [[mod port] (-> (boot/get-env) :gae :modules)]
                                  (map #(if (:module %)
                                          (str "--jvm_flag=-Dcom.google.appengine.devappserver_module."
                                               (:module %)
                                               ".port="
                                               (str (:port %)))
                                          nil)
                                       cos)))
                            ;; modules))

          _ (println "MOD PORTS: " mod-ports)

          http-port (or http-port (if-let [p (:port (first default-mod))] p nil))
          _ (println "HTTP-PORT: " http-port)

          jvm-flags (for [flag jvm-flags] (str "--jvm_flag=" flag))
          jargs (concat ["com.google.appengine.tools.development.DevAppServerMain"
                         (str "--sdk_root=" (:sdk-root config-map))]
                        mod-ports
                        jvm-flags
                        (if disable-restricted-check ["--disable_restricted_check"])
                        (if (not java-agent) ["--no_java_agent"])
                        (if http-port [(str "--port=" http-port)])
                        (if http-address [(str "--address=" http-address)])
                        [target-dir])
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
                                {:url (str url)}))
                ns (if (:ns config) (:ns config) (:class config)) ]
            (merge config {:urls urls
                           :ns ns})))))})

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
                     gen-servlets-content (stencil/render-file "migae/templates/gen-servlets.mustache"
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
      (let [boot-config-edn-fs (->> (boot/fileset-diff @prev-pre fileset)
                                boot/input-files
                                (boot/by-re [(re-pattern (str boot-config-edn "$"))]))]
            ;; (boot/by-name [boot-config-edn]))]
        (if (> (count boot-config-edn-fs) 1) (throw (Exception. "only one _boot_config.edn file allowed")))
        (if (= (count boot-config-edn-fs) 0) (throw (Exception. "_boot_config.edn file not found")))

        (let [boot-config-edn-f (first boot-config-edn-fs)
              web-xml (-> (boot/tmp-file boot-config-edn-f) slurp read-string)
              path     (boot/tmp-path boot-config-edn-f)
              in-file  (boot/tmp-file boot-config-edn-f)
              out-file (io/file workspace path)]
          (let [content (stencil/render-file
                         "migae/templates/xml.web.mustache"
                         web-xml)]
            (let [;;tmp-dir (boot/tmp-dir!)
                  xml-out-path (str odir "/web.xml")
                  xml-out-file (doto (io/file workspace xml-out-path) io/make-parents)
                  ;; reloader (doto (io/file tmp-dir "foobar.xml") io/make-parents)
                  ]
           ;; web.xml always
           ;; (if (.exists reloader)
           ;;   nop
           ;;   create empty reloader
              (if verbose (util/info "Configuring web.xml\n"))
              (spit xml-out-file content)))))
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
