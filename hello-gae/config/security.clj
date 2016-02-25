(ns security)

(def config
  {:security [{:resource {:name "foo" :desc {:text "Foo resource security"}
                          :url "/foo/*"}
               :role "admin"}]})
