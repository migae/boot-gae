(ns appstats)

(def config
  {:appstats
   {:admin-console {:url "/appstats" :name "Appstats"}
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
    :servlet {:display {:name "Google Appstats"}}}})
