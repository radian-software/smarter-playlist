(ns smarter-playlist.core
  (:gen-class)
  (:require [clj-time.core :as t]
            [clojure.java.shell :refer [sh]]
            [clojure.string :as str]
            [com.github.bdesham.clj-plist :refer [parse-plist]]
            [smarter-playlist
             [stats :as stats]
             [util :as util :refer [defconfig]]]))

;;;; Configuration variables

(defconfig age-summary
  (stats/mean-median))

(defconfig age-weighting
  (stats/log-power))

(defconfig length-distribution
  (-> (stats/normal :mean 8 :stdev 5)
    (stats/bounded :min 0 :max 6)
    (stats/rounded)))

(defconfig fraction-distribution
  (-> (stats/normal :mean 0.5 :stdev 0.3)
    (stats/bounded :min 0 :max 1)))

(defconfig playlist-length
  100)

(defconfig playlist-name
  "Smarter Playlist")

;;;; Reading the library from disk

(defn itunes-library
  "Reads the iTunes XML file and returns a Clojure data structure
  containing information about the user's iTunes library."
  []
  (->> (System/getProperty "user.home")
    (format "%s/Music/iTunes/iTunes Music Library.xml")
    (parse-plist)))

;;;; Getting attributes of songs

(defn title
  "Returns the title of a song."
  [song]
  (get song "Name"))

(defn album
  "Returns the album name of a song."
  [song]
  (get song "Album"))

(defn track-number
  "Returns the track number of a song."
  [song]
  (get song "Track Number"))

(defn disc-number
  "Returns the disc number of a song."
  [song]
  (get song "Disc Number"))

(defn comments
  "Returns the contents of a song's Comments field."
  [song]
  (get song "Comments"))

(defn pseudo-track-number
  "Returns an object that can be used to sort songs according to the
  order in which they appear in iTunes within a single album."
  [song]
  [(disc-number song)
   (nil? (track-number song))
   (or (track-number song)
       (title song))])

;;;; Reshaping the library

(defn library->songs
  "Converts the data structure returned by itunes-library into a
  sequence of songs, each of which can be passed to title, album,
  track-number, etc."
  [library]
  (-> library
    (get "Tracks")
    (vals)
    (->> (filter #(re-find #"\\Tag2\\"
                           (comments %))))))

(defn songs->albums
  "Converts a sequence of songs into a map from album names to
  sequences of songs. Within an album, the songs with track numbers
  come first, followed by the songs without track numbers (in
  alphabetical order). This is consistent with their display order in
  iTunes. Disks are not taken into account."
  [songs]
  (->> songs
    (group-by album)
    (reduce-kv (fn [album-map album songs]
                 (assoc album-map album
                        (sort-by pseudo-track-number
                                 songs)))
               {})))

;;;; Calculating things about songs

(defn age
  "Determine how long it has been since a song was last played, in
  seconds. Returns default-age if the song has never been played."
  [song default-age]
  (if-let [play-date (get song "Play Date UTC")]
    (-> play-date
      (t/interval (t/now))
      (t/in-seconds))
    default-age))

;;;; Generating playlists

(defn select-album
  "Returns the name of a randomly chosen album."
  [album-map max-age]
  (->> album-map
    (reduce-kv (fn [[album-names weights] album-name songs]
                 [(conj album-names album-name)
                  (conj weights
                        (age-summary
                          (map #(age % max-age)
                               songs)))])
               [[] []])
    (apply stats/weighted-rand-nth)))

