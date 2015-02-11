(defproject twitbot "0.1.0-SNAPSHOT"
  :description "twitbot"
  :url "http://github.com/gre/twitbot"
  :license {:name "Affero General Public License"
            :url "https://gnu.org/licenses/agpl.html"}
  :dependencies [
    [org.clojure/clojure "1.6.0"]
    [org.clojure/core.async "0.1.346.0-17112a-alpha"]
    [twitter-api "0.7.8"]
    [twitter-streaming-client "0.3.2"]
    [overtone "0.9.1"]
    ]
  :main ^:skip-aot twitbot.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
