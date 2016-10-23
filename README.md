# Smarter Playlist

When I want to listen to music from iTunes library, I basically have
two options:

* listen to music from only one album, in order or on shuffle; or
* listen to music from my entire library, on shuffle.

Neither of these options are really ideal for me. Many of my albums
are quite long (several hours), so I can get bored with listening to
the same style of music for too long. And shuffling through my entire
library means there's no continuity between sequential songs. Besides,
there's a lot of good music in my library that I forget I have, so I'd
like shuffle to be biased toward less recently played songs.

To solve all of these problems, I present **Smarter Playlist**&trade;.
This script will create an iTunes playlist that:

* shuffles through your entire iTunes library, but
* tends to prefer songs that you have listened to less recently, and
* has some inertia, and therefore likes to play a few sequential songs from an
  album before switching to another album.

## Table of contents

<!-- markdown-toc start - Don't edit this section. Run M-x markdown-toc-generate-toc again -->


- [Smarter Playlist](#smarter-playlist)
    - [Table of contents](#table-of-contents)
    - [Setup](#setup)
    - [Basic usage](#basic-usage)
    - [Algorithm description](#algorithm-description)
    - [Advanced usage](#advanced-usage)
    - [Portability](#portability)
    - [Implementation notes](#implementation-notes)
    - [Contributing](#contributing)

<!-- markdown-toc end -->

## Setup

* Clone or download this repository.
* Make sure you have a [JDK] (or at least a JRE) installed.
* Install Leiningen using [Homebrew][homebrew] (`brew install leiningen`) or
  from [the official website](leiningen).

On OS X, you can use the [Radian] setup script to take care of all the
dependencies. You *are* using OS X, right? (Smarter Playlist uses
AppleScript to interface with iTunes and will only work on OS X.)

```
% git clone https://github.com/raxod502/radian.git
% radian/scripts/setup.sh only leiningen
```

## Basic usage

Create a playlist by executing `lein run` in the `smarter-playlist`
directory:

```
% lein run
age-summary = (stats/mean-median) [default]
age-weighting = (stats/log-power) [default]
fraction-distribution = (-> (stats/normal :mean 0.5 :stdev 0.3) (stats/bounded :min 0 :max 1)) [default]
length-distribution = (-> (stats/normal :mean 8 :stdev 5) (stats/bounded :min 0 :max 6) (stats/rounded)) [default]
playlist-length = 100 [default]
playlist-name = "Smarter Playlist" [default]

Making an empty playlist in iTunes.
Reading iTunes library.
Creating playlist.
Exporting playlist to iTunes.
```

This will add a playlist named "Smarter Playlist" to iTunes,
overwriting any previously existing playlist by that name.

Specify the name and/or length of the playlist by passing (optional)
keyword arguments:

```
% lein run --playlist-length 10 --playlist-name '"The Best Playlist"'
age-summary = (stats/mean-median) [default]
age-weighting = (stats/log-power) [default]
fraction-distribution = (-> (stats/normal :mean 0.5 :stdev 0.3) (stats/bounded :min 0 :max 1)) [default]
length-distribution = (-> (stats/normal :mean 8 :stdev 5) (stats/bounded :min 0 :max 6) (stats/rounded)) [default]
playlist-length = 10
playlist-name = "The Best Playlist"

Making an empty playlist in iTunes.
Reading iTunes library.
Creating playlist.
Exporting playlist to iTunes.
```

Please note the double quotes around the playlist name. The keyword
arguments are allowed to be arbitrary Clojure forms which will be
evaluated. So, to specify a string, you must pass the double quotes as
part of the argument.

After you read the algorithm description below, you may want to
configure some of the other parameters. For instance, the following
inhibits Smarter Playlist's default behavior of sometimes skipping
songs in an album rather than taking them sequentially, and increases
the average run length considerably:

```
% lein run --fraction-distribution '(constantly 1)' --length-distribution '(-> (stats/normal :mean 20 :stdev 5) (stats/bounded :min 0) (stats/rounded))'
age-summary = (stats/mean-median) [default]
age-weighting = (stats/log-power) [default]
fraction-distribution = (constantly 1)
length-distribution = (-> (stats/normal :mean 20 :stdev 5) (stats/bounded :min 0) (stats/rounded))
playlist-length = 100 [default]
playlist-name = "Smarter Playlist" [default]

Making an empty playlist in iTunes.
Reading iTunes library.
Creating playlist.
Exporting playlist to iTunes.
```

## Algorithm description

The age of each album is computed using the `age-summary` function.
Then a random album is selected, with the weighting for each album
determined based on its age by the `age-weighting` function.

Once an album is selected, a "run length" is selected using the
`length-distribution` function, and a random song from the album is
selected with the weighting determined again using `age-weighting`.
The "run" of songs under consideration begins with the chosen song,
extends either forward or backward in the album (with equal
probability, and wrapping around if necessary), and has the chosen
length (subject to the constraint that the length must be at most the
number of songs in the album).

Now a fraction between 0 and 1 is selected using the
`fraction-distribution` function, and the run length is multiplied by
the fraction to determine the number of songs from the run that will
actually be added to the playlist. These songs are selected randomly,
without replacement, with weighting given again by the `age-weighting`
function.

Before the selected songs are added to the playlist, they are sorted
by their order in the original album. All sorting is performed
according to iTunes' rules: tracks are sorted first by disc number,
then by track number, then by title for tracks lacking a track number
(with songs lacking track numbers coming after songs with track
numbers, within a given album).

The selected songs are then added to the playlist and removed from the
in-memory index of the iTunes library. (This ensures that no single
playlist will ever contain duplicates, and implies that the maximum
allowable `playlist-length` is the number of songs in your iTunes
library.) The playlist is then extended by continuing to select random
albums until it reaches `playlist-length`. If necessary, the playlist
is trimmed to have exactly that length.

## Advanced usage

The available options and their default values are printed whenever
you run Smarter Playlist. They can all be set in the same way, namely

```
--some-option '(some Clojure data structure or form)'
```

The options are evaluated after they are parsed. You have access to
anything available in the `smarter-playlist.core` namespace, including
the `smarter-playlist.stats` functions used in the default values of
many of the options.

To be precise:

* `age-summary` should be a function taking a sequence of ages
  (doubles) and returning a single age (double) summarizing them.

* `age-weighting` should be a function taking an age (double) and
  returning a weighting (double) for the album or song with that age
  in random selection.

* `fraction-distribution` and `length-distribution` should be
  functions of no arguments returning fractions (doubles) and lengths
  (longs) respectively.

* `playlist-length` should be a long.

* `playlist-name` should be a string.

## Portability

You can also create a JAR that can be run from anywhere that has Java
installed (without a need for Leiningen or the source code):

```
% lein uberjar
Compiling smarter-playlist.core
Compiling smarter-playlist.stats
Compiling smarter-playlist.util
Created /Users/raxod502/Desktop/Code/Clojure/smart-playlist/target/smarter-playlist-0.1.0-SNAPSHOT.jar
Created /Users/raxod502/Desktop/Code/Clojure/smart-playlist/target/smarter-playlist-standalone.jar
% java -jar target/smarter-playlist-standalone.jar --playlist-length 10
age-summary = (stats/mean-median) [default]
age-weighting = (stats/log-power) [default]
fraction-distribution = (-> (stats/normal :mean 0.5 :stdev 0.3) (stats/bounded :min 0 :max 1)) [default]
length-distribution = (-> (stats/normal :mean 8 :stdev 5) (stats/bounded :min 0 :max 6) (stats/rounded)) [default]
playlist-length = 10
playlist-name = "Smarter Playlist" [default]

Making an empty playlist in iTunes.
Reading iTunes library.
Creating playlist.
Exporting playlist to iTunes.
```

## Implementation notes

* Uses AppleScript inlined in `smarter-playlist.core`. Because
  AppleScript is horrible, songs are added to the iTunes playlist one
  at a time, which is slow. I would love to find a way to get iTunes
  to do them all at once.

* Uses a custom `defconfig` macro to define configuration parameters
  (dynamic vars that can be configured using command-line options).
  The macro places special metadata on the created vars to identify
  them as configuration parameters and to allow the literal form
  passed to `def` to be reported in the command-line output rather
  than the less-pretty evaluated form. The advantage of doing this is
  that adding a new configuration variable is as easy as adding a new
  `defconfig` declaration to `smarter-playlist.core` (the var will be
  automatically detected by the option parser).

## Contributing

Please do, if you want! Issues and pull requests are welcome.

[core]: src/smart_playlist/core.clj
[homebrew]: http://brew.sh/
[jdk]: http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html
[leiningen]: http://leiningen.org/
[radian]: https://github.com/raxod502/radian
