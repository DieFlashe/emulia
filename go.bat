@echo off
cd /d "%~dp0"
IF ERRORLEVEL 1 exit /B 1

echo Checking ...
where /q javac.exe
IF ERRORLEVEL 1 (
    echo Can not find javac.exe in PATH, abort.
    pause
    exit /B 1
)
cls

echo Cleaning ...
if exist bin\ rmdir /s /q bin\
mkdir bin\
cls

if not exist src\boxenluther\emulia\DEBUG.java goto skipDebug
javac -classpath src\ -d bin\ src\boxenluther\emulia\DEBUG.java
IF ERRORLEVEL 1 (
    echo Debug failure, abort.
    pause
    exit /B 1
)
:skipDebug

echo Compiling ...
javac -classpath src\ -d bin\ src\boxenluther\emulia\Main.java
IF ERRORLEVEL 1 (
    echo Compiling failure, abort.
    pause
    exit /B 1
)
cls

start "Emulia" java -classpath bin\ boxenluther.emulia.Main %*
