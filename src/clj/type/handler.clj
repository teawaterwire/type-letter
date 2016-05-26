(ns type.handler
  (:require [compojure.core :refer [GET defroutes]]
            [compojure.route :refer [not-found resources]]
            [hiccup.page :refer [include-js include-css html5]]
            [type.middleware :refer [wrap-middleware]]
            [config.core :refer [env]]
            [clojure.java.io :as io]
            [pl.danieljanus.tagsoup :as ts]))

(def mount-target
  [:div#app.fheight
      [:h3.press-start "Wait for it!"]])

(def loading-page
  (html5
   [:head
    [:title "Type Letter"]
    [:meta {:charset "utf-8"}]
    [:meta {:name "viewport"
            :content "width=device-width, initial-scale=1"}]
    [:meta {:name "description"
            :content "Mini game to learn to type fast."}]
    [:link {:rel "icon"
            :href "/favicon.ico"}]
    [:link {:href "https://fonts.googleapis.com/css?family=Cousine"
            :rel "stylesheet"}]
    [:link {:href "https://fonts.googleapis.com/css?family=Press+Start+2P"
            :rel "stylesheet"}]
    (include-css (if (env :dev) "/css/site.css" "/css/site.min.css"))]
    [:body
     mount-target
     (ts/parse-string (slurp (io/resource "public/html/ribbon.html")))
     (include-js "/js/app.js")
     (include-js "/js/analytics.js")]))


(defroutes routes
  (GET "/" [] loading-page)
  (resources "/")
  (not-found "Not Found"))

(def app (wrap-middleware #'routes))
