(ns calendula
  (:require [clojure.string :as str]
            [nextjournal.impulse :as impulse]
            [nextjournal.garden-id :as garden-id :refer [username displayname email]]
            [nextjournal.garden-email :as garden-email]
            [ring.util.codec :as codec])
  (:import (java.time LocalDate DayOfWeek)
           (java.time.temporal TemporalAdjusters)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; configuration

(def availability {:monday [{:start 9 :end 10}
                            {:start 12 :end 15}]
                   :wednesday [{:start 8 :end 10}
                               {:start 12 :end 14}]})

(def owner-email (or (System/getenv "GARDEN_OWNER_EMAIL") "my-email@example.com"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; date stuff

(defn now [] (str (LocalDate/now)))

(defn before? [a b]
  (.isBefore (LocalDate/parse a) (LocalDate/parse b)))

(defn start-of-week [date]
  (str (.with (LocalDate/parse date) (TemporalAdjusters/previous DayOfWeek/MONDAY))))

(defn offset-week [date offset]
  (str (.plusWeeks (LocalDate/parse date) offset)))

(defn dates-of-week [start-date]
  (map (fn [offset] (str (.plusDays (LocalDate/parse start-date) offset))) (range 7)))

(defn date->keyword [day]
  (keyword (str/lower-case (str (.getDayOfWeek (LocalDate/parse day))))))

(defn free? [availability day hour]
  (and (some (fn [{:keys [start end]}] (<= start hour end)) (availability (date->keyword day)))
       (not (get-in @impulse/state [:appointments day hour]))))

(comment
  (before? "2024-09-16" "2024-09-17") ;=> true
  (before? "2024-09-17" "2024-09-16") ;=> false
  (dates-of-week (start-of-week (now)))
  (start-of-week (now))
  (offset-week (start-of-week (now)) 1)
  (dates-of-week (offset-week (start-of-week (now)) 1))
  (date->keyword "2024-01-01") ;=> :monday
  (free? {:monday [{:start 12 :end 15}]} "2024-01-01" 15))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; the app!

(defn render-day [req day]
  [:div {:id (str "date-" day)}
   [:strong (str (name (date->keyword day)))]
   [:span " (" day ")"]
   (for [hour (mapcat #(range (:start %) (:end %)) (availability (date->keyword day)))
         :let [status (cond (free? availability day hour) :free
                            (= (get-in @impulse/state [:appointments day hour]) (email req)) :mine
                            :else nil)]
         :when status]              ; either free or booked by the current user
     [:div {:hx-post (str (if (= status :free)
                            "/book-appointment?"
                            "/unbook-appointment?")
                          (codec/form-encode {:day day :hour hour}))
            :hx-target (str "#date-" day)}
      [:input {:type "checkbox"
               :checked (= status :mine)}]
      (str hour ":00")])])

(defn render-week [req start-of-week]
  (let [today (now)]
    [:div#week
     [:div.grid
      (for [date (dates-of-week start-of-week)
            :when (and (availability (date->keyword date))
                       (not (before? date today)))]
        (render-day req date))]
     [:div {:role "group" :style "margin-top: 1em"}
      (if (before? start-of-week today)
        [:button.outline {:disabled true} "← prev week"]
        [:button.outline {:hx-post "/prev" :hx-target "#week"} "← prev week"])
      [:button.outline {:hx-post "/next" :hx-target "#week"} "next week →"]]]))

(defn index [req]
  (let [uname (username req)]
    (impulse/page
     {:title "Calendula"}
     [:nav
      [:ul
       [:li
        [:svg {:xmlns "http://www.w3.org/2000/svg" :viewbox "0 0 60 75" :x "0px" :y "0px" :width "2em" :style "margin-right: 0.5em"}
         [:path {:d "M49.279,22.01c4.121-4.266,5.006-10.147,1.937-13.228S42.256,6.6,37.99,10.721C37.88,4.785,34.343,0,30,0s-7.88,4.785-7.99,10.721C17.745,6.6,11.864,5.715,8.782,8.784,5.715,11.863,6.6,17.745,10.721,22.01,4.785,22.12,0,25.657,0,30s4.785,7.88,10.721,7.99C6.6,42.255,5.715,48.137,8.784,51.218a6.649,6.649,0,0,0,4.8,1.832,12.563,12.563,0,0,0,8.423-3.774C22.118,55.213,25.657,60,30,60s7.882-4.787,7.99-10.724a12.561,12.561,0,0,0,8.422,3.774,6.663,6.663,0,0,0,4.806-1.834c3.067-3.079,2.182-8.961-1.939-13.226C55.215,37.88,60,34.343,60,30S55.215,22.12,49.279,22.01ZM39.2,12.326c3.5-3.511,8.259-4.467,10.6-2.13s1.383,7.1-2.129,10.607c-.46.46-.921.888-1.38,1.306A21.6,21.6,0,0,0,36.228,24.99a8.155,8.155,0,0,0-1.218-1.218,21.6,21.6,0,0,0,2.881-10.065C38.309,13.248,38.737,12.787,39.2,12.326ZM36,30a6,6,0,1,1-6-6A6.006,6.006,0,0,1,36,30ZM30,2c3.309,0,6,4.037,6,9,0,4.9-1,9.186-2.7,11.723a7.911,7.911,0,0,0-6.606,0C25,20.185,24,15.9,24,11,24,6.037,26.691,2,30,2ZM10.2,10.2c2.347-2.336,7.1-1.383,10.607,2.129.46.46.888.921,1.306,1.38A21.6,21.6,0,0,0,24.99,23.772a8.155,8.155,0,0,0-1.218,1.218,21.6,21.6,0,0,0-10.065-2.881c-.459-.418-.92-.846-1.381-1.307C8.815,17.3,7.86,12.542,10.2,10.2ZM2,30c0-3.309,4.037-6,9-6,4.9,0,9.186,1,11.723,2.7a7.911,7.911,0,0,0,0,6.606C20.185,35,15.9,36,11,36,6.037,36,2,33.309,2,30ZM20.8,47.674c-3.5,3.511-8.259,4.467-10.6,2.13S8.815,42.7,12.327,39.2c.46-.46.921-.888,1.38-1.306A21.6,21.6,0,0,0,23.772,35.01a8.155,8.155,0,0,0,1.218,1.218,21.6,21.6,0,0,0-2.881,10.065C21.691,46.752,21.263,47.213,20.8,47.674ZM30,58c-3.309,0-6-4.037-6-9,0-4.9,1-9.185,2.7-11.722a7.911,7.911,0,0,0,6.606,0C35,39.814,36,44.1,36,49,36,53.963,33.309,58,30,58Zm19.8-8.2C47.458,52.137,42.7,51.185,39.2,47.673c-.46-.46-.888-.921-1.306-1.38A21.6,21.6,0,0,0,35.01,36.228a8.155,8.155,0,0,0,1.218-1.218,21.6,21.6,0,0,0,10.065,2.881c.459.418.92.846,1.381,1.307C51.185,42.7,52.14,47.458,49.8,49.8ZM49,36c-4.9,0-9.185-1-11.722-2.7a7.911,7.911,0,0,0,0-6.606C39.814,25,44.1,24,49,24c4.963,0,9,2.691,9,6S53.963,36,49,36Z"}]]
        [:strong "Calendula"]]]
      [:ul
       [:li (if uname
              [:span (str uname " ") [:a {:href garden-id/logout-uri} "(logout)"]]
              [:a {:href garden-id/login-uri} "login"])]]]
     (when uname
       (render-week req (get-in @impulse/state [:user-state uname :week] (start-of-week (now))))))))

(defn book-appointment [{:as req :keys [params]}]
  (let [{:strs [day hour]} params]
    ;; XXX race condition if multiple users are booking appointments at the same time
    (swap! impulse/state assoc-in [:appointments (str day) (parse-long hour)] (email req))
    (garden-email/send-email! {:to {:email owner-email}
                               :subject (format "%s (%s) booked appointment at %s %s:00" (displayname req) (email req) day hour)})
    (garden-email/send-email! {:to {:email (email req)}
                               :subject (format "You booked an appointment at %s %s:00" day hour)})
    (render-day req day)))

(defn unbook-appointment [{:as req :keys [params]}]
  (let [{:strs [day hour]} params]
    (swap! impulse/state update-in [:appointments (str day)] dissoc (parse-long hour))
    (garden-email/send-email! {:to {:email owner-email}
                               :subject (format "%s (%s) has canceled their appointment at %s %s:00" (displayname req) (email req) day hour)})
    (garden-email/send-email! {:to {:email (email req)}
                               :subject (format "You cancelled an appointment at %s %s:00" day hour)})
    (render-day req day)))

(defn update-week [offset req]
  (render-week req (impulse/swap-in! impulse/state
                                     [:user-state (username req) :week]
                                     (fnil offset-week (start-of-week (now)))
                                     offset)))

(def routes
  [["/" #'index]
   ["/book-appointment" {:post #'book-appointment}]
   ["/unbook-appointment" {:post #'unbook-appointment}]
   ["/next" {:post (partial update-week 1)}]
   ["/prev" {:post (partial update-week -1)}]])

(defn start! [opts]
  (impulse/start! #'routes opts))
