@echo off
REM ---------------------------------------------------------------
REM  JobRadar  -  baut die Windows-Anwendung
REM
REM  Voraussetzung: JDK 21 oder neuer installiert, JAVA_HOME gesetzt.
REM  Maven wird nicht gebraucht, wenn du IntelliJ nutzt - dort reicht
REM  ein Rechtsklick auf pom.xml. Sonst: https://maven.apache.org
REM ---------------------------------------------------------------

setlocal
set NAME=JobRadar
set VERSION=1.0.0

echo.
echo [1/3] Baue das JAR ...
call mvn -B clean package
if errorlevel 1 goto fehler

echo.
echo [2/3] Raeume alten Build weg ...
if exist dist rmdir /s /q dist
mkdir dist\lib
REM Nur das fertige JAR einpacken, nicht den Rest aus target\
copy /y target\jobradar-%VERSION%.jar dist\lib\ >nul
if errorlevel 1 goto fehler

echo.
echo [3/3] Verpacke zur Windows-Anwendung ...
REM app-image statt exe-Installer: braucht kein WiX und keine Adminrechte.
REM Die Java-Laufzeit wird mit eingepackt - auf dem Zielrechner muss
REM nichts installiert sein.
jpackage ^
  --type app-image ^
  --name %NAME% ^
  --app-version %VERSION% ^
  --input dist\lib ^
  --main-jar jobradar-%VERSION%.jar ^
  --dest dist ^
  --vendor "BeWater" ^
  --description "Automatische Stellensuche" ^
  --java-options "-Xmx256m" ^
  --java-options "-Dfile.encoding=UTF-8" ^
  --java-options "--enable-native-access=ALL-UNNAMED"
if errorlevel 1 goto fehler

echo.
echo ================================================================
echo  Fertig.
echo.
echo  Die Anwendung liegt in:  dist\%NAME%\
echo  Starten mit:             dist\%NAME%\%NAME%.exe
echo.
echo  Fuer eine Verknuepfung auf dem Desktop:
echo  Rechtsklick auf %NAME%.exe  ^>  Senden an  ^>  Desktop
echo ================================================================
echo.
goto ende

:fehler
echo.
echo !! Build fehlgeschlagen. Zeilen oben lesen.
echo !! Pruefe zuerst:  java -version   (muss 21 oder hoeher sein)
echo.
exit /b 1

:ende
endlocal