(defn select-songs
  "Returns a random selection of songs, ordered by
  `pseudo-track-number`."
  [songs max-age]
  (let [initial-index (stats/weighted-rand-nth
                        (range (count songs))
                        (map #(age % max-age)
                             songs))
        indices (->> initial-index
                  (iterate (rand-nth [inc dec]))
                  (map #(mod % (count songs)))
                  (distinct)
                  (take (min (length-distribution)
                             (count songs))))
        songs (map #(nth songs %) indices)]
    (loop [chosen-songs []
           songs songs
           remaining-to-choose (* (count songs)
                                  (fraction-distribution))]
      (if (pos? remaining-to-choose)
        (let [song (stats/weighted-rand-nth
                     songs
                     (map #(age % max-age)
                          songs))]
          (recur (conj chosen-songs song)
                 (remove #{song} songs)
                 (dec remaining-to-choose)))
        (sort-by pseudo-track-number chosen-songs)))))

(defn create-playlist
  "Creates a playlist (vector of songs) and returns it."
  [album-map]
  (let [max-age (->> album-map
                  (vals)
                  (apply concat)
                  (keep #(age % nil))
                  (apply max 0))]
    (loop [playlist []
           album-map album-map]
      (if (< (count playlist) playlist-length)
        (let [album (select-album album-map max-age)
              songs (select-songs (get album-map album) max-age)]
          (recur (into playlist songs)
                 (let [album-map (update album-map album
                                         (fn [original-songs]
                                           (vec
                                             (remove (set songs)
                                                     original-songs))))]
                   (cond-> album-map
                     (empty? (get album-map album))
                     (dissoc album)))))
        (subvec playlist 0 playlist-length)))))

;;;; Interacting with AppleScript

(defn escape
  "Escapes a string for embedding within an AppleScript string
  literal."
  [s]
  (str/replace s "\"" "\\\""))

(defn run-applescript
  "Shells out to run AppleScript code. If the code is too large to be
  runnable as a shell command, writes it to a temporary file before
  executing it."
  [code]
  (if (<= (count code) 100000)
    (sh "osascript" "-e" code)
    (let [file "/tmp/smarter-playlist-applescript"]
      (spit file code)
      (sh "osascript" file))))

(defn format-applescript
  "Convenience function for generating AppleScript code to pass to
  `run-applescript`."
  [lines+args]
  (->> lines+args
    (map #(apply format %))
    (str/join \newline)))

;;;; Controlling iTunes via AppleScript
;;; Based on http://apple.stackexchange.com/a/77626/184150
;;; and lots of other random websites

(defn make-empty-playlist
  "Creates an empty iTunes playlist with the given name, or clears
  the playlist by that name if it already exists."
  []
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
  "Adds the given songs to the given playlist. WARNING: If there are
  two or more songs in your iTunes library with the same title and
  album name, then this function might add the wrong one to the
  playlist."
  [songs]
  (run-applescript
    (format-applescript
      `[["tell application \"iTunes\""]
        ~@(map (fn [song]
                 ["    duplicate (first item of (tracks whose (name is \"%1$s\") and (album is \"%2$s\"))) to playlist \"%3$s\""
                  (escape (title song))
                  (escape (album song))
                  (escape playlist-name)])
               songs)
        ["end tell"]])))

;;;; Main entry point

(defn create-and-save-playlist
  "Create a playlist (see `create-playlist`), make an empty iTunes
  playlist of the given name (see `make-empty-playlist`), and fill it
  with the songs in the generated playlist (see
  `add-songs-to-playlist`)."
  []
  (println)
  (println "Making an empty playlist in iTunes.")
  (make-empty-playlist)
  (println "Reading iTunes library.")
  (let [songs (library->songs (itunes-library))]
    (if (<= playlist-length (count songs))
      (let [album-map (songs->albums songs)]
        (println "Creating playlist.")
        (let [playlist (create-playlist album-map)]
          (println "Exporting playlist to iTunes.")
          (add-songs-to-playlist playlist)))
      (printf "You can't make a playlist of length %d if your library only has %d songs!%n"
              playlist-length (count songs)))))

(defn main
  "Like `-main`, but doesn't call `shutdown-agents` and is therefore
  suitable for testing."
  [& args]
  (binding [*ns* (the-ns 'smarter-playlist.core)]
    (util/with-parsed-options args create-and-save-playlist)))

(defn -main
  "Main entry point. Command line arguments are parsed and the
  configuration vars are bound accordingly, then
  `create-and-save-playlist` is called."
  [& args]
  (apply main args)
  ;; The following prevents a minute-long hang that keeps Clojure from
  ;; exiting after finishing:
  (shutdown-agents))
