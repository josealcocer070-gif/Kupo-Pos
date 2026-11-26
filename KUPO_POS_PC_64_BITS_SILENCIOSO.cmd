@echo off
REM KUPO POS - Lanzador de PRUEBA con impresion silenciosa (version GitHub)
REM Abre KUPO POS como ventana de aplicacion en Google Chrome o Edge y
REM manda el ticket directo a la impresora predeterminada SIN la ventana
REM de Windows. Es una PRUEBA: hay que probarla en la terminal.
REM
REM ANTES DE PROBARLO, 2 cosas en la terminal:
REM  1. Poner la POS-80 como impresora PREDETERMINADA de Windows
REM     (Panel de control - Dispositivos e impresoras - clic derecho
REM     en POS-80 - Establecer como impresora predeterminada). Si la
REM     predeterminada es otra, el ticket sale en la otra impresora.
REM  2. Cerrar por completo Chrome y Edge antes de hacer doble clic en
REM     este archivo (si el navegador ya esta abierto, ignora el modo
REM     silencioso y vuelve a salir la ventana).
REM Si el ticket no sale o sale mal, se usa el lanzador normal
REM (KUPO_POS_PC_64_BITS.cmd), que si muestra la ventana de Windows.
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
start "" "%ProgramFiles%\Google\Chrome\Application\chrome.exe" --app="%URL%" --kiosk-printing
exit /b
:chrome32
start "" "%ProgramFiles(x86)%\Google\Chrome\Application\chrome.exe" --app="%URL%" --kiosk-printing
exit /b
:edge64
start "" "%ProgramFiles%\Microsoft\Edge\Application\msedge.exe" --app="%URL%" --kiosk-printing
exit /b
:edge32
start "" "%ProgramFiles(x86)%\Microsoft\Edge\Application\msedge.exe" --app="%URL%" --kiosk-printing
exit /b
