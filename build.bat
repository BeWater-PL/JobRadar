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

REM --- JDK suchen: erst JAVA_HOME, sonst das neueste Adoptium-JDK ---------
REM  Keine festen Benutzerpfade - alles ueber Umgebungsvariablen und Suche.
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\jpackage.exe" goto :jdk_da
for /f "delims=" %%d in ('dir /b /ad /o-n "%ProgramFiles%\Eclipse Adoptium\jdk-*" 2^>nul') do (
  if exist "%ProgramFiles%\Eclipse Adoptium\%%d\bin\jpackage.exe" (
    set "JAVA_HOME=%ProgramFiles%\Eclipse Adoptium\%%d"
    goto :jdk_da
  )
)
echo !! Kein JDK mit jpackage gefunden. JAVA_HOME auf ein JDK 21 oder neuer setzen.
exit /b 1
:jdk_da
set "PATH=%JAVA_HOME%\bin;%PATH%"
echo     JDK:   %JAVA_HOME%

REM --- Maven suchen: PATH, sonst die Installation aus IntelliJ IDEA -------
set "MVN=mvn"
where mvn >nul 2>nul && goto :mvn_da
for /f "delims=" %%m in ('where /r "%ProgramFiles%\JetBrains" mvn.cmd 2^>nul') do (
  set "MVN=%%m"
  goto :mvn_da
)
for /f "delims=" %%m in ('where /r "%LOCALAPPDATA%\Programs" mvn.cmd 2^>nul') do (
  set "MVN=%%m"
  goto :mvn_da
)
echo !! Maven nicht gefunden - weder im PATH noch bei IntelliJ IDEA.
exit /b 1
:mvn_da
echo     Maven: %MVN%

echo.
echo [1/4] Baue das JAR ...
call "%MVN%" -B clean package
if errorlevel 1 goto fehler

echo.
echo [2/4] Raeume alten Build weg ...
if exist dist rmdir /s /q dist
mkdir dist\lib
REM Nur das fertige JAR einpacken, nicht den Rest aus target\
copy /y target\jobradar-%VERSION%.jar dist\lib\ >nul
if errorlevel 1 goto fehler

echo.
echo [3/4] Verpacke zur Windows-Anwendung ...
REM app-image statt exe-Installer: braucht kein WiX und keine Adminrechte.
REM Die Java-Laufzeit wird mit eingepackt - auf dem Zielrechner muss
REM nichts installiert sein.
"%JAVA_HOME%\bin\jpackage" ^
  --type app-image ^
  --name %NAME% ^
  --app-version %VERSION% ^
  --input dist\lib ^
  --main-jar jobradar-%VERSION%.jar ^
  --dest dist ^
  --vendor "BeWater" ^
  --description "Automatische Stellensuche" ^
  --icon icon\jobradar.ico ^
  --java-options "-Xmx256m" ^
  --java-options "-Dfile.encoding=UTF-8" ^
  --java-options "--enable-native-access=ALL-UNNAMED"
if errorlevel 1 goto fehler

echo.
echo [4/4] Lege die Desktop-Verknuepfung an ...
REM Desktop-Pfad ueber .NET holen - er kann auf OneDrive umgeleitet sein.
REM %~dp0 endet mit Backslash, deshalb steht hier kein weiterer davor.
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ziel = '%~dp0dist\%NAME%\%NAME%.exe';" ^
  "$desktop = [Environment]::GetFolderPath('Desktop');" ^
  "$lnk = Join-Path $desktop '%NAME%.lnk';" ^
  "$w = New-Object -ComObject WScript.Shell;" ^
  "$k = $w.CreateShortcut($lnk);" ^
  "$k.TargetPath = $ziel;" ^
  "$k.WorkingDirectory = '%~dp0dist\%NAME%';" ^
  "$k.IconLocation = '%~dp0icon\jobradar.ico';" ^
  "$k.Description = 'JobRadar - automatische Stellensuche';" ^
  "$k.Save();" ^
  "Write-Host ('    ' + $lnk);" ^
  "$alt = Join-Path (Split-Path -Parent '%~dp0'.TrimEnd('\')) '%NAME%.lnk';" ^
  "if (Test-Path $alt) { Remove-Item $alt -Force; Write-Host ('    alte Verknuepfung entfernt: ' + $alt) }"
if errorlevel 1 goto fehler

echo.
echo ================================================================
echo  Fertig.
echo.
echo  Die Anwendung liegt in:  dist\%NAME%\
echo  Starten mit:             dist\%NAME%\%NAME%.exe
echo.
echo  Die Verknuepfung mit Icon liegt auf dem Desktop.
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
