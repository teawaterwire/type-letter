(ns type.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [reagent.core :as reagent :refer [atom]]
            [reagent.session :as session]
            [cljs.core.async :as async
             :refer [>! <! put! chan timeout poll!]]
            [goog.events :as events]
            [secretary.core :as secretary :include-macros true])
  (:import [goog.events EventType]))

;; -------------------------
;; App state

(defonce app (atom {:status nil :score 0}))

(defonce first-interval 2000)
(defonce speed-inc -10)
(defn start! []
  (swap! app assoc :score 0)
  (swap! app assoc :status :started))
(defn pre-end! [] (swap! app assoc :status :ending))
(defn end! []
  (swap! app assoc :status :ended)
  (swap! app assoc :timestamp nil))
(defn next-level! [] (swap! app update :score inc))
(defn set-letter-wanted! [l]
  (swap! app assoc :letter-wanted l)
  (swap! app assoc :timestamp (.getTime (js/Date.))))
(defn get-interval []
  (-> (:score @app) (* speed-inc) (+ first-interval)))

;; -------------------------
;; Create letter generator

(defonce alphabet (seq "ABCDEFGHIJKLMNOPQRSTUVWXYZ"))
; http://onlineslangdictionary.com/lists/most-vulgar-words/
(defonce words ["CUNT" "SKULLFUCK" "BLUMPKIN" "ASSMUCUS" "MOTHERFUCKER"
                "CUMDUMP" "FUCKMEAT" "FUCK" "GFY" "FUCKTOY" "SPERG"
                "COCK" "CUNTBAG" "SWEARGASM" "FUB" "SHUM"])

(defn start-generator [keys-chan]
  (go
   (<! keys-chan)
   (start!)
   (set-letter-wanted! (rand-nth alphabet))
   (loop [w ""]
     (let [t (timeout (get-interval))
           [letter c] (alts! [t keys-chan])]
       (when (and (= c keys-chan) (= (:letter-wanted @app) letter))
         (let [word (if (= w "") (rand-nth (if (:offensive @app) words alphabet)) w)
               l (first word)]
           (next-level!)
           (set-letter-wanted! l)
           (recur (subs word 1))))
       (pre-end!)
       (<! (timeout 1000))
       (poll! keys-chan)
       (end!)
       (start-generator keys-chan)))))

;; -------------------------
;; Define utilities listening to global events and feeding channels

(defn events->chan
  "Given a target DOM element and event type return a channel of
  observed events. Can supply the channel to receive events as third
  optional argument."
  ([el event-type] (events->chan el event-type (chan)))
  ([el event-type c]
   (events/listen el event-type
                  (fn [e] (put! c e)))
   c))

(defn createKeysChan []
  (events->chan
   js/window
   EventType.KEYDOWN
   (chan 1 (map #(-> % .-keyCode js/String.fromCharCode)))))

;; -------------------------
;; Views

(defn toggle-mode []
  [:input {:type "checkbox"
           :default-checked (:offensive @app)
           :on-change #(swap! app assoc :offensive (-> % .-target .-value))} "Offensive mode (you've been warned!)"])

(defn twitter-button []
  (let [base "https://platform.twitter.com/widgets/tweet_button.html?"
        size "size=l"
        domain "&url=http://typeletter.co/"
        text "&text=Learn to type fast with Type Letter 💯➕💯"
        src (reduce str [base size domain text])]
    (fn []
      [:iframe {:src src
                :scrolling "no"
                :width 80
                :height 30
                :style {:border 0}}])))

(defn timer-bar [timestamp]
  (let [speed 20
        timestamp (atom timestamp)
        time-left (atom (get-interval))]
    (js/setInterval #(swap! time-left (fn [t] (- t speed))) speed)
    (fn [stamp status interval]
      (when (> stamp @timestamp)
        (reset! time-left interval)
        (reset! timestamp stamp))
      (when (#{:ending :ended} status) (reset! time-left 0))
      (let [tx (-> @time-left (* 100) (/ interval) (str "%"))]
        [:div.loader-bck {:style {:position "absolute"
                                  :height "100%"
                                  :width "100%"
                                  :transform (str "translateX(" tx ")")}}]))))

(defn home-page []
  [:div.fheight
   (when (:status @app)
     [timer-bar
      (:timestamp @app)
      (:status @app)
      (get-interval)])
   [:div.container
    [:span.logo.left "TYPE LETTER"]
    [:span.instructions-and-link.left
     "type the letters as they appear"]
    [:span.press-start.right "SCORE: " (:score @app)]
    [:div.giant-letter {:class-name (:status @app)} (get @app :letter-wanted "?")]
    [:div.press-start.blink
     (case (:status @app)
       nil "PRESS ANY KEY TO START"
       :ended "GAME OVER... PRESS ANY KEY TO TRY AGAIN"
       "")]
    [:div.footer
     [:span.social-btn.left [twitter-button]]
     [:span.instructions-and-link.right {:style {:font-size "14px"
                                                 :position "relative"
                                                 :top "6px"}}
      [:span {:style {:color "#fff"}} "Made by "]
      [:a {:target "_blank"
           :href "https://twitter.com/teawaterwire"} "@teawaterwire"]
      [:span {:style {:color "#fff"}} " & Designed by "]
      [:a {:target "_blank"
           :href "https://twitter.com/guillaumechabot"} "@guillaumechabot"]]]]
   ])

;; -------------------------
;; Initialize app

(defn mount-root []
  (reagent/render [home-page] (.getElementById js/document "app")))

(defn init! []
  (start-generator (createKeysChan))
  (mount-root))
