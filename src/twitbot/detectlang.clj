(ns twitbot.detectlang
  (:use twitbot.config)
  (:require [clj-http.client :as http]))

(defn identify [tweet-text]
  (let [key (:detectlang-key config)
        params {:q tweet-text :key key}
        url "http://ws.detectlanguage.com/0.2/detect"
        response (:body (http/get url {:query-params params :accept :json :as :json}))]
    (:language (first (-> response :data :detections)))))
