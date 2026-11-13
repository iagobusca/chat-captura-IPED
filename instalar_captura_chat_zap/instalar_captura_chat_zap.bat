@echo off
setlocal enabledelayedexpansion

set "SCRIPT_DIR=%~dp0"
set "SCRIPT_ROOT=%SCRIPT_DIR:~0,-1%"

REM O JAR da pasta do instalador e preferido para que o ZIP funcione sozinho.
set "PLUGIN_JAR=%SCRIPT_DIR%iped-viewers-impl-4.3.1.jar"
if not exist "%PLUGIN_JAR%" set "PLUGIN_JAR=%SCRIPT_DIR%..\..\chat-captura IPED\instalar_captura_chat_zap\iped-viewers-impl-4.3.1.jar"
set "JSON_SOURCE=%SCRIPT_DIR%dependencies\json-20260719.jar"
set "OPENCV_SOURCE=%SCRIPT_DIR%dependencies\opencv-4120.jar"
set "OPENCV_DLL_SOURCE=%SCRIPT_DIR%opencv_java4120.dll"

REM Sobe ate a pasta raiz do IPED, procurando o iped.exe.
set "IPED_ROOT="
set "PROBE=%SCRIPT_DIR%"
for /L %%L in (1,1,6) do (
    if exist "!PROBE!iped.exe" set "IPED_ROOT=!PROBE!"
    set "PROBE=!PROBE!..\"
)
if "!IPED_ROOT!"=="" if exist "%SCRIPT_DIR%..\..\iped-4.3.1\lib\iped-viewers-impl-4.3.1.jar" set "IPED_ROOT=%SCRIPT_DIR%..\..\iped-4.3.1"
if "!IPED_ROOT!"=="" if exist "%SCRIPT_DIR%..\..\..\iped-4.3.1\lib\iped-viewers-impl-4.3.1.jar" set "IPED_ROOT=%SCRIPT_DIR%..\..\..\iped-4.3.1"

if "%IPED_ROOT%"=="" (
    echo.
    echo ERRO: pasta raiz do IPED nao encontrada.
    echo Coloque o instalador dentro da pasta do IPED ou ao lado de "iped-4.3.1".
    echo.
    pause
    exit /b 1
)
for %%I in ("%IPED_ROOT%") do set "IPED_ROOT=%%~fI"
if not "%IPED_ROOT:~-2%"==":\" if "%IPED_ROOT:~-1%"=="\" set "IPED_ROOT=%IPED_ROOT:~0,-1%"

set "TARGET_JAR=%IPED_ROOT%\lib\iped-viewers-impl-4.3.1.jar"
set "JSON_TARGET=%IPED_ROOT%\lib\json-20260719.jar"
set "JSON_OLD=%IPED_ROOT%\lib\json-20250517.jar"
set "OPENCV_TARGET=%IPED_ROOT%\lib\opencv-4120.jar"
set "OPENCV_DLL_TARGET=%IPED_ROOT%\opencv_java4120.dll"
set "OPENCV_PLUGINS_DLL_TARGET=%IPED_ROOT%\plugins\opencv_java4120.dll"

echo.
echo Instalador do plugin de captura de chat WhatsApp
echo Versao do plugin: 2.6.0-beta
echo Verificador de integridade: 1.3.5
echo ------------------------------------------------
echo.

if not exist "%PLUGIN_JAR%" (
    echo ERRO: JAR do plugin nao encontrado em nenhuma destas pastas:
    echo   "%SCRIPT_DIR%iped-viewers-impl-4.3.1.jar"
    echo   "%SCRIPT_DIR%..\..\chat-captura IPED\instalar_captura_chat_zap\iped-viewers-impl-4.3.1.jar"
    echo.
    pause
    exit /b 1
)

if not exist "%TARGET_JAR%" (
    echo ERRO: JAR original do IPED nao encontrado:
    echo "%TARGET_JAR%"
    echo.
    pause
    exit /b 1
)

