#!/usr/bin/env bash
# Compile and run the automated test suite (no external libraries needed).
set -e
cd "$(dirname "$0")"
rm -rf out
javac --release 17 -d out -sourcepath "src/main/java:src/test/java" src/test/java/com/codenova/bank/BankServiceTest.java
java -cp out com.codenova.bank.BankServiceTest
