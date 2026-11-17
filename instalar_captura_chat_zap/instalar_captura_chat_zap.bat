@echo off
setlocal DisableDelayedExpansion

set "SCRIPT_DIR=%~dp0"
set "SCRIPT_ROOT=%SCRIPT_DIR:~0,-1%"

REM O JAR da pasta do instalador e preferido para que o ZIP funcione sozinho.
set "PLUGIN_JAR=%SCRIPT_DIR%iped-viewers-impl-4.3.1.jar"
if not exist "%PLUGIN_JAR%" set "PLUGIN_JAR=%SCRIPT_DIR%..\..\chat-captura IPED\instalar_captura_chat_zap\iped-viewers-impl-4.3.1.jar"
set "EXPECTED_PLUGIN_SHA256=D61591049C3B64EE4DB050A57D90E0018B301F8163FD43B01408F0FEE675666A"
set "JSON_SOURCE=%SCRIPT_DIR%dependencies\json-20260719.jar"
set "OPENCV_DLL_SOURCE=%SCRIPT_DIR%opencv_java4120.dll"
set "VERIFIER_JAR_SOURCE=%SCRIPT_DIR%integridade-verifier.jar"
if not exist "%VERIFIER_JAR_SOURCE%" set "VERIFIER_JAR_SOURCE=%SCRIPT_DIR%..\verificador-integridade\integridade-verifier.jar"
set "EXPECTED_VERIFIER_JAR_SHA256=8180566A0BEF7D70815FBCFFA77DE37B833B9BD4B2C87B185BB1B2A5792A41EF"
set "VERIFIER_EXE_SOURCE=%SCRIPT_DIR%integridade-verifier.exe"
if not exist "%VERIFIER_EXE_SOURCE%" set "VERIFIER_EXE_SOURCE=%SCRIPT_DIR%..\verificador-integridade\integridade-verifier.exe"
set "EXPECTED_VERIFIER_EXE_SHA256=959424CFC4991148F23A4E41F167512C449128392618F3B070E3837E34FFF370"

REM Sobe ate a pasta raiz do IPED, procurando o iped.exe.
set "IPED_ROOT="
set "PROBE=%SCRIPT_DIR%"
for /L %%L in (1,1,6) do call :PROBE_IPED_STEP
if not defined IPED_ROOT if exist "%SCRIPT_DIR%..\iped-4.3.1\lib\iped-viewers-impl-4.3.1.jar" set "IPED_ROOT=%SCRIPT_DIR%..\iped-4.3.1"
if not defined IPED_ROOT if exist "%SCRIPT_DIR%..\..\iped-4.3.1\lib\iped-viewers-impl-4.3.1.jar" set "IPED_ROOT=%SCRIPT_DIR%..\..\iped-4.3.1"
if not defined IPED_ROOT if exist "%SCRIPT_DIR%..\..\..\iped-4.3.1\lib\iped-viewers-impl-4.3.1.jar" set "IPED_ROOT=%SCRIPT_DIR%..\..\..\iped-4.3.1"

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
set "VERIFIER_TARGET_DIR=%IPED_ROOT%\verificador-integridade"
set "VERIFIER_JAR_TARGET=%VERIFIER_TARGET_DIR%\integridade-verifier.jar"
set "VERIFIER_EXE_TARGET=%VERIFIER_TARGET_DIR%\integridade-verifier.exe"

echo.
echo Instalador do plugin de captura de chat WhatsApp
echo Versao do plugin: 2.8.6
echo Verificador de integridade: 1.5.4
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

if "%EXPECTED_PLUGIN_SHA256%"=="" (
    echo ERRO: hash esperado do plugin nao configurado no instalador.
    pause
    exit /b 1
)

if not exist "%VERIFIER_JAR_SOURCE%" (
    echo ERRO: verificador JAR nao encontrado:
    echo "%VERIFIER_JAR_SOURCE%"
    pause
    exit /b 1
)
if not exist "%VERIFIER_EXE_SOURCE%" (
    echo ERRO: verificador EXE nao encontrado:
    echo "%VERIFIER_EXE_SOURCE%"
    pause
    exit /b 1
)
if "%EXPECTED_VERIFIER_JAR_SHA256%"=="" (
    echo ERRO: hash esperado do verificador JAR nao configurado no instalador.
    pause
    exit /b 1
)
if "%EXPECTED_VERIFIER_EXE_SHA256%"=="" (
    echo ERRO: hash esperado do verificador EXE nao configurado no instalador.
    pause
    exit /b 1
)

