(ns smarter-playlist.core
  (:require [clj-time.core :as t]
            [clojure.java.shell :refer [sh]]
            [clojure.string :as str]
            [com.github.bdesham.clj-plist :refer [parse-plist]]
            [smarter-playlist.util :as util])
  (:gen-class))

;;;; Reading the library from disk

(defn itunes-library
  []
  (->> (System/getProperty "user.home")
    (format "%s/Music/iTunes/iTunes Music Library.xml")
    (parse-plist)))

;;;; Getting attributes of songs

(defn title
  [song]
  (get song "Name"))

(defn album
  [song]
  (get song "Album"))

(defn track-number
  [song]
  (get song "Track Number"))

(defn comments
  [song]
  (get song "Comments"))

;;;; Reshaping the library

(defn library->songs
  [library]
  (-> library
    (get "Tracks")
    (vals)
    (->> (filter #(re-find #"\\Tag2\\"
                           (comments %))))))

(defn songs->albums
  [songs]
  (->> songs
    (group-by album)
    (reduce-kv (fn [album-map album songs]
                 (assoc album-map album
                        (sort-by (fn [song]
                                   [(nil? song)
                                    (when song
                                      (title song)
                                      (track-number song))])
                                 songs)))
               {})))

;;;; Calculating things about songs

(defn age
  [song]
  (when-let [play-date (get song "Play Date UTC")]
    (-> play-date
      (t/interval (t/now))
      (t/in-seconds))))

(defn age->weight
  ([age]
   (age->weight age nil))
  ([age default]
   (if age
     (Math/pow (Math/log age) 5)
     default)))

(defn weight
  ([song]
   (weight song nil))
  ([song default]
   (age->weight (age song) default)))

;;;; Generating playlists

(defn next-song
  [songs album-map song strategy]
  (case strategy
    :next-in-album
    (->> (get album-map (album song))
      (drop-while #(not= (title %)
                         (title song)))
      (second))

    :random-in-album
    (rand-nth (get album-map (album song)))

    :random
    (let [oldest (->> songs
                   (map age)
                   (remove nil?)
                   (apply max))]
      (util/weighted-rand-nth
        songs
        (map (fn [song]
               (age->weight
                 (or (age song)
                     oldest)))
             songs)))))

(def default-strategy-weights
  {:next-in-album 100
   :random-in-album 2
   :random 20})

(defn playlist
  [songs length & [strategy-weights]]
  (let [strategy-weights (or strategy-weights
                             default-strategy-weights)
        albums (songs->albums songs)]
    (->> strategy-weights
      ((juxt keys vals))
      (apply util/weighted-rand-nth)
      (fn [])
      (repeatedly)
      (cons :random)
      (reductions (partial next-song songs albums) nil)
      (remove nil?)
      (take (dec length)))))

;;;; Interacting with AppleScript

(defn escape
  [s]
  (str/replace s "\"" "\\\""))

(defn run-applescript
  [lines]
  (->> lines
    (interleave (repeat "-e"))
    (apply sh "osascript")))

(defn format-applescript
  [lines+args]
  (map #(apply format %) lines+args))

;;;; Controlling iTunes via AppleScript

;;; Based on http://apple.stackexchange.com/a/77626/184150
(defn make-empty-playlist
  [playlist-name]
  (run-applescript
    (format-applescript
      [["tell application \"iTunes\""]
       ["    if user playlist \"%s\" exists then" playlist-name]
       ["        try"]
       ["            delete tracks of user playlist \"%s\"" playlist-name]
       ["        end try"]
       ["    else"]
       ["        make new user playlist with properties {name:\"%s\"}" playlist-name]
       ["    end if"]
       ["end tell"]])))

;;;; iTunes AppleScript

(defn add-songs-to-playlist
  [playlist-name songs]
  (run-applescript
    (format-applescript
      `[["set thePlaylistName to \"%s\"" ~(escape playlist-name)]
        [""]
        ["tell application \"iTunes\""]
        ~@(map (fn [song]
                 ["    my handleSong(\"%s\", \"%s\")"
                  (escape (title song))
                  (escape (album song))])
               songs)
        ["    my handleSong(\"Silence\", \"F-Zero Remixed\")"]
        ["end tell"]
        [""]
        ["on handleSong(theSongName, theAlbumName)"]
        ["    tell application \"iTunes\""]
        ["        set filteredSongs to (every track of library playlist 1 whose name is theSongName)"]
        ["        repeat with theFilteredSong in filteredSongs"]
        ["            if the album of theFilteredSong is theAlbumName then"]
        ["                duplicate theFilteredSong to playlist (my thePlaylistName)"]
        ["                return"]
        ["            end if"]
        ["        end repeat"]
        ["    end tell"]
        ["    display dialog \"Uh, oh. Couldn't find '\" & theSongName & \"' by '\" & theAlbumName & \"'!\""]
        ["end handleSong"]])))

;;;; Main entry point

(defn create-and-save-playlist
  [& {:keys [length playlist-name strategy-weights]
      :or {length 100
           playlist-name "Smarter Playlist"}}]
  (printf "Creating playlist of length %d with strategy weights %s and saving it as \"%s\"...%n"
          length
          (or strategy-weights default-strategy-weights)
          playlist-name)
  (flush)
  (doto playlist-name
    (make-empty-playlist)
    (add-songs-to-playlist
      (-> (itunes-library)
        (library->songs)
        (playlist length strategy-weights)))))

(defn -main
  [& args]
  (->> args
    (str/join \space)
    (util/read-all)
    (apply create-and-save-playlist))
  ;; The following prevents a minute-long hang that prevents Clojure
  ;; from exiting after finishing:
  (shutdown-agents))
