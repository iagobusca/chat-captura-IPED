@echo off
setlocal
set "HERE=%~dp0"
set "SRC=%HERE%src\IntegrityVerifier.java"
set "OUT=%HERE%build\classes"
if not defined JAVA_HOME (
  echo Defina JAVA_HOME para um JDK 8.
  exit /b 1
)
set "JSON=%HERE%dependencies\json-20260719.jar"
set "MANIFEST=%HERE%build\MANIFEST.MF"
set "JSON_OUT=%HERE%build\json-classes"
if not exist "%OUT%" mkdir "%OUT%"
if not exist "%JSON_OUT%" mkdir "%JSON_OUT%"
>"%MANIFEST%" echo Manifest-Version: 1.0
>>"%MANIFEST%" echo Main-Class: IntegrityVerifier
>>"%MANIFEST%" echo Implementation-Version: 1.5.6
>>"%MANIFEST%" echo.
if exist "%OUT%" rmdir /s /q "%OUT%"
mkdir "%OUT%"
"%JAVA_HOME%\bin\javac.exe" -encoding UTF-8 -source 1.8 -target 1.8 -cp "%JSON%" -d "%OUT%" "%SRC%"
if errorlevel 1 exit /b 1
if exist "%HERE%imagem doc\verificador-icon.png" (
  if not exist "%OUT%\imagem doc" mkdir "%OUT%\imagem doc"
  copy /Y "%HERE%imagem doc\verificador-icon.png" "%OUT%\imagem doc\verificador-icon.png" >nul
  copy /Y "%HERE%imagem doc\verificador-icon.png" "%OUT%\verificador-icon.png" >nul
)
"%JAVA_HOME%\bin\jar.exe" cfm "%HERE%integridade-verifier.jar" "%MANIFEST%" -C "%OUT%" .
if errorlevel 1 exit /b 1
pushd "%JSON_OUT%"
"%JAVA_HOME%\bin\jar.exe" xf "%JSON%"
if errorlevel 1 (popd & exit /b 1)
popd
"%JAVA_HOME%\bin\jar.exe" uf "%HERE%integridade-verifier.jar" -C "%JSON_OUT%" org/json
exit /b %ERRORLEVEL%
