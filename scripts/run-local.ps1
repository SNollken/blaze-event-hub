<#
.SYNOPSIS
  Sobe o backend Spring Boot local carregando o .env sem expor valores.
.DESCRIPTION
  Le o .env, exporta as variaveis para o processo do Maven/Spring Boot,
  e roda mvnw.cmd spring-boot:run.
  NUNCA imprime BLAZE_CLIENT_SECRET, tokens ou valores sensiveis.
#>

$repoDir = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $repoDir ".env"

if (-not (Test-Path $envFile)) {
    Write-Host "ERRO: .env nao encontrado em $envFile" -ForegroundColor Red
    Write-Host "Crie o .env a partir do .env.example antes de rodar." -ForegroundColor Yellow
    exit 1
}

# Carrega .env e seta variaveis no processo
$envLoaded = @{}
Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -ne "" -and $line -notmatch '^\s*#') {
        $idx = $line.IndexOf("=")
        if ($idx -gt 0) {
            $key = $line.Substring(0, $idx).Trim()
            $val = $line.Substring($idx + 1).Trim()
            Set-Item -Path "env:$key" -Value $val
            $envLoaded[$key] = $true
        }
    }
}

# Valida se as variaveis essenciais foram carregadas
$requiredKeys = @("BLAZE_CLIENT_ID", "BLAZE_CLIENT_SECRET")
$missing = $requiredKeys | Where-Object { -not $envLoaded.ContainsKey($_) }
if ($missing.Count -gt 0) {
    Write-Host "ERRO: .env carregado mas as chaves obrigatorias estao ausentes: $($missing -join ', ')" -ForegroundColor Red
    Write-Host "Verifique se o .env existe em $envFile e tem o formato CHAVE=VALOR." -ForegroundColor Yellow
    exit 1
}

Write-Host ""
Write-Host "=== run-local: NollenBlaze ===" -ForegroundColor Cyan
Write-Host "Variaveis carregadas do .env:"

$loadedKeys = @("BLAZE_CLIENT_ID","BLAZE_CLIENT_SECRET","BLAZE_REDIRECT_URI","BLAZE_AUTH_BASE_URL","BLAZE_API_BASE_URL","BLAZE_SOCKET_URL","BLAZE_SOCKET_PATH","BLAZE_SCOPES","BLAZE_MONITORED_CHANNEL_ID")
foreach ($k in $loadedKeys) {
    if (Test-Path "env:$k") {
        if ($k -eq "BLAZE_CLIENT_SECRET") {
            Write-Host "  $k : [SEGURADO]" -ForegroundColor Green
        } else {
            Write-Host "  $k : [configurado]" -ForegroundColor Green
        }
    }
}

Write-Host ""
Write-Host "Iniciando Spring Boot..." -ForegroundColor Cyan
Write-Host ""

$mvnw = Join-Path $repoDir "mvnw.cmd"
if (-not (Test-Path $mvnw)) {
    Write-Host "ERRO: mvnw.cmd nao encontrado em $mvnw" -ForegroundColor Red
    exit 1
}

Write-Host "Comando: mvnw.cmd spring-boot:run" -ForegroundColor DarkGray
& $mvnw "spring-boot:run"
