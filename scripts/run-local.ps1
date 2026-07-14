<#
.SYNOPSIS
  Inicia o Blaze Event Hub local com Java 21 e Maven Wrapper.
.DESCRIPTION
  O Spring carrega o .env diretamente. Nenhum secret e colocado na linha de comando.
#>

$repoDir = Split-Path -Parent $PSScriptRoot
$checkEnv = Join-Path $PSScriptRoot "check-env.ps1"
$mvnw = Join-Path $repoDir "mvnw.cmd"

$envValid = & $checkEnv -PassThru
if (-not $envValid) { exit 1 }

try {
    $javaVersion = (& java -version 2>&1 | Select-Object -First 1).ToString()
} catch {
    Write-Host "ERRO: Java nao encontrado. Ative um JDK 21 nesta sessao." -ForegroundColor Red
    exit 1
}

if ($LASTEXITCODE -ne 0 -or $javaVersion -notmatch 'version "21(?:\.|\")') {
    Write-Host "ERRO: o Blaze Event Hub exige Java 21. Versao detectada: $javaVersion" -ForegroundColor Red
    exit 1
}

if (-not (Test-Path -LiteralPath $mvnw -PathType Leaf)) {
    Write-Host "ERRO: Maven Wrapper nao encontrado." -ForegroundColor Red
    exit 1
}

$env:SPRING_PROFILES_ACTIVE = "local"
Write-Host ""
Write-Host "=== run-local: Blaze Event Hub ===" -ForegroundColor Cyan
Write-Host "Perfil: local | Backend: http://localhost:9090" -ForegroundColor DarkGray
Write-Host "Comando: mvnw.cmd spring-boot:run" -ForegroundColor DarkGray
Write-Host ""

Push-Location $repoDir
try {
    & $mvnw "spring-boot:run"
    $exitCode = $LASTEXITCODE
} finally {
    Pop-Location
}

exit $exitCode