if not exist "%JSON_SOURCE%" (
    echo ERRO: dependencia JSON corrigida nao encontrada:
    echo "%JSON_SOURCE%"
    pause
    exit /b 1
)

powershell -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT_DIR%validate-plugin-jar.ps1" -Jar "%PLUGIN_JAR%"
if errorlevel 1 (
    echo ERRO: pacote incompleto ou invalido. Nenhum arquivo foi alterado.
    pause
    exit /b 1
)

for /f %%i in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd-HHmmss"') do set "STAMP=%%i"
if "%STAMP%"=="" set "STAMP=backup"
set "BACKUP_JAR=%TARGET_JAR%.bak-%STAMP%"
set "BACKUP_JSON=%JSON_OLD%.bak-%STAMP%"
set "BACKUP_JSON_TARGET=%JSON_TARGET%.bak-%STAMP%"
set "BACKUP_OPENCV_JAR=%OPENCV_TARGET%.bak-%STAMP%"
set "BACKUP_OPENCV_DLL=%OPENCV_DLL_TARGET%.bak-%STAMP%"
set "BACKUP_OPENCV_PLUGINS_DLL=%OPENCV_PLUGINS_DLL_TARGET%.bak-%STAMP%"
set "OPENCV_JAR_WRITTEN=0"
set "OPENCV_DLL_WRITTEN=0"
set "OPENCV_PLUGINS_DLL_WRITTEN=0"

echo Criando backup:
echo "%BACKUP_JAR%"
copy /Y "%TARGET_JAR%" "%BACKUP_JAR%" >nul
if errorlevel 1 (
    echo.
    echo ERRO: nao foi possivel criar backup. Feche o IPED e tente novamente.
    pause
    exit /b 1
)

echo.
echo Instalando plugin:
echo "%PLUGIN_JAR%"
echo para:
echo "%TARGET_JAR%"
copy /Y "%PLUGIN_JAR%" "%TARGET_JAR%" >nul
if errorlevel 1 (
    echo.
    echo ERRO: nao foi possivel substituir o JAR. Feche o IPED e tente novamente.
    echo O backup foi preservado em:
    echo "%BACKUP_JAR%"
    pause
    exit /b 1
)

REM Substitui as versoes JSON anteriores; conserva backups para reversao.
if exist "%JSON_TARGET%" (
    move /Y "%JSON_TARGET%" "%BACKUP_JSON_TARGET%" >nul
    if errorlevel 1 (
        echo ERRO: nao foi possivel preservar a dependencia JSON atual.
        copy /Y "%BACKUP_JAR%" "%TARGET_JAR%" >nul
        pause
        exit /b 1
    )
)
if exist "%JSON_OLD%" (
    move /Y "%JSON_OLD%" "%BACKUP_JSON%" >nul
    if errorlevel 1 (
        echo ERRO: nao foi possivel preservar a dependencia JSON antiga.
        if exist "%BACKUP_JSON_TARGET%" move /Y "%BACKUP_JSON_TARGET%" "%JSON_TARGET%" >nul
        copy /Y "%BACKUP_JAR%" "%TARGET_JAR%" >nul
        pause
        exit /b 1
    )
)
copy /Y "%JSON_SOURCE%" "%JSON_TARGET%" >nul
if errorlevel 1 (
    echo ERRO: nao foi possivel instalar a dependencia JSON corrigida.
    if exist "%BACKUP_JSON%" move /Y "%BACKUP_JSON%" "%JSON_OLD%" >nul
    if exist "%BACKUP_JSON_TARGET%" move /Y "%BACKUP_JSON_TARGET%" "%JSON_TARGET%" >nul
    copy /Y "%BACKUP_JAR%" "%TARGET_JAR%" >nul
    pause
    exit /b 1
)

