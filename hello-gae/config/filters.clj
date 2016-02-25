(ns filters)

(def config
  {:filters [{:ns 'reloader   ; REQUIRED
              :name "reloader"      ; REQUIRED
              :display {:name "Clojure reload filter"} ; OPTIONAL
              :urls [{:url "/echo/*"}
                     {:url "/math/*"}]
              :desc {:text "clojure reload filter"}}]})