set "SRC_VJAR_HASH="
set "PS_HASH_PATH=%VERIFIER_JAR_SOURCE%"
for /f %%i in ('powershell -NoProfile -Command "(Get-FileHash -LiteralPath $env:PS_HASH_PATH -Algorithm SHA256).Hash"') do set "SRC_VJAR_HASH=%%i"
set "SRC_VEXE_HASH="
set "PS_HASH_PATH=%VERIFIER_EXE_SOURCE%"
for /f %%i in ('powershell -NoProfile -Command "(Get-FileHash -LiteralPath $env:PS_HASH_PATH -Algorithm SHA256).Hash"') do set "SRC_VEXE_HASH=%%i"
set "PS_HASH_PATH="
if "%SRC_VJAR_HASH%"=="" (
    echo ERRO: nao foi possivel calcular o SHA-256 do verificador JAR do pacote.
    pause
    exit /b 1
)
if /I not "%SRC_VJAR_HASH%"=="%EXPECTED_VERIFIER_JAR_SHA256%" (
    echo ERRO: hash do verificador JAR do pacote diverge do esperado. Nenhum arquivo foi alterado.
    echo   esperado: %EXPECTED_VERIFIER_JAR_SHA256%
    echo   pacote:   %SRC_VJAR_HASH%
    pause
    exit /b 1
)
if "%SRC_VEXE_HASH%"=="" (
    echo ERRO: nao foi possivel calcular o SHA-256 do verificador EXE do pacote.
    pause
    exit /b 1
)
if /I not "%SRC_VEXE_HASH%"=="%EXPECTED_VERIFIER_EXE_SHA256%" (
    echo ERRO: hash do verificador EXE do pacote diverge do esperado. Nenhum arquivo foi alterado.
    echo   esperado: %EXPECTED_VERIFIER_EXE_SHA256%
    echo   pacote:   %SRC_VEXE_HASH%
    pause
    exit /b 1
)

powershell -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT_DIR%validate-plugin-jar.ps1" -Jar "%PLUGIN_JAR%" -ExpectedSha256 "%EXPECTED_PLUGIN_SHA256%"
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
set "BACKUP_VERIFIER_JAR=%VERIFIER_JAR_TARGET%.bak-%STAMP%"
set "BACKUP_VERIFIER_EXE=%VERIFIER_EXE_TARGET%.bak-%STAMP%"
set "LOCALE_BASE_TARGET=%IPED_ROOT%\localization\iped-viewer-messages.properties"
set "LOCALE_PT_TARGET=%IPED_ROOT%\localization\iped-viewer-messages_pt_BR.properties"
set "BACKUP_LOCALE_BASE=%LOCALE_BASE_TARGET%.bak-%STAMP%"
set "BACKUP_LOCALE_PT=%LOCALE_PT_TARGET%.bak-%STAMP%"
set "JSON_DUP_MOVED=0"
set "JSON_OLD_MOVED=0"
set "JSON_OLD_WRITTEN=0"
set "OPENCV_DUP_MOVED=0"
set "OPENCV_DLL_WRITTEN=0"
set "OPENCV_PLUGINS_DLL_WRITTEN=0"
set "VERIFIER_DIR_CREATED=0"
set "VERIFIER_JAR_WRITTEN=0"
set "VERIFIER_EXE_WRITTEN=0"

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
    call :ROLLBACK
    pause
    exit /b 1
)

REM Atualiza o JAR JSON referenciado pelo Class-Path do IPED base e remove copias duplicadas em lib\.
if exist "%JSON_TARGET%" (
    move /Y "%JSON_TARGET%" "%BACKUP_JSON_TARGET%" >nul
    if errorlevel 1 (
        echo ERRO: nao foi possivel remover copia duplicada json-20260719.jar. Restaurando backup.
        call :ROLLBACK
        pause
        exit /b 1
    )
    set "JSON_DUP_MOVED=1"
)
if exist "%OPENCV_TARGET%" (
    move /Y "%OPENCV_TARGET%" "%BACKUP_OPENCV_JAR%" >nul
    if errorlevel 1 (
        echo ERRO: nao foi possivel remover copia duplicada opencv-4120.jar. Restaurando backup.
        call :ROLLBACK
        pause
        exit /b 1
    )
    set "OPENCV_DUP_MOVED=1"
)
if exist "%JSON_OLD%" (
    move /Y "%JSON_OLD%" "%BACKUP_JSON%" >nul
    if errorlevel 1 (
        echo ERRO: nao foi possivel preservar a dependencia JSON antiga. Restaurando backup.
        call :ROLLBACK
        pause
        exit /b 1
    )
    set "JSON_OLD_MOVED=1"
)
copy /Y "%JSON_SOURCE%" "%JSON_OLD%" >nul
if errorlevel 1 (
    echo ERRO: nao foi possivel instalar a dependencia JSON corrigida. Restaurando backup.
    call :ROLLBACK
    pause
    exit /b 1
)
set "JSON_OLD_WRITTEN=1"