REM Mantem o nome legado como alias com os mesmos bytes novos para classpaths fixos do IPED.
copy /Y "%JSON_SOURCE%" "%JSON_OLD%" >nul
if errorlevel 1 (
    echo ERRO: nao foi possivel criar o alias de compatibilidade da dependencia JSON.
    del /Q "%JSON_TARGET%" >nul 2>&1
    if exist "%BACKUP_JSON%" move /Y "%BACKUP_JSON%" "%JSON_OLD%" >nul
    if exist "%BACKUP_JSON_TARGET%" move /Y "%BACKUP_JSON_TARGET%" "%JSON_TARGET%" >nul
    copy /Y "%BACKUP_JAR%" "%TARGET_JAR%" >nul
    pause
    exit /b 1
)

REM Instala o OpenCV (necessario para censura por similaridade em capturas html-message-v1).
if exist "%OPENCV_SOURCE%" (
    if not exist "%OPENCV_TARGET%" (
        copy /Y "%OPENCV_SOURCE%" "%OPENCV_TARGET%" >nul
        if errorlevel 1 (
            echo ERRO: nao foi possivel instalar opencv-4120.jar. Restaurando backup.
            call :ROLLBACK
            pause
            exit /b 1
        )
        set "OPENCV_JAR_WRITTEN=1"
    ) else (
        REM Atualiza apenas se o hash diferir.
        for /f %%i in ('powershell -NoProfile -Command "(Get-FileHash -LiteralPath '%OPENCV_SOURCE%' -Algorithm SHA256).Hash"') do set "OPENCV_SRC_HASH=%%i"
        for /f %%i in ('powershell -NoProfile -Command "(Get-FileHash -LiteralPath '%OPENCV_TARGET%' -Algorithm SHA256).Hash"') do set "OPENCV_DST_HASH=%%i"
        if not "!OPENCV_SRC_HASH!"=="!OPENCV_DST_HASH!" (
            copy /Y "%OPENCV_TARGET%" "%BACKUP_OPENCV_JAR%" >nul
            if errorlevel 1 (
                echo ERRO: nao foi possivel criar backup do opencv-4120.jar. Restaurando backup.
                call :ROLLBACK
                pause
                exit /b 1
            )
            copy /Y "%OPENCV_SOURCE%" "%OPENCV_TARGET%" >nul
            if errorlevel 1 (
                echo ERRO: nao foi possivel atualizar opencv-4120.jar. Restaurando backup.
                set "OPENCV_JAR_WRITTEN=1"
                call :ROLLBACK
                pause
                exit /b 1
            )
            set "OPENCV_JAR_WRITTEN=1"
        )
    )
)
if exist "%OPENCV_DLL_SOURCE%" (
    if exist "%OPENCV_DLL_TARGET%" (
        copy /Y "%OPENCV_DLL_TARGET%" "%BACKUP_OPENCV_DLL%" >nul
        if errorlevel 1 (
            echo ERRO: nao foi possivel criar backup da opencv_java4120.dll. Restaurando backup.
            call :ROLLBACK
            pause
            exit /b 1
        )
    )
    copy /Y "%OPENCV_DLL_SOURCE%" "%OPENCV_DLL_TARGET%" >nul
    if errorlevel 1 (
        echo ERRO: nao foi possivel instalar opencv_java4120.dll na raiz do IPED. Restaurando backup.
        set "OPENCV_DLL_WRITTEN=1"
        call :ROLLBACK
        pause
        exit /b 1
    )
    set "OPENCV_DLL_WRITTEN=1"
    REM Instala tambem na pasta plugins/, onde o plugin procura por padrao.
    if not exist "%IPED_ROOT%\plugins" (
        mkdir "%IPED_ROOT%\plugins"
        if errorlevel 1 (
            echo ERRO: nao foi possivel criar a pasta plugins do IPED. Restaurando backup.
            call :ROLLBACK
            pause
            exit /b 1
        )
    )
    if exist "%OPENCV_PLUGINS_DLL_TARGET%" (
        copy /Y "%OPENCV_PLUGINS_DLL_TARGET%" "%BACKUP_OPENCV_PLUGINS_DLL%" >nul
        if errorlevel 1 (
            echo ERRO: nao foi possivel criar backup de plugins\opencv_java4120.dll. Restaurando backup.
            call :ROLLBACK
            pause
            exit /b 1
        )
    )
    copy /Y "%OPENCV_DLL_SOURCE%" "%OPENCV_PLUGINS_DLL_TARGET%" >nul
    if errorlevel 1 (
        echo ERRO: nao foi possivel instalar opencv_java4120.dll em plugins\. Restaurando backup.
        set "OPENCV_PLUGINS_DLL_WRITTEN=1"
        call :ROLLBACK
        pause
        exit /b 1
    )
    set "OPENCV_PLUGINS_DLL_WRITTEN=1"
)

