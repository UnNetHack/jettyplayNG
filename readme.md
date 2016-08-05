# Jettyplay

Jettyplay is a program designed for playing back "ttyrecs", or terminal recordings. It is written in Java (specifically, version 7), to enable it to be used by nontechnical users without requiring a separate compilation step or knowledge of how to use the command line. Additionally, it has more features than the vast majority of ttyrec players: for instance, it can load ttyrecs in the background (meaning that there is no need to wait for the entire ttyrec to parse before playing it), without sacrificing the ability to seek or rewind.

Many features of jettyplay (in particular, playlists) are currently unfinished; as such, it should be treated as alpha-quality software. However, the many features that do work typically work quite well.

To run jettyplay, provide the precompiled binary (or a binary you compile yourself) to a Java 7 runtime. This can normally be done via double-clicking on it in a GUI (marking it executable if necessary for the operating system). Alternatively, from the command line, run:

```
java -jar jettyplay.jar
```
