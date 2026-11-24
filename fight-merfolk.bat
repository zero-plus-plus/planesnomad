@echo off
rem Planesnomad: fight the Lorwyn easy Merfolk enemy (data\encounters\lorwyn\easy\merfolk-tidecaller.json)
rem with the red starter deck (decks\Starter - Red.dck). Build first with:  mvn compile
rem Uses classpath.txt (the libraries' paths on this PC), like shortcuts\start command.txt.
rem Another encounter:  fight-merfolk.bat <plane>/<tier>/<name>   e.g.  lorwyn/easy/merfolk-tidecaller
setlocal
cd /d "%~dp0"
set "ENCOUNTER=%~1"
if "%ENCOUNTER%"=="" set "ENCOUNTER=lorwyn/easy/merfolk-tidecaller"
for /f "usebackq delims=" %%a in ("classpath.txt") do set "CP=%%a"
java -cp "target\classes;%CP%" com.planesnomad.gui.MatchViewerApp encounter %ENCOUNTER%
if errorlevel 1 pause
