(ns twitbot.core
  (:gen-class)
  (:use
    [twitbot.stream]
    [twitbot.config])
  (:require
    [clojure.core.async :as async :refer [>! <! filter< alts! sliding-buffer tap mult chan thread timeout go go-loop alts!!]]
    [twitbot.spreadsheet :as spreadsheet]
    [twitbot.detectlang :as detectlang]
    [twitbot.twitter :as twitter]))

(require 'clojure.edn)

(defn pp [o] (let [_ (clojure.pprint/pprint o)] o))

;; CONFIG

(println "Loading messages...")

(def mock-twitter-action (get config :mock-twitter-action false))

(def messages
  (if-not (nil? (:messages-gdocs config))
    (spreadsheet/gdocs-load-messages (:messages-gdocs config))
    (clojure.edn/read-string (slurp "messages.edn"))))

(pp messages)

;;;;;

(defn guess-lang
  [tweet]
  (let [ lang (:lang tweet) ]
    (if (= lang "und")
      (detectlang/identify (:text tweet)) ; fallback to detectlang service when twitter say undefined
      lang)))

(defn pick-answer
  "Pick an answer for a given topic and language. Ex: 'linux' 'fr'"
  [topic lang]
  (let [
    topic-data (get messages (keyword topic))]
    (rand-nth (get (:messages topic-data) (keyword lang)))))

;;; ACTIONS

(defn reply [topicid original-tweet]
    (let [
          message (:text original-tweet)
          tweet-id (:id original-tweet)
          lang (guess-lang original-tweet)
          answer (pick-answer topicid lang)
          screen-name (-> original-tweet :user :screen_name)
          to-tweet-text (str "@" screen-name " " answer) ]
      (if answer
        (do
          (println "\n" (str "[" topicid " " lang " " tweet-id "]") ":" message "\n ==>" to-tweet-text)
          (if (get config :follow-on-tweet false)
            (twitter/follow screen-name))
          (if-not mock-twitter-action
            (twitter/tweet to-tweet-text tweet-id))))))

(defn tweet [topicid]
    (let [
          lang "en"
          answer (pick-answer topicid lang)
          to-tweet-text (str answer) ]
      (if answer
        (do
          (println "\n" (str "[" topicid " " lang "]") "tweet -->" to-tweet-text)
          (if-not mock-twitter-action
            (twitter/tweet to-tweet-text))))))

(defn retweet [original-tweet]
  (let [
        message (:text original-tweet)
        tweet-id (:id original-tweet) ]
    (println "\n" (str "[" tweet-id "]") ":" message "\n --> [Retweet]")
    (if-not mock-twitter-action
      (twitter/retweet tweet-id))))

(defn star [original-tweet]
  (let [
        message (:text original-tweet)
        tweet-id (:id original-tweet) ]
    (println "\n" (str "[" tweet-id "]") ":" message "\n --> *Starred*")
    (if-not mock-twitter-action
      (twitter/star tweet-id))))

;;;;

(defn route-action [topic]
  (let [id (:topic topic)
        action (:action topic)]
    (case action
      "reply" #(reply id %)
      "star" star
      "retweet" retweet
      "tweet" (fn [_] (tweet id))
      (do (println "Unable to find action for " action) (fn [_] nil)))))

(defn standalone-topic? [topic]
  (let [id (:topic topic)
        action (:action topic)]
    (case action
      "tweet" true
      false)))

;;;;; topic stream

(defn score-tweet [tweet]
  (- (-> tweet :user :followers_count) (/ (-> tweet :user :friends_count) 3)))

(defn pick-interesting-tweet [tweets]
  (get
    (vec (reverse (sort-by score-tweet tweets))) ; sort by best tweets
    (int (* (rand) (rand) (count tweets))) ; distribution that will favorize the best tweets
    ))

(defn tweet-matches-topic [tweet topic]
  (let
    [keywords (:keywords topic)
     exclude (:exclude topic)]
    (if-let
      [ text (.toLowerCase (:text tweet)) ]
      (and
        (not (-> tweet :favorited))
        (not (-> tweet :retweeted))
        (not (-> tweet :possibly_sensitive))
        (not (= (-> tweet :user :screen_name) (:twitter-screen-name config)))
        (some #(.contains text (.toLowerCase %)) keywords)
        (every? #(not (.contains text (.toLowerCase %))) exclude)))))

(defn topic-channel [topic tweets-mult]
  (if (standalone-topic? topic)
    ; channels from timeouts
    (let [c (chan)
          rate (* 1000 (get topic :rate 60)) ]
      (go-loop
        [i 1]
        (let []
          (<! (timeout rate))
          (>! c i))
        (recur (+ i 1)))
      c)

    ; channels from tweets
    (let [c (chan)
          id (:topic topic)
          rate (* 1000 (get topic :rate 60))
          filtered (filtered-channel tweets-mult #(tweet-matches-topic % topic) (sliding-buffer 100))
          buffered (time-bufferize filtered rate) ]
      (go-loop
        []
        (let [tweets (<! buffered)
              tweet (pick-interesting-tweet tweets)
              _ (println id "received" (count tweets) "tweets." (if-not tweet " No tweet handled." ""))]
          (if tweet (>! c tweet)))
        (recur))
      c)))



(defn -main
  "bot main function."
  [& args]

  (let
    [keywords  (->> messages
                    vals
                    (map :keywords)
                    flatten
                    (map #(.toLowerCase %))
                    set)

     _ (println "Creating stream for keywords:" (clojure.string/join ", " keywords))

     tweets-chan (twitter/stream keywords)
     tweets-broadcast (mult tweets-chan)]

    (doseq [topicname (keys messages)]

      (let [topic (get messages topicname)
            _ (println "Handling topic:" topicname)
            tweet-action (route-action topic)
            chan (topic-channel topic tweets-broadcast)]

        (go-loop
          []
          (tweet-action (<! chan))
          (recur))))))


;
;  This file is part of twitbot.
; 
;  Copyright 2015 Zengularity
; 
;  twitbot is free software: you can redistribute it and/or modify
;  it under the terms of the AFFERO GNU General Public License as published by
;  the Free Software Foundation.
; 
;  twitbot is distributed "AS-IS" AND WITHOUT ANY WARRANTY OF ANY KIND,
;  INCLUDING ANY IMPLIED WARRANTY OF MERCHANTABILITY,
;  NON-INFRINGEMENT, OR FITNESS FOR A PARTICULAR PURPOSE. See
;  the AFFERO GNU General Public License for the complete license terms.
; 
;  You should have received a copy of the AFFERO GNU General Public License
;  along with twitbot.  If not, see <http://www.gnu.org/licenses/agpl-3.0.html>
;
