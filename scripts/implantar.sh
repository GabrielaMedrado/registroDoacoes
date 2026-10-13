#!/usr/bin/env bash
# Troca a aplicação do servidor pela versão pedida e só termina bem se ela ficar saudável.
# Uso: scripts/implantar.sh vX.Y.Z [--backup]   (APP_URL: endereço local da aplicação, padrão http://127.0.0.1:8080)
# --backup: sobe o banco (se for o primeiro deploy, ele ainda não existe), faz o backup e só então troca a versão.
set -euo pipefail

versao="${1:-}"
if [[ ! "$versao" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
	echo "Versão inválida: $versao. Use o formato vX.Y.Z." >&2
	exit 2
fi

backup=false
case "${2:-}" in
	"") ;;
	--backup) backup=true ;;
	*)
		echo "Opção desconhecida: $2. Use --backup ou nada." >&2
		exit 2
		;;
esac

cd "$(dirname "$0")/.."
if [[ "$backup" == true ]]; then
	docker compose up -d --wait db
	scripts/backup.sh
fi

export APP_VERSAO="$versao"
docker compose pull app
docker compose up -d app
scripts/verificar-implantacao.sh "${APP_URL:-http://127.0.0.1:8080}"
