(ns twitbot.spreadsheet
  (:use twitbot.config)
  (:require
    [clojure.string :as str]
    [clojure.xml :as xml]
    [clojure.data.json :as json]
    [clj-http.client :as http]))

(require 'clojure.edn)

(defn pp [o] (let [_ (clojure.pprint/pprint o)] o))

(defn tuples-to-key-value-vector [tuples]
  (->> tuples
       (group-by first)
       (map #(let [[k v] %] [k (vec (map second v))]))
       (reduce conj {})))

(defn group-by-first [group m]
  (into {} (map #(let [[k v] %] [k (first v)])
                (group-by group m))))

(defn get-index-sheet [id]
  (:body (http/get (str "https://spreadsheets.google.com/feeds/list/" id "/od6/public/values?alt=json") {:as :json})))

(defn get-sheet-cells [id sheet-index]
  (try
    (:body (http/get (str "https://spreadsheets.google.com/feeds/list/" id "/" sheet-index "/public/values?alt=json") {:as :json}))
    (catch Exception _ nil)))

(defn cell-value [cell]
  (str/trim (or (:$t cell) "")))

(defn parse-multiple-cell [cell]
  (map #(str/trim %) (filter #(not (str/blank? %)) (str/split (cell-value cell) #"[,]"))))

(defn parse-index-sheet [json]
  (->> json
       :feed
       :entry
       (map (fn [entry]
              {:topic (-> entry :gsx$topic cell-value)
               :action (-> entry :gsx$action cell-value)
               :rate (let [cell (-> entry :gsx$rate cell-value)] (if cell (try (. Integer parseInt cell) (catch Exception e nil))))
               :keywords (parse-multiple-cell (-> entry :gsx$keywords))
               :exclude (parse-multiple-cell  (-> entry :gsx$exclude))}))))

(defn parse-sheet-cells [json]
  {:topic (-> json :feed :title cell-value)
   :messages
   (tuples-to-key-value-vector (apply concat (->> json
         :feed
         :entry
         (map (fn [entry]
             (filter
               #(not (nil? %))
               (map #(let [[k v] %
                           content (cell-value v)]
                       (if (and (not (str/blank? content)) (.startsWith (name k) "gsx$"))
                         [ (keyword (.substring (name k) 4)) content ])) entry)))))))

   })

(defn gdocs-load-messages [id]
  (let [ topics (parse-index-sheet (get-index-sheet id))
        sheets (map #(if-let [sheet (get-sheet-cells id %)] (parse-sheet-cells sheet)) (range 2 (+ 2 (count topics)))) ]

    (into {} (filter #(let [[k v] %] (and k v))
    (merge-with
      merge
      (group-by-first #(keyword (:topic %)) topics)
      (group-by-first #(keyword (:topic %)) sheets))))))
