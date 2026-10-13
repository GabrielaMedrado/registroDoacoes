#!/usr/bin/env bash
# Notas de versão em Markdown a partir dos Conventional Commits desde a tag anterior.
# Uso: scripts/changelog.sh vX.Y.Z
set -euo pipefail

tag="${1:?informe a tag, ex.: v1.0.0}"
if anterior=$(git describe --tags --abbrev=0 "$tag^" 2>/dev/null); then
	intervalo="$anterior..$tag"
else
	intervalo="$tag"
fi

novidades=""
correcoes=""
outras=""
padrao='^([a-z]+)(\(([^)]+)\))?!?: (.*)$'

while IFS=$'\t' read -r hash assunto; do
	tipo=""
	texto="$assunto"
	if [[ "$assunto" =~ $padrao ]]; then
		tipo="${BASH_REMATCH[1]}"
		texto="${BASH_REMATCH[4]}"
		if [[ -n "${BASH_REMATCH[3]}" ]]; then
			texto="**${BASH_REMATCH[3]}:** $texto"
		fi
	fi
	linha="- $texto ($hash)"$'\n'
	case "$tipo" in
		feat) novidades+="$linha" ;;
		fix) correcoes+="$linha" ;;
		*) outras+="$linha" ;;
	esac
done < <(git log --no-merges --format='%h%x09%s' "$intervalo")

secao() {
	if [[ -n "$2" ]]; then
		printf '### %s\n%s\n' "$1" "$2"
	fi
}

secao "Novidades" "$novidades"
secao "Correções" "$correcoes"
secao "Outras mudanças" "$outras"
