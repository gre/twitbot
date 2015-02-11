
(ns twitbot.stream
  (:require
    [clojure.core.async :as async :refer [>! <! filter< alts! sliding-buffer tap mult chan thread timeout go go-loop alts!!]]))


(defn time-bufferize [in t]
  (let [c (chan)]
    (go
      (loop
        [buf []
         timer (timeout t)]

        (let [[value port] (alts! [in timer])
              timed-out (= port timer) ]
          (if (= port timer) ; timed out
            (do
              (>! c buf)
              (recur [] (timeout t)))

            (recur (conj buf value) timer)))))
    c))

(defn filtered-channel [chan-input predicate & [buf-or-n]]
  (let [c (if buf-or-n (chan buf-or-n) (chan))]
    (tap chan-input c)
    (filter< predicate c)))
