#!/usr/bin/env bash
# Compile and run.  ./run.sh          -> interactive menu
#                   ./run.sh --demo   -> scripted feature walkthrough
set -e
cd "$(dirname "$0")"
rm -rf out
javac --release 17 -d out -sourcepath src/main/java src/main/java/com/codenova/bank/Main.java
java -cp out com.codenova.bank.Main "$@"
