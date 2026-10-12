#!/usr/bin/env bash
# shellcheck source=scripts/testes/lib.sh
source "$(dirname "$0")/lib.sh"

# Repositório git descartável dentro do projeto copiado.
repositorio() {
	cd "$PROJ"
	git init -q -b main .
	git config user.email teste@exemplo.org
	git config user.name Teste
}

commit() { git commit -q --allow-empty -m "$1"; }

agrupa_por_tipo() {
	repositorio
	commit "feat(relatorios): copiar pendências"
	commit "fix: CSV com tab"
	commit "docs: README"
	commit "Ajuste solto"
	git tag v1.0.0
	local saida
	saida=$(scripts/changelog.sh v1.0.0)
	afirmar_contem "$saida" "### Novidades"$'\n'"- **relatorios:** copiar pendências ("
	afirmar_contem "$saida" "### Correções"$'\n'"- CSV com tab ("
	afirmar_contem "$saida" "### Outras mudanças"
	afirmar_contem "$saida" "- README ("
	afirmar_contem "$saida" "- Ajuste solto ("
	[[ "$saida" =~ \(([0-9a-f]{7,})\) ]] || falhar "sem hash curto: $saida"
}

so_desde_a_tag_anterior() {
	repositorio
	commit "feat(relatorios): copiar pendências"
	git tag v1.0.0
	commit "feat: painel"
	git tag v1.1.0
	local saida
	saida=$(scripts/changelog.sh v1.1.0)
	afirmar_contem "$saida" "- painel ("
	afirmar_nao_contem "$saida" "copiar pendências"
}

secao_vazia_nao_aparece() {
	repositorio
	commit "fix: CSV com tab"
	git tag v1.0.0
	local saida
	saida=$(scripts/changelog.sh v1.0.0)
	afirmar_contem "$saida" "### Correções"
	afirmar_nao_contem "$saida" "### Novidades"
	afirmar_nao_contem "$saida" "### Outras mudanças"
}

ignora_merge() {
	repositorio
	commit "feat: base"
	git checkout -q -b ramo
	commit "fix: no ramo"
	git checkout -q main
	commit "docs: na main"
	git merge -q --no-ff -m "Merge branch 'ramo'" ramo
	git tag v1.0.0
	local saida
	saida=$(scripts/changelog.sh v1.0.0)
	afirmar_nao_contem "$saida" "Merge branch"
	afirmar_contem "$saida" "- no ramo ("
}

rodar_testes agrupa_por_tipo so_desde_a_tag_anterior secao_vazia_nao_aparece ignora_merge
