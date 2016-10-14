(defproject smart-playlist "0.1.0-SNAPSHOT"
  :description "Generates interesting iTunes playlists"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [clj-time "0.12.0"]
                 [com.github.bdesham/clj-plist "0.10.0"]]
  :main smart-playlist.core
  :uberjar-name "smart-playlist-standalone.jar"
  :profiles {:uberjar {:aot :all}})
