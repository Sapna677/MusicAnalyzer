@echo off
title Launching Music Analyzer & Player 🌸
echo =======================================================
echo     🌸 Music Analyzer & Player Launch Script 🌸
echo =======================================================
echo.
echo Compiling and executing project...
echo.

"C:\Users\HP\.m2\wrapper\dists\apache-maven-3.8.5-bin\5i5jha092a3i37g0paqnfr15e0\apache-maven-3.8.5\bin\mvn.cmd" clean compile exec:java -Dexec.mainClass="Main"

if %ERRORLEVEL% neq 0 (
    echo.
    echo 😿 Error occurred during execution. Press any key to exit.
    pause > nul
)
