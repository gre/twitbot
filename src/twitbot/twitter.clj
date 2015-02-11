(ns twitbot.twitter
  (:use
    [twitbot.config]
    [overtone.at-at]
    [twitter.oauth]
    [twitter.callbacks]
    [twitter.callbacks.handlers]
    [twitter.api.search]
    [twitter.api.streaming]
    [twitter.api.restful])
  (:require
    [clojure.core.async :as async :refer [>! <! filter< alts! sliding-buffer tap mult chan thread timeout go go-loop alts!!]]
    [twitter-streaming-client.core :as client]
    [twitter.oauth :as oauth]
    [clojure.data.json :as json]
    [http.async.client :as ac])
  (:import
    (twitter.callbacks.protocols SyncSingleCallback)
    (twitter.callbacks.protocols AsyncStreamingCallback)))

(defn pp [o] (let [_ (clojure.pprint/pprint o)] o))

(def my-creds (make-oauth-creds (:twitter-key config)
                (:twitter-secret config)
                (:twitter-accesstoken config)
                (:twitter-accesstoken-secret config)))

(defn tweet
  ([message]
   (statuses-update :oauth-creds my-creds :params {:status message }))
  ([message parent-id]
   (statuses-update :oauth-creds my-creds :params {:status message, :in_reply_to_status_id parent-id })))

(defn follow [screen-name]
  (friendships-create :oauth-creds my-creds :params { :screen_name screen-name }))

(defn star [id]
  (favorites-create :oauth-creds my-creds :params { :id id }))

(defn retweet [id]
  (statuses-retweets-id :oauth-creds my-creds :params { :id id }))

(defn create-stream [term]
  (client/create-twitter-stream twitter.api.streaming/statuses-filter :oauth-creds my-creds :params {:track term}))

(defn start-stream [stream]
  (client/start-twitter-stream stream))

(defn stream [keywords]
  (let [c (chan)
        stream (create-stream (clojure.string/join "," keywords))
        _ (start-stream stream)
        my-pool (mk-pool)]

    (every
      1000
      (fn[]
        (let [q (client/retrieve-queues stream)
              tweets (:tweet q)]
          (if tweets (doseq [t tweets] (go (>! c t))))))
      my-pool)

    c))


; (defn parse-date
;   [date]
;   (.parse (new java.text.SimpleDateFormat "EEE MMM dd HH:mm:ss Z yyyy" java.util.Locale/ENGLISH) date))
; 
; (defn pp [o] (let [_ (clojure.pprint/pprint o)] o))
; 
; (defn tweet-query [keywords exclude] (clojure.string/join " " (concat keywords (map #(str "-" %) exclude))))
; 
; (defn search-new-tweets
;   [keywords exclude after-time]
;   (let [response (search :oauth-creds my-creds :params {:q (tweet-query keywords exclude)})]
;     (if (not= (-> response :status :code) 200) []
;       (filter
;         #(> (.compareTo (-> % :created_at parse-date) after-time) 0)
;         (-> response :body :statuses))
;       )))
; 
;  (defn date-now [ deltaTime ] (new java.util.Date (+ (.getTime (new java.util.Date)) deltaTime)))
; 
; (defn stream-by-search-loop [interval keywords exclude callback]
;   (let [
;     from-time (date-now (- interval))
;     tweets (search-new-tweets keywords exclude from-time)]
;     (pp (str "nb tweets from search: " (count tweets)))
;   (doseq [t tweets] (callback t))
; 
; 
;   (Thread/sleep interval)
;   (recur interval keywords exclude callback)))
; 
; (defn stream-by-search [keywords exclude callback]
;   (stream-by-search-loop 60000 keywords exclude callback))
; 
; (stream-by-search ["linux"] ["gnu" "kernel"] pp)

