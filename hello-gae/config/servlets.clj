(ns servlets)

;;   :servlet-ns 'hello.servlets
(def config
  {:servlets [{:ns 'hello.echo  ;; = servlet-class
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
                        {:name "arg2" :val 2}]}]})