REM Injeta os bundles de idioma do plugin no JAR instalado.
powershell -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT_DIR%install-localization.ps1" -Jar "%TARGET_JAR%" -ResourceDir "%SCRIPT_ROOT%" -LocaleDir "%IPED_ROOT%\localization"
if errorlevel 1 (
    echo ERRO: falha ao instalar os arquivos de idioma. Restaurando backup.
    call :ROLLBACK
    pause
    exit /b 1
)
REM Confere se o JAR instalado tem o mesmo hash do pacote.
set "SRC_HASH="
set "DST_HASH="
for /f %%i in ('powershell -NoProfile -Command "(Get-FileHash -LiteralPath '%PLUGIN_JAR%' -Algorithm SHA256).Hash"') do set "SRC_HASH=%%i"
for /f %%i in ('powershell -NoProfile -Command "(Get-FileHash -LiteralPath '%TARGET_JAR%' -Algorithm SHA256).Hash"') do set "DST_HASH=%%i"

echo.
echo Instalacao concluida com sucesso.
echo Backup criado em:
echo "%BACKUP_JAR%"
echo.
if "%SRC_HASH%"=="%DST_HASH%" (
    echo SHA-256 conferido: %DST_HASH%
) else (
    echo AVISO: os hashes nao conferem.
    echo   pacote:    %SRC_HASH%
    echo   instalado: %DST_HASH%
)
echo.
echo Feche e abra novamente o IPED para carregar o plugin.
echo.
pause
endlocal
exit /b 0

:ROLLBACK
if "%OPENCV_PLUGINS_DLL_WRITTEN%"=="1" (
    if exist "%BACKUP_OPENCV_PLUGINS_DLL%" (
        move /Y "%BACKUP_OPENCV_PLUGINS_DLL%" "%OPENCV_PLUGINS_DLL_TARGET%" >nul
    ) else if exist "%OPENCV_PLUGINS_DLL_TARGET%" (
        del /Q "%OPENCV_PLUGINS_DLL_TARGET%" >nul 2>&1
    )
)
if "%OPENCV_DLL_WRITTEN%"=="1" (
    if exist "%BACKUP_OPENCV_DLL%" (
        move /Y "%BACKUP_OPENCV_DLL%" "%OPENCV_DLL_TARGET%" >nul
    ) else if exist "%OPENCV_DLL_TARGET%" (
        del /Q "%OPENCV_DLL_TARGET%" >nul 2>&1
    )
)
if "%OPENCV_JAR_WRITTEN%"=="1" (
    if exist "%BACKUP_OPENCV_JAR%" (
        move /Y "%BACKUP_OPENCV_JAR%" "%OPENCV_TARGET%" >nul
    ) else if exist "%OPENCV_TARGET%" (
        del /Q "%OPENCV_TARGET%" >nul 2>&1
    )
)
if exist "%JSON_TARGET%" del /Q "%JSON_TARGET%" >nul 2>&1
if exist "%JSON_OLD%" if not exist "%BACKUP_JSON%" del /Q "%JSON_OLD%" >nul 2>&1
if exist "%BACKUP_JSON%" move /Y "%BACKUP_JSON%" "%JSON_OLD%" >nul
if exist "%BACKUP_JSON_TARGET%" move /Y "%BACKUP_JSON_TARGET%" "%JSON_TARGET%" >nul
if exist "%BACKUP_JAR%" copy /Y "%BACKUP_JAR%" "%TARGET_JAR%" >nul
goto :eof
