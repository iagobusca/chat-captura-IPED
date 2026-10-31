@echo off
setlocal
set "APP=%~dp0integridade-verifier.exe"
if exist "%APP%" (
  start "" "%APP%"
  exit /b 0
)
set "JAR=%~dp0integridade-verifier.jar"
where java >nul 2>nul
if errorlevel 1 (
  echo Executavel autoextraivel nao encontrado e Java nao esta instalado.
  pause
  exit /b 1
)
java -jar "%JAR%" "%~dp0"
