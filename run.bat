@echo off
REM Compile and run.  run.bat          -> interactive menu
REM                   run.bat --demo   -> scripted feature walkthrough
cd /d "%~dp0"
if exist out rmdir /s /q out
javac --release 17 -d out -sourcepath src/main/java src/main/java/com/codenova/bank/Main.java || exit /b 1
java -cp out com.codenova.bank.Main %*
