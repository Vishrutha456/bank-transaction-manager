@echo off
REM Compile and run the automated test suite.
cd /d "%~dp0"
if exist out rmdir /s /q out
javac --release 17 -d out -sourcepath "src/main/java;src/test/java" src/test/java/com/codenova/bank/BankServiceTest.java || exit /b 1
java -cp out com.codenova.bank.BankServiceTest
