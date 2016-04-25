(ns type.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [reagent.core :as reagent :refer [atom]]
            [reagent.session :as session]
            [cljs.core.async :as async
             :refer [>! <! put! chan timeout]]
            [goog.events :as events]
            [secretary.core :as secretary :include-macros true])
  (:import [goog.events EventType]))

;; -------------------------
;; App state

(defonce app (atom {:letter-wanted nil :status nil :score 0}))

(defn pre-start! []
  (swap! app assoc :status :starting)
  (swap! app assoc :score 0))
(defn start! [] (swap! app assoc :status :started))
(defn end! []
  (swap! app assoc :status :ended)
  (swap! app assoc :timestamp nil))
(defn next-level! [] (swap! app update :score inc))
(defn set-letter-wanted! [l]
  (swap! app assoc :letter-wanted l)
  (swap! app assoc :timestamp (.getTime (js/Date.))))
(defn get-interval []
  (-> (:score @app) (* -10) (+ 2000)))

;; -------------------------
;; Create letter generator

(defonce alphabet (seq "ABCDEFGHIJKLMNOPQRSTUVXYZ"))
; http://onlineslangdictionary.com/lists/most-vulgar-words/
(defonce words ["CUNT" "SKULLFUCK" "BLUMPKIN" "ASSMUCUS" "MOTHERFUCKER"
                "CUMDUMP" "FUCKMEAT" "FUCK" "GFY" "FUCKTOY" "SPERG"
                "COCK" "CUNTBAG" "SWEARGASM" "FUB" "SHUM"])

(defn start-generator [keys-chan]
  (go
   (<! keys-chan)
   (pre-start!)
   (<! (timeout 2000))
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

(defn timer-bar [timestamp]
  (let [speed 20
        timestamp (atom timestamp)
        time-left (atom (get-interval))]
    (js/setInterval #(swap! time-left (fn [t] (- t speed))) speed)
    (fn [stamp status]
      (when (> stamp @timestamp)
        (reset! time-left (get-interval))
        (reset! timestamp stamp))
      (when (= :ended status) (reset! time-left 0))
      [:div {:style {:height "4px"
                     :width (-> @time-left (* 100) (/ (get-interval)) (str "%"))
                     :background-color "#FF304F"}}])))

(defn toggle-mode []
  [:input {:type "checkbox"
           :default-checked (:offensive @app)
           :on-change #(swap! app assoc :offensive (-> % .-target .-value))} "Offensive mode (you've been warned!)"])

(defn home-page []
  [:div
   [:div {:style {:background-color "#118DF0" :padding "80px"}}
    ; [toggle-mode]
    [:h2 (case (:status @app)
           :starting "Focus on the letter!"
           :started "type type type"
           :ended "Game Over... Press any key to try again!"
           "Press any key to start")]]
   [:div
    (when (#{:started :ended} (:status @app))
      [:div
       [:div {:style {:background-color "#0E2F56" :padding "40px"}}
        [:h2 {:style {:text-shadow "-2px 0px 1px #ECECDA"
                      :color (->> "0123456789abcdef" shuffle (take 6) (apply str "#"))}}
         (:letter-wanted @app)]
        [timer-bar (:timestamp @app) (:status @app)]]
       [:div {:style {:background-color "#FF304F" :padding "20px"}}
        [:h3 "Score: " (:score @app)]]])]])

;; -------------------------
;; Initialize app

(defn mount-root []
  (reagent/render [home-page] (.getElementById js/document "app")))

(defn init! []
  (start-generator (createKeysChan))
  (mount-root))
