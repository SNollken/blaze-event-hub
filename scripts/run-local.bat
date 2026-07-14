@echo off
setlocal EnableExtensions EnableDelayedExpansion
REM Inicia o Blaze Event Hub local sem expor secrets na linha de comando.

set "REPO_DIR=%~dp0.."
set "ENV_FILE=%REPO_DIR%\.env"

if not exist "%ENV_FILE%" (
    echo ERRO: .env local nao encontrado.
    exit /b 1
)

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0check-env.ps1"
if errorlevel 1 exit /b 1

set "JAVA_VERSION="
for /f "tokens=3" %%V in ('java -version 2^>^&1 ^| findstr /i "version"') do if not defined JAVA_VERSION set "JAVA_VERSION=%%~V"
if not defined JAVA_VERSION (
    echo ERRO: Java nao encontrado. Ative um JDK 21 nesta sessao.
    exit /b 1
)

for /f "tokens=1 delims=." %%M in ("!JAVA_VERSION!") do set "JAVA_MAJOR=%%M"
if not "!JAVA_MAJOR!"=="21" (
    echo ERRO: o Blaze Event Hub exige Java 21. Versao detectada: !JAVA_VERSION!
    exit /b 1
)

if not exist "%REPO_DIR%\mvnw.cmd" (
    echo ERRO: Maven Wrapper nao encontrado.
    exit /b 1
)

set "SPRING_PROFILES_ACTIVE=local"
echo.
echo === run-local: Blaze Event Hub ===
echo Perfil: local ^| Backend: http://localhost:9090
echo.

pushd "%REPO_DIR%"
call mvnw.cmd spring-boot:run
set "EXIT_CODE=!ERRORLEVEL!"
popd
exit /b !EXIT_CODE!
