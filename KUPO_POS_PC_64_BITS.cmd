@echo off
REM KUPO POS - Lanzador para PC Windows 64 bits (version GitHub)
REM Abre KUPO POS como ventana de aplicacion en Google Chrome o Edge.
REM No instala nada ni copia la base de datos: usa la app web de GitHub
REM y los datos viven en la licencia de KUPO POS en la nube.
REM La impresion sale con la ventana de Windows (hay que elegir la
REM impresora POS-80); NO es impresion silenciosa.
set "URL=https://josealcocer070-gif.github.io/Kupo-Pos/index.html"
if exist "%ProgramFiles%\Google\Chrome\Application\chrome.exe" goto :chrome64
if exist "%ProgramFiles(x86)%\Google\Chrome\Application\chrome.exe" goto :chrome32
if exist "%ProgramFiles%\Microsoft\Edge\Application\msedge.exe" goto :edge64
if exist "%ProgramFiles(x86)%\Microsoft\Edge\Application\msedge.exe" goto :edge32
echo No se encontro Google Chrome ni Microsoft Edge en este equipo.
echo Instala Chrome o Edge y vuelve a intentarlo.
pause
exit /b
:chrome64
start "" "%ProgramFiles%\Google\Chrome\Application\chrome.exe" --app="%URL%"
exit /b
:chrome32
start "" "%ProgramFiles(x86)%\Google\Chrome\Application\chrome.exe" --app="%URL%"
exit /b
:edge64
start "" "%ProgramFiles%\Microsoft\Edge\Application\msedge.exe" --app="%URL%"
exit /b
:edge32
start "" "%ProgramFiles(x86)%\Microsoft\Edge\Application\msedge.exe" --app="%URL%"
exit /b
