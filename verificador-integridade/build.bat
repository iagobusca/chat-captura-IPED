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
>>"%MANIFEST%" echo Implementation-Version: 1.3.5
>>"%MANIFEST%" echo.
"%JAVA_HOME%\bin\javac.exe" -encoding UTF-8 -source 1.8 -target 1.8 -cp "%JSON%" -d "%OUT%" "%SRC%"
if errorlevel 1 exit /b 1
"%JAVA_HOME%\bin\jar.exe" cfm "%HERE%integridade-verifier.jar" "%MANIFEST%" -C "%OUT%" .
if errorlevel 1 exit /b 1
pushd "%JSON_OUT%"
"%JAVA_HOME%\bin\jar.exe" xf "%JSON%"
if errorlevel 1 (popd & exit /b 1)
popd
"%JAVA_HOME%\bin\jar.exe" uf "%HERE%integridade-verifier.jar" -C "%JSON_OUT%" org/json
exit /b %ERRORLEVEL%
