@echo off
setlocal
set "INSTALLER=%~dp0..\..\instalar_captura_chat_zap\instalar_captura_chat_zap.bat"
if not exist "%INSTALLER%" (
    echo ERRO: instalador atualizado nao encontrado:
    echo "%INSTALLER%"
    pause
    exit /b 1
)
call "%INSTALLER%" %*
exit /b %ERRORLEVEL%
