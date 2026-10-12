#!/usr/bin/env bash
# Roda todos os testes dos scripts (*.test.sh desta pasta).
set -uo pipefail
falhas=0
for teste in "$(dirname "$0")"/*.test.sh; do
	echo "# $(basename "$teste")"
	bash "$teste" || falhas=$((falhas + 1))
done
(( falhas == 0 ))
