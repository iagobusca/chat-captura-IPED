@echo off
setlocal
set "HERE=%~dp0"
set "SRC=%HERE%src\IntegrityVerifier.java"
set "OUT=%HERE%build\classes"
if not defined JAVA_HOME (
  echo Defina JAVA_HOME para um JDK 11 ou superior.
  exit /b 1
)
if not exist "%OUT%" mkdir "%OUT%"
"%JAVA_HOME%\bin\javac.exe" -encoding UTF-8 --release 11 -d "%OUT%" "%SRC%"
if errorlevel 1 exit /b 1
"%JAVA_HOME%\bin\jar.exe" --create --file "%HERE%integridade-verifier.jar" --main-class IntegrityVerifier -C "%OUT%" IntegrityVerifier.class -C "%OUT%" "IntegrityVerifier$Capture.class" -C "%OUT%" "IntegrityVerifier$Item.class"
exit /b %ERRORLEVEL%
