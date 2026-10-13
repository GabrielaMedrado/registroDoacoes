#!/usr/bin/env bash
# Mini-framework dos testes dos scripts: cópia isolada do projeto e um `docker` falso no PATH.
# Variáveis do docker falso: DOCKER_FALHA=1 (sai com 1), DOCKER_FALHA_EM=<trecho> (sai com 1 só nas chamadas
# que contêm o trecho), DOCKER_SAIDA (o que escreve no stdout).
set -euo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

preparar_projeto() {
	TMP="$(mktemp -d)"
	trap 'rm -rf "$TMP"' EXIT
	PROJ="$TMP/proj"
	mkdir -p "$PROJ/scripts" "$TMP/bin"
	cp "$RAIZ"/scripts/*.sh "$PROJ/scripts/"
	: > "$PROJ/docker-compose.yml"
	export LOG_DOCKER="$TMP/docker.log"
	: > "$LOG_DOCKER"
	cat > "$TMP/bin/docker" <<'FALSO'
#!/usr/bin/env bash
echo "APP_VERSAO=${APP_VERSAO:-} $*" >> "$LOG_DOCKER"
if [[ "${DOCKER_FALHA:-0}" == 1 ]]; then exit 1; fi
if [[ -n "${DOCKER_FALHA_EM:-}" && "$*" == *"$DOCKER_FALHA_EM"* ]]; then exit 1; fi
printf '%s' "${DOCKER_SAIDA-}"
FALSO
	chmod +x "$TMP/bin/docker"
	export PATH="$TMP/bin:$PATH"
}

falhar() {
	echo "  $1" >&2
	exit 1
}

afirmar_igual() { [[ "$2" == "$1" ]] || falhar "esperado '$1', obtido '$2'"; }
afirmar_contem() { [[ "$1" == *"$2"* ]] || falhar "esperado conter '$2' em: $1"; }
afirmar_nao_contem() { [[ "$1" != *"$2"* ]] || falhar "não deveria conter '$2' em: $1"; }

# Roda cada função cujo nome foi passado, isolada num subshell; sai com 1 se alguma falhar.
rodar_testes() {
	local falhas=0 teste
	for teste in "$@"; do
		if ( preparar_projeto; "$teste" ); then
			echo "ok - $teste"
		else
			echo "FALHOU - $teste"
			falhas=$((falhas + 1))
		fi
	done
	(( falhas == 0 ))
}
