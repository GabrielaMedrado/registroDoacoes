#!/usr/bin/env bash
# shellcheck source=scripts/testes/lib.sh
source "$(dirname "$0")/lib.sh"

grava_dump_com_data_no_nome() {
	local saida
	saida=$(DOCKER_SAIDA="PGDMP-conteudo" "$PROJ/scripts/backup.sh")
	local arquivos=("$PROJ"/backups/*)
	afirmar_igual 1 "${#arquivos[@]}"
	[[ "$(basename "${arquivos[0]}")" =~ ^doacoes-[0-9]{8}-[0-9]{6}\.dump$ ]] || falhar "nome inesperado: ${arquivos[0]}"
	afirmar_igual "PGDMP-conteudo" "$(cat "${arquivos[0]}")"
	afirmar_contem "$saida" "Backup gravado em backups/doacoes-"
	afirmar_contem "$(cat "$LOG_DOCKER")" "compose exec -T db"
}

falha_do_pg_dump_nao_deixa_arquivo() {
	local saida status=0
	saida=$(DOCKER_FALHA=1 "$PROJ/scripts/backup.sh" 2>&1) || status=$?
	afirmar_igual 1 "$status"
	afirmar_contem "$saida" "Backup falhou; nenhum arquivo foi gravado."
	afirmar_igual "" "$(ls -A "$PROJ/backups")"
}

dump_vazio_e_falha() {
	local status=0
	DOCKER_SAIDA="" "$PROJ/scripts/backup.sh" > /dev/null 2>&1 || status=$?
	afirmar_igual 1 "$status"
	afirmar_igual "" "$(ls -A "$PROJ/backups")"
}

mantem_so_os_mais_recentes() {
	mkdir -p "$PROJ/backups"
	local antigo
	for antigo in 20260101-000000 20260102-000000 20260103-000000; do
		echo velho > "$PROJ/backups/doacoes-$antigo.dump"
	done
	DOCKER_SAIDA="novo" BACKUP_MANTER=2 "$PROJ/scripts/backup.sh" > /dev/null
	local arquivos
	arquivos=$(ls "$PROJ/backups")
	afirmar_igual 2 "$(echo "$arquivos" | wc -l | tr -d ' ')"
	afirmar_contem "$arquivos" "doacoes-20260103-000000.dump"
	afirmar_nao_contem "$arquivos" "doacoes-20260101-000000.dump"
	afirmar_nao_contem "$arquivos" "doacoes-20260102-000000.dump"
}

manter_invalido_e_recusado_sem_apagar_nada() {
	mkdir -p "$PROJ/backups"
	echo velho > "$PROJ/backups/doacoes-20260101-000000.dump"
	local valor saida status
	for valor in 0 -1 abc 1.5 ' 3'; do
		status=0
		saida=$(BACKUP_MANTER="$valor" DOCKER_SAIDA="novo" "$PROJ/scripts/backup.sh" 2>&1) || status=$?
		afirmar_igual 2 "$status"
		afirmar_contem "$saida" "BACKUP_MANTER inválido: $valor. Use um número inteiro maior que zero."
	done
	afirmar_igual "doacoes-20260101-000000.dump" "$(ls "$PROJ/backups")"
	afirmar_igual "" "$(cat "$LOG_DOCKER")"
}

manter_vazio_usa_o_padrao() {
	BACKUP_MANTER="" DOCKER_SAIDA="novo" "$PROJ/scripts/backup.sh" > /dev/null
	afirmar_igual 1 "$(find "$PROJ/backups" -name 'doacoes-*.dump' | wc -l | tr -d ' ')"
}

rodar_testes grava_dump_com_data_no_nome falha_do_pg_dump_nao_deixa_arquivo dump_vazio_e_falha \
	mantem_so_os_mais_recentes manter_invalido_e_recusado_sem_apagar_nada manter_vazio_usa_o_padrao
