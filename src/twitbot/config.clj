(ns twitbot.config)

(require 'clojure.edn)

(def config (clojure.edn/read-string (slurp "config.edn")))