REM Instala apenas a biblioteca nativa OpenCV (as classes Java ja estao no fat-JAR do plugin).
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

REM Instala o verificador de integridade em verificador-integridade/ dentro do IPED.
if not exist "%VERIFIER_TARGET_DIR%" (
    mkdir "%VERIFIER_TARGET_DIR%"
    if errorlevel 1 (
        echo ERRO: nao foi possivel criar a pasta verificador-integridade do IPED. Restaurando backup.
        call :ROLLBACK
        pause
        exit /b 1
    )
    set "VERIFIER_DIR_CREATED=1"
)
if exist "%VERIFIER_JAR_TARGET%" (
    copy /Y "%VERIFIER_JAR_TARGET%" "%BACKUP_VERIFIER_JAR%" >nul
    if errorlevel 1 (
        echo ERRO: nao foi possivel criar backup de integridade-verifier.jar. Restaurando backup.
        call :ROLLBACK
        pause
        exit /b 1
    )
)
copy /Y "%VERIFIER_JAR_SOURCE%" "%VERIFIER_JAR_TARGET%" >nul
if errorlevel 1 (
    echo ERRO: nao foi possivel instalar integridade-verifier.jar. Restaurando backup.
    set "VERIFIER_JAR_WRITTEN=1"
    call :ROLLBACK
    pause
    exit /b 1
)
set "VERIFIER_JAR_WRITTEN=1"

if exist "%VERIFIER_EXE_TARGET%" (
    copy /Y "%VERIFIER_EXE_TARGET%" "%BACKUP_VERIFIER_EXE%" >nul
    if errorlevel 1 (
        echo ERRO: nao foi possivel criar backup de integridade-verifier.exe. Restaurando backup.
        call :ROLLBACK
        pause
        exit /b 1
    )
)
copy /Y "%VERIFIER_EXE_SOURCE%" "%VERIFIER_EXE_TARGET%" >nul
if errorlevel 1 (
    echo ERRO: nao foi possivel instalar integridade-verifier.exe. Restaurando backup.
    set "VERIFIER_EXE_WRITTEN=1"
    call :ROLLBACK
    pause
    exit /b 1
)
set "VERIFIER_EXE_WRITTEN=1"

set "DST_VJAR_HASH="
set "PS_HASH_PATH=%VERIFIER_JAR_TARGET%"
for /f %%i in ('powershell -NoProfile -Command "(Get-FileHash -LiteralPath $env:PS_HASH_PATH -Algorithm SHA256).Hash"') do set "DST_VJAR_HASH=%%i"
set "DST_VEXE_HASH="
set "PS_HASH_PATH=%VERIFIER_EXE_TARGET%"
for /f %%i in ('powershell -NoProfile -Command "(Get-FileHash -LiteralPath $env:PS_HASH_PATH -Algorithm SHA256).Hash"') do set "DST_VEXE_HASH=%%i"
set "PS_HASH_PATH="
if "%DST_VJAR_HASH%"=="" (
    echo ERRO: nao foi possivel conferir SHA-256 de integridade-verifier.jar instalado. Restaurando backup.
    call :ROLLBACK
    pause
    exit /b 1
)
if /I not "%DST_VJAR_HASH%"=="%EXPECTED_VERIFIER_JAR_SHA256%" (
    echo ERRO: SHA-256 de integridade-verifier.jar instalado diverge do esperado. Restaurando backup.
    call :ROLLBACK
    pause
    exit /b 1
)
if "%DST_VEXE_HASH%"=="" (
    echo ERRO: nao foi possivel conferir SHA-256 de integridade-verifier.exe instalado. Restaurando backup.
    call :ROLLBACK
    pause
    exit /b 1
)
if /I not "%DST_VEXE_HASH%"=="%EXPECTED_VERIFIER_EXE_SHA256%" (
    echo ERRO: SHA-256 de integridade-verifier.exe instalado diverge do esperado. Restaurando backup.
    call :ROLLBACK
    pause
    exit /b 1
)

REM Injeta os bundles de idioma do plugin no JAR instalado.
powershell -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT_DIR%install-localization.ps1" -Jar "%TARGET_JAR%" -ResourceDir "%SCRIPT_ROOT%" -LocaleDir "%IPED_ROOT%\localization" -BackupStamp "%STAMP%"
if errorlevel 1 (
    echo ERRO: falha ao instalar os arquivos de idioma. Restaurando backup.
    call :ROLLBACK
    pause
    exit /b 1
)
REM Confere se o JAR instalado tem o hash esperado do release e coincide com o pacote.
set "SRC_HASH="
set "DST_HASH="
set "PS_HASH_PATH=%PLUGIN_JAR%"
for /f %%i in ('powershell -NoProfile -Command "(Get-FileHash -LiteralPath $env:PS_HASH_PATH -Algorithm SHA256).Hash"') do set "SRC_HASH=%%i"
set "PS_HASH_PATH=%TARGET_JAR%"
for /f %%i in ('powershell -NoProfile -Command "(Get-FileHash -LiteralPath $env:PS_HASH_PATH -Algorithm SHA256).Hash"') do set "DST_HASH=%%i"
set "PS_HASH_PATH="

