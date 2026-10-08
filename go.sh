#! /usr/bin/env bash
cd "$(dirname $(realpath $0))" || exit 1
command -v javac >/dev/null || exit 1
rm -rf bin/
test -f src/boxenluther/emulia/DEBUG.java &&
javac -classpath src/ -d bin/ src/boxenluther/emulia/DEBUG.java
javac -classpath src/ -d bin/ src/boxenluther/emulia/Main.java &&
sudo java -classpath bin/ boxenluther.emulia.Main "$@"
