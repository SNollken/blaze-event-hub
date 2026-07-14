#!/usr/bin/env bash
# Inicia o Blaze Event Hub local sem colocar secrets na linha de comando.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
ENV_FILE="$REPO_DIR/.env"

if [[ ! -f "$ENV_FILE" ]]; then
    echo "ERRO: .env local nao encontrado."
    exit 1
fi

required=(
    BLAZE_CLIENT_ID
    BLAZE_CLIENT_SECRET
    BLAZE_REDIRECT_URI
    BLAZE_SCOPES
    EVENTHUB_CREDENTIAL_ENCRYPTION_KEY
)
missing=()

for key in "${required[@]}"; do
    line="$(grep -m1 -E "^[[:space:]]*${key}=" "$ENV_FILE" || true)"
    value="${line#*=}"
    value="${value%$'\r'}"
    value="${value#"${value%%[![:space:]]*}"}"
    value="${value%"${value##*[![:space:]]}"}"
    value="${value#\"}"
    value="${value%\"}"
    value="${value#\'}"
    value="${value%\'}"
    normalized="${value,,}"
    case "$normalized" in
        ""|"<"*|*placeholder*|*change-me*) missing+=("$key") ;;
    esac
done

if (( ${#missing[@]} > 0 )); then
    echo "ERRO: chaves ausentes ou placeholder: ${missing[*]}"
    exit 1
fi

java_line="$(java -version 2>&1 | head -n 1 || true)"
if [[ "$java_line" != *'version "21.'* && "$java_line" != *'version "21"'* ]]; then
    echo "ERRO: o Blaze Event Hub exige Java 21."
    exit 1
fi

if [[ ! -x "$REPO_DIR/mvnw" ]]; then
    echo "ERRO: Maven Wrapper sem permissao de execucao. Rode: chmod +x mvnw"
    exit 1
fi

export SPRING_PROFILES_ACTIVE=local
echo "=== run-local: Blaze Event Hub ==="
echo "Perfil: local | Backend: http://localhost:9090"

cd "$REPO_DIR"
exec ./mvnw spring-boot:run
