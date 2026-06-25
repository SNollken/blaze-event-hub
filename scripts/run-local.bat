@echo off
REM run-local.bat — NollenBlaze local runner
REM Le .env, seta vars e chama mvnw.cmd.
REM NUNCA imprime valores sensiveis.

for /f "tokens=1,* delims== eol=#" %%A in (%~dp0..\.env) do if not "%%A"=="" if not "%%B"=="" set "%%A=%%B"

echo.
echo === run-local: NollenBlaze ===
echo Variaveis carregadas do .env
echo   BLAZE_CLIENT_ID     : [configurado]
echo   BLAZE_CLIENT_SECRET : [SEGURADO]
echo   BLAZE_REDIRECT_URI  : [configurado]
echo   BLAZE_SCOPES        : [configurado]
echo.
echo Iniciando Spring Boot...
echo.

cd /d %~dp0..
call mvnw.cmd spring-boot:run
