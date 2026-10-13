#!/usr/bin/env bash
# Backup do banco (pg_dump, formato custom) em backups/doacoes-AAAAMMDD-HHMMSS.dump.
# Roda da pasta do projeto, com o Compose no ar. Restauração: ver README.
# BACKUP_DIR: pasta dos arquivos (padrão backups). BACKUP_MANTER: quantos manter (padrão 30).
set -euo pipefail
cd "$(dirname "$0")/.."

pasta="${BACKUP_DIR:-backups}"
manter="${BACKUP_MANTER:-30}"
mkdir -p "$pasta"

arquivo="$pasta/doacoes-$(TZ=America/Sao_Paulo date +%Y%m%d-%H%M%S).dump"
parcial="$arquivo.parcial"
trap 'rm -f "$parcial"' EXIT

# As variáveis são as do contêiner do banco, por isso as aspas simples.
# shellcheck disable=SC2016
if ! docker compose exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$parcial" \
	|| [[ ! -s "$parcial" ]]; then
	echo "Backup falhou; nenhum arquivo foi gravado." >&2
	exit 1
fi
mv "$parcial" "$arquivo"
echo "Backup gravado em $arquivo"

# Os nomes têm data e hora, então a ordem alfabética é a cronológica.
mapfile -t antigos < <(printf '%s\n' "$pasta"/doacoes-*.dump | sort -r | tail -n +"$((manter + 1))")
if (( ${#antigos[@]} > 0 )); then
	rm -f -- "${antigos[@]}"
fi
