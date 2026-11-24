@echo off
rem Planesnomad (test builds). Double-click to play; it updates itself when a new build is out.
setlocal
cd /d "%~dp0"
rem A launcher update downloaded last time is put in place before starting.
if exist launcher-new.jar move /y launcher-new.jar launcher.jar >nul
where javaw >nul 2>nul
if errorlevel 1 (
  echo Planesnomad needs Java 17 or newer.
  echo Get it from https://adoptium.net ^(JDK 17 or later^), then double-click Planesnomad.bat again.
  pause
  exit /b 1
)
start "" javaw -jar launcher.jar
