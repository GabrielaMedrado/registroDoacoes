#!/usr/bin/env bash
# Confere se a aplicação implantada está no ar e servindo o que deve.
# Uso: scripts/verificar-implantacao.sh [URL]   (padrão http://127.0.0.1:8080)
# ESPERA_SEGUNDOS: quanto esperar o health ficar UP (padrão 90).
set -euo pipefail

url="${1:-http://127.0.0.1:8080}"
espera="${ESPERA_SEGUNDOS:-90}"

falhar() {
	echo "Verificação falhou: $1" >&2
	exit 1
}

inicio=$SECONDS
until curl -fsS "$url/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; do
	if (( SECONDS - inicio >= espera )); then
		echo "A aplicação não ficou saudável em ${espera}s." >&2
		exit 1
	fi
	sleep 2
done

curl -fsS "$url/" | grep -q 'id="root"' || falhar "a página inicial não é o React"
curl -fsS "$url/campanhas/1" | grep -q 'id="root"' || falhar "recarregar uma rota do React não abre o app"

status_api=$(curl -s -o /dev/null -w '%{http_code}' "$url/api/painel")
[[ "$status_api" == 401 ]] || falhar "/api/painel sem sessão deu $status_api, esperado 401"

curl -fsS -o /dev/null -D - -H 'X-Forwarded-Proto: https' "$url/api/auth/csrf" \
	| grep -i '^set-cookie: XSRF-TOKEN=' | grep -qi 'secure' \
	|| falhar "atrás de proxy HTTPS o cookie XSRF-TOKEN não sai com Secure"

echo "Implantação verificada: $url"
