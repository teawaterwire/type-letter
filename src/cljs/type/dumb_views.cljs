(ns type.dumb-views)

(defn twitter-button []
  (let [base "https://platform.twitter.com/widgets/tweet_button.html?"
        size "size=l"
        domain "&url=http://typeletter.co/"
        text "&text=Learn to type fast with Type Letter ⌨💯"
        src (reduce str [base size domain text])]
    (fn []
      [:iframe {:src src
                :scrolling "no"
                :width 80
                :height 30
                :style {:border 0}}])))

(defn footer []
  [:div.footer
   [:span.social-btn.left [twitter-button]]
   [:span.primary-info.right.small {:style {:position "relative" :top "6px"}}
    [:span.white "Made by "]
    [:a {:target "_blank"
         :href "https://twitter.com/teawaterwire"} "@teawaterwire"]
    [:span.white " & Designed by "]
    [:a {:target "_blank"
         :href "https://twitter.com/guillaumechabot"} "@guillaumechabot"]]])
