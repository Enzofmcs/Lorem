@echo off
setlocal
cd /d "%~dp0..\.."
title Lorem - Carregar Ipsums no historico normal

node -e "process.exit(Number(process.versions.node.split('.')[0]) < 24 ? 1 : 0)" >nul 2>&1
if errorlevel 1 (
    echo Instale o Node.js 24 pelo site https://nodejs.org/ e abra este arquivo novamente.
    pause
    exit /b 1
)

if not exist "firebase\node_modules\firebase-tools\package.json" (
    call npm --prefix firebase ci
    if errorlevel 1 (
        pause
        exit /b 1
    )
)

call npm --prefix firebase run seed-normal-history -- %*
set "lorem_seed_result=%errorlevel%"
pause
exit /b %lorem_seed_result%
