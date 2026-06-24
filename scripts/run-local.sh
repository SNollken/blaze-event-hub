#!/usr/bin/env bash
# run-local.sh — NollenBlaze local runner
# Carrega .env e passa como -D para spring-boot:run.
# NUNCA imprime valores sensiveis.
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
ENV_FILE="$REPO_DIR/.env"

if [ ! -f "$ENV_FILE" ]; then
    echo "ERRO: .env nao encontrado em $ENV_FILE"
    exit 1
fi

# Monta lista de -D props para spring-boot-maven-plugin
JVM_ARGS=""
while IFS='=' read -r key val; do
    key="$(echo "$key" | tr -d ' \r')"
    val="$(echo "$val" | sed 's/[[:space:]]*$//')"
    case "$key" in
        BLAZE_CLIENT_ID|BLAZE_CLIENT_SECRET|BLAZE_REDIRECT_URI|BLAZE_AUTH_BASE_URL|BLAZE_API_BASE_URL|BLAZE_SOCKET_URL|BLAZE_SOCKET_PATH|BLAZE_MONITORED_CHANNEL_ID)
            if [ -n "$val" ]; then
                JVM_ARGS="$JVM_ARGS -D$key=$val"
            fi
            ;;
        BLAZE_SCOPES)
            if [ -n "$val" ]; then
                JVM_ARGS="$JVM_ARGS -D$key=$val"
            fi
            ;;
    esac
done < <(grep -E '^BLAZE_' "$ENV_FILE" | grep -v '^#' | sed 's/\r$//')

echo ""
echo "=== run-local: NollenBlaze ==="
echo "Props injetadas via spring-boot.run.jvmArguments"
echo "  BLAZE_CLIENT_ID     : [configurado]"
echo "  BLAZE_CLIENT_SECRET : [SEGURADO]"
echo "  BLAZE_REDIRECT_URI  : [configurado]"
echo "  BLAZE_SCOPES        : [configurado]"
echo ""
echo "Iniciando Spring Boot..."
echo ""

cd "$REPO_DIR"
eval exec ./mvnw spring-boot:run "-Dspring-boot.run.jvmArguments=$JVM_ARGS"
