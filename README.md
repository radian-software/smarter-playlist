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
* has some inertia, and likes to play a few sequential songs from an
  album before switching to another album.

## Setup

* Clone (`git clone https://github.com/raxod502/smarter-playlist.git`)
  or download this repository.
* Make sure you have a [JDK] (or at least a JRE) installed.
* Install Leiningen using [Homebrew] (`brew install leiningen`) or
  from [the official website](leiningen).

## Basic usage

Create a playlist by executing `lein run` in the `smarter-playlist`
directory:

```
% lein run
Creating playlist of length 100 with strategy weights {:next-in-album 100, :random-in-album 2, :random 20} and saving it as "Smarter Playlist"... done.
```

This will add a playlist named "Smarter Playlist" to iTunes,
overwriting any previously existing playlist by that name.

Specify the name and/or length of the playlist by passing (optional)
keyword arguments:

```
% lein run :length 10 :playlist-name \"The Best Playlist\"
Creating playlist of length 10 with strategy weights {:next-in-album 100, :random-in-album 2, :random 20} and saving it as "The Best Playlist"... done.
```

## Advanced usage

Change the strategy weight map (this affects how often a random song
will be selected instead of the next one from the current album; see
the documentation of `next-song` in [`core.clj`](core) for more
details):

```
% lein run :strategy-weights \{:next-in-album 0 :random-in-album 10 :random 1\}
Creating playlist of length 100 with strategy weights {:next-in-album 0, :random-in-album 10, :random 1} and saving it as "Smarter Playlist"... done.
```

Create a JAR that can be run from anywhere that has Java installed (without a need for Leiningen or the source code):

```
% lein uberjar
Compiling smarter-playlist.core
Compiling smarter-playlist.util
Created smarter-playlist/target/smarter-playlist-0.1.0-SNAPSHOT.jar
Created smarter-playlist/target/smarter-playlist-standalone.jar
% java -jar target/smarter-playlist-standalone.jar :length 20
Creating playlist of length 20 with strategy weights {:next-in-album 100, :random-in-album 2, :random 20} and saving it as "Smarter Playlist"... done.
```

## Contributing

Please do, if you want! Issues and pull requests are welcome.

[core]: src/smart_playlist/core.clj
[homebrew]: http://brew.sh/
[jdk]: http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html
[leiningen]: http://leiningen.org/
