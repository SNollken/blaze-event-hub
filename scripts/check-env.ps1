<#
.SYNOPSIS
  Verifica se o .env local tem as chaves obrigatórias da Blaze sem expor valores.
.DESCRIPTION
  Lê .env, checa chaves obrigatórias, indica configurado/ausente/placeholder.
  Exit code = 0 se tudo ok, >0 se faltar obrigatório.
  NUNCA imprime BLAZE_CLIENT_SECRET, tokens ou valores sensíveis.
#>

$repoDir = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $repoDir ".env"
$exampleFile = Join-Path $repoDir ".env.example"

if (-not (Test-Path $envFile)) {
    Write-Host "ERRO: .env nao encontrado em $envFile" -ForegroundColor Red
    exit 1
}

$lines = Get-Content $envFile
$vars = @{}
foreach ($line in $lines) {
    $line = $line.Trim()
    if ($line -eq "" -or $line.StartsWith("#")) { continue }
    $idx = $line.IndexOf("=")
    if ($idx -le 0) { continue }
    $key = $line.Substring(0, $idx).Trim()
    $val = $line.Substring($idx + 1).Trim()
    $vars[$key] = $val
}

$mandatory = @(
    "BLAZE_CLIENT_ID",
    "BLAZE_CLIENT_SECRET",
    "BLAZE_REDIRECT_URI",
    "BLAZE_SCOPES"
)

$optional = @(
    "BLAZE_MONITORED_CHANNEL_ID"
)

$allOk = $true

Write-Host ""
Write-Host "=== check-env: NollenBlaze ===" -ForegroundColor Cyan

foreach ($key in $mandatory) {
    $val = $vars[$key]
    if ([string]::IsNullOrEmpty($val)) {
        Write-Host "  $key : AUSENTE" -ForegroundColor Red
        $allOk = $false
    } elseif ($val -like "<*>" -or $val -like "*placeholder*") {
        Write-Host "  $key : PLACEHOLDER" -ForegroundColor Yellow
        $allOk = $false
    } else {
        Write-Host "  $key : configurado" -ForegroundColor Green
    }
}

foreach ($key in $optional) {
    $val = $vars[$key]
    if ([string]::IsNullOrEmpty($val)) {
        Write-Host "  $key : ausente (opcional)" -ForegroundColor DarkYellow
    } else {
        Write-Host "  $key : configurado" -ForegroundColor Green
    }
}

Write-Host ""
if ($allOk) {
    Write-Host "Resultado: todas as chaves obrigatorias configuradas." -ForegroundColor Green
    exit 0
} else {
    Write-Host "Resultado: ha chaves ausentes ou placeholder no .env" -ForegroundColor Yellow
    exit 1
}
