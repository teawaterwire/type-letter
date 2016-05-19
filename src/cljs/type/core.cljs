(ns type.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [reagent.core :as reagent :refer [atom]]
            [reagent.session :as session]
            [cljs.core.async :as async
             :refer [>! <! put! chan timeout poll!]]
            [goog.events :as events]
            [secretary.core :as secretary :include-macros true]
            [type.dumb-views :refer [footer]])
  (:import [goog.events EventType]))


;; -------------------------
;; App states, constants and update functions

(defonce status (atom nil)) ; Could be nil, :started or :ended
(defonce score (atom 0))
(defonce letter-wanted (atom "?"))

(defonce first-interval 2000) ; First letter gets 2s
(defonce speed-delta 20) ; Decreased by 20ms at each level
(defonce alphabet (seq "ABCDEFGHIJKLMNOPQRSTUVWXYZ"))

(defn start! [] (reset! status :started) (reset! score 0))
(defn end! [] (reset! status :ended))
(defn next-level! [] (swap! score inc))
(defn set-letter-wanted! [l] (reset! letter-wanted l))
(defn get-interval [] (-> @score (* -1 speed-delta) (+ first-interval)))


;; -------------------------
;; Views

(defn timer-bar []
  (let [previous-score (atom 0)
        time-left (atom (get-interval))]
    ;; I'm not sure this is the best way to "decrement" time...
    (js/setInterval #(swap! time-left (fn [t] (- t speed-delta))) speed-delta)
    (fn []
      (when (#{:ended} @status) (reset! time-left 0))
      (let [interval (get-interval)
            tx (-> @time-left (* 100) (/ interval) (str "%"))]
        (when (> @score @previous-score)
          (reset! previous-score @score) (reset! time-left interval))
        [:div#t.loader-bck {:style {:position "absolute"
                                    :height "100%"
                                    :width "100%"
                                    :transform (str "translateX(" tx ")")}}]))))

(defn home-page []
  [:div.fheight
   (when (#{:started} @status)
     [timer-bar])
   [:div.container
    [:span.logo.left "TYPE LETTER"]
    [:span.primary-info.left
     "type the letters as they appear"]
    [:span.press-start.right "SCORE: " @score]
    [:div.giant-letter {:class @status} @letter-wanted]
    [:div.press-start.blink
     (case @status
       nil "PRESS ANY KEY TO START"
       :ended "GAME OVER... PRESS ANY KEY TO TRY AGAIN"
       "")]
    [footer]]])


;; -------------------------
;; Put keyDown events into channel (from David Nolen)

(defn events->chan [el event-type c]
  (events/listen el event-type (fn [e] (put! c e)))
  c)

(defn createKeysChan []
  (events->chan
   js/window
   EventType.KEYDOWN
   (chan 1 (map #(-> % .-keyCode js/String.fromCharCode)))))


;; -------------------------
;; Create letter generator

(defn start-generator [keys-chan]
 (go
  (<! keys-chan) ; Waiting for first letter to start
  (start!) ; Update status to :started
  (set-letter-wanted! (rand-nth alphabet))
  (loop []
    (let [t (timeout (get-interval))
          [letter c] (alts! [t keys-chan])] ; Just loving the alts!
      (when (and (= c keys-chan) (= @letter-wanted letter))
        (let [l (rand-nth alphabet)]
          (next-level!)
          (set-letter-wanted! l)
          (recur)))
      (end!) ; Update status to :ended
      (<! (timeout 1000)) ; Wait for one second and
      (poll! keys-chan) ; Clean channel and start again
      (start-generator keys-chan)))))


;; -------------------------
;; Initialize app

(defn mount-root []
  (reagent/render [home-page] (.getElementById js/document "app")))

(defn init! []
  (start-generator (createKeysChan))
  (mount-root))
