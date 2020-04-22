(ns ^:biff biff.system
  (:require
    [biff.http :as bhttp]
    [biff.util.system :refer [start-biff]]
    [biff.util.crux :as bu-crux]
    [biff.util :as bu]
    [mount.core :refer [defstate]]
    [trident.util :as u]
    [biff.core :as core]))

(defmulti ws (fn [event data] (:event-id event)))

(defmethod ws :default
  [_ _]
  (bu/anom :not-found))

(defn get-config [{:keys [main plugins mailgun/api-key]}]
  (let [[routes
         static-pages
         rules
         triggers] (->> plugins
                     vals
                     (map (juxt ::route ::static-pages ::rules ::triggers))
                     (apply map (comp not-empty #(filter some? %) vector)))]
    (u/assoc-some
      {:app-namespace 'biff.system
       :event-handler #(ws % (:?data %))
       :on-signin "/"
       :on-signin-request "/biff/signin-request"
       :on-signin-fail "/biff/signin-fail"
       :on-signout "/biff/signin"
       :send-email (some-> main :biff/send-email requiring-resolve)}
      :static-pages (when static-pages
                      (apply bu/merge-safe static-pages))
      :route (some->> routes (into [""]))
      :rules (when rules
               (apply merge-with bu/merge-safe rules))
      :triggers (when triggers
                  (apply merge-with
                    (fn [m1 m2]
                      (merge-with
                        (fn [f g]
                          (fn [env]
                            (f env)
                            (g env)))
                        m1 m2))
                    triggers)))))

(defstate system
  :start (start-biff (get-config (merge core/config (bu/secrets))))
  :stop ((:close system)))

(defmethod bhttp/handler :default
  [req]
  (if-some [handler (:handler system)]
    (handler req)
    {:status 503
     :body "Unavailable."
     :headers {"Content-Type" "text/plain"}}))
