@echo off
setlocal EnableExtensions
cd /d "%~dp0"
title Mali 2 Inspector Android - Compilar APK
if not exist "scripts\BUILD_WINDOWS.ps1" (
    echo [ERRO] scripts\BUILD_WINDOWS.ps1 nao encontrado.
    pause
    exit /b 1
)
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\BUILD_WINDOWS.ps1"
set "RC=%ERRORLEVEL%"
if not "%RC%"=="0" echo [ERRO] Compilacao interrompida. Leia o erro acima.
echo.
pause
exit /b %RC%
