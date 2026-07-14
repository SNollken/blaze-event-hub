<#
.SYNOPSIS
  Valida a configuracao local do Blaze Event Hub sem imprimir valores.
.DESCRIPTION
  Confere apenas presenca e placeholders. Nunca mostra secrets, tokens ou senhas.
#>
param(
    [switch]$PassThru
)

$repoDir = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $repoDir ".env"

if (-not (Test-Path -LiteralPath $envFile -PathType Leaf)) {
    Write-Host "ERRO: .env local nao encontrado." -ForegroundColor Red
    if ($PassThru) { return $false }
    exit 1
}

$values = @{}
foreach ($rawLine in Get-Content -LiteralPath $envFile) {
    $line = $rawLine.Trim()
    if ($line.Length -eq 0 -or $line.StartsWith("#")) { continue }

    $separator = $line.IndexOf("=")
    if ($separator -le 0) { continue }

    $key = $line.Substring(0, $separator).Trim()
    $value = $line.Substring($separator + 1).Trim().Trim('"').Trim("'")
    $values[$key] = $value
}

$required = @(
    "BLAZE_CLIENT_ID",
    "BLAZE_CLIENT_SECRET",
    "BLAZE_REDIRECT_URI",
    "BLAZE_SCOPES",
    "EVENTHUB_CREDENTIAL_ENCRYPTION_KEY"
)

$optional = @(
    "EVENTHUB_API_KEY",
    "EVENTHUB_DB_URL",
    "EVENTHUB_DB_USER",
    "EVENTHUB_DB_PASSWORD"
)

function Test-ConfiguredValue([string]$Value) {
    if ([string]::IsNullOrWhiteSpace($Value)) { return $false }
    $normalized = $Value.Trim().ToLowerInvariant()
    return -not (
        $normalized.StartsWith("<") -or
        $normalized.Contains("placeholder") -or
        $normalized.Contains("change-me")
    )
}

$valid = $true
Write-Host ""
Write-Host "=== check-env: Blaze Event Hub ===" -ForegroundColor Cyan

foreach ($key in $required) {
    if (Test-ConfiguredValue $values[$key]) {
        Write-Host "  $key : configurado" -ForegroundColor Green
    } else {
        Write-Host "  $key : AUSENTE OU PLACEHOLDER" -ForegroundColor Red
        $valid = $false
    }
}

foreach ($key in $optional) {
    if (Test-ConfiguredValue $values[$key]) {
        Write-Host "  $key : configurado (opcional)" -ForegroundColor Green
    } else {
        Write-Host "  $key : ausente (opcional)" -ForegroundColor DarkGray
    }
}

Write-Host ""
if ($valid) {
    Write-Host "Resultado: configuracao local pronta." -ForegroundColor Green
} else {
    Write-Host "Resultado: corrija as chaves obrigatorias antes de iniciar." -ForegroundColor Yellow
}

if ($PassThru) { return $valid }
if ($valid) { exit 0 }
exit 1
