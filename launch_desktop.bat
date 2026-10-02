@echo off
cd /d "%~dp0"
title Atlas Studio - LiquidGlass
echo ========================================================
echo  Launching Atlas Studio at native screen resolution...
echo ========================================================
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\workspace.ps1" library :desktop:run
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo Application exited with code %ERRORLEVEL%
    pause
)