if "%SRC_HASH%"=="" (
    echo ERRO: nao foi possivel calcular o SHA-256 do JAR do pacote. Restaurando backup.
    call :ROLLBACK
    pause
    exit /b 1
)
if "%DST_HASH%"=="" (
    echo ERRO: nao foi possivel calcular o SHA-256 do JAR instalado. Restaurando backup.
    call :ROLLBACK
    pause
    exit /b 1
)
if /I not "%SRC_HASH%"=="%EXPECTED_PLUGIN_SHA256%" (
    echo ERRO: o hash do JAR do pacote diverge do esperado. Restaurando backup.
    echo   esperado:  %EXPECTED_PLUGIN_SHA256%
    echo   pacote:    %SRC_HASH%
    call :ROLLBACK
    pause
    exit /b 1
)
if /I not "%DST_HASH%"=="%EXPECTED_PLUGIN_SHA256%" (
    echo ERRO: os hashes nao conferem. Restaurando backup.
    echo   esperado:  %EXPECTED_PLUGIN_SHA256%
    echo   pacote:    %SRC_HASH%
    echo   instalado: %DST_HASH%
    call :ROLLBACK
    pause
    exit /b 1
)

echo.
echo Instalacao concluida com sucesso.
echo Backup criado em:
echo "%BACKUP_JAR%"
echo Verificador instalado em:
echo "%VERIFIER_TARGET_DIR%"
echo.
echo SHA-256 conferido: %DST_HASH%
echo Verificador JAR:   %DST_VJAR_HASH%
echo Verificador EXE:   %DST_VEXE_HASH%
echo.
echo Feche e abra novamente o IPED para carregar o plugin.
echo.
pause
endlocal
exit /b 0

:ROLLBACK
if "%VERIFIER_EXE_WRITTEN%"=="1" (
    if exist "%BACKUP_VERIFIER_EXE%" (
        move /Y "%BACKUP_VERIFIER_EXE%" "%VERIFIER_EXE_TARGET%" >nul
    ) else if exist "%VERIFIER_EXE_TARGET%" (
        del /Q "%VERIFIER_EXE_TARGET%" >nul 2>&1
    )
)
if "%VERIFIER_JAR_WRITTEN%"=="1" (
    if exist "%BACKUP_VERIFIER_JAR%" (
        move /Y "%BACKUP_VERIFIER_JAR%" "%VERIFIER_JAR_TARGET%" >nul
    ) else if exist "%VERIFIER_JAR_TARGET%" (
        del /Q "%VERIFIER_JAR_TARGET%" >nul 2>&1
    )
)
if "%VERIFIER_DIR_CREATED%"=="1" (
    rmdir "%VERIFIER_TARGET_DIR%" >nul 2>&1
)
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
if exist "%BACKUP_LOCALE_BASE%" move /Y "%BACKUP_LOCALE_BASE%" "%LOCALE_BASE_TARGET%" >nul
if exist "%BACKUP_LOCALE_PT%" move /Y "%BACKUP_LOCALE_PT%" "%LOCALE_PT_TARGET%" >nul
if "%JSON_OLD_WRITTEN%"=="1" (
    if exist "%JSON_OLD%" del /Q "%JSON_OLD%" >nul 2>&1
)
if "%JSON_OLD_MOVED%"=="1" (
    if exist "%BACKUP_JSON%" move /Y "%BACKUP_JSON%" "%JSON_OLD%" >nul
)
if "%OPENCV_DUP_MOVED%"=="1" (
    if exist "%BACKUP_OPENCV_JAR%" move /Y "%BACKUP_OPENCV_JAR%" "%OPENCV_TARGET%" >nul
)
if "%JSON_DUP_MOVED%"=="1" (
    if exist "%BACKUP_JSON_TARGET%" move /Y "%BACKUP_JSON_TARGET%" "%JSON_TARGET%" >nul
)
if exist "%BACKUP_JAR%" copy /Y "%BACKUP_JAR%" "%TARGET_JAR%" >nul
goto :eof

:PROBE_IPED_STEP
if not defined IPED_ROOT if exist "%PROBE%iped.exe" set "IPED_ROOT=%PROBE%"
set "PROBE=%PROBE%..\"
goto :eof
