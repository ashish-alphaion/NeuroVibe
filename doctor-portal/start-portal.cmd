@echo off
set "PORTAL_DIR=%~dp0"
echo Starting NeuroVibe Doctor Portal...
start "NeuroVibe Portal Server" /min /D "%PORTAL_DIR%" cmd /k node dev-server.mjs
timeout /t 2 /nobreak >nul
start "" "http://127.0.0.1:4173"
echo The portal has been opened in your browser.
echo Close the minimized "NeuroVibe Portal Server" window to stop it.
timeout /t 3 /nobreak >nul
