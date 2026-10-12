#!/usr/bin/env bash
# shellcheck source=scripts/testes/lib.sh
source "$(dirname "$0")/lib.sh"

# Troca o verificador da cópia por um falso que registra a URL e falha com VERIFICACAO_FALHA=1.
verificador_falso() {
	cat > "$PROJ/scripts/verificar-implantacao.sh" <<'FALSO'
#!/usr/bin/env bash
echo "$1" > "$(dirname "$0")/../verificador.log"
if [[ "${VERIFICACAO_FALHA:-0}" == 1 ]]; then
	echo "A aplicação não ficou saudável em 1s." >&2
	exit 1
fi
FALSO
	chmod +x "$PROJ/scripts/verificar-implantacao.sh"
}

versao_invalida_e_recusada() {
	local versao saida status
	for versao in latest v1.0 1.0.0 'v1.0.0; rm -rf ~' ''; do
		status=0
		saida=$("$PROJ/scripts/implantar.sh" "$versao" 2>&1) || status=$?
		afirmar_igual 2 "$status"
		afirmar_contem "$saida" "Versão inválida: $versao. Use o formato vX.Y.Z."
	done
	afirmar_igual "" "$(cat "$LOG_DOCKER")"
}

implanta_a_versao_pedida() {
	verificador_falso
	"$PROJ/scripts/implantar.sh" v1.2.3 > /dev/null
	local log
	log=$(cat "$LOG_DOCKER")
	afirmar_igual "APP_VERSAO=v1.2.3 compose pull app" "$(sed -n 1p "$LOG_DOCKER")"
	afirmar_igual "APP_VERSAO=v1.2.3 compose up -d app" "$(sed -n 2p "$LOG_DOCKER")"
	afirmar_igual 2 "$(echo "$log" | wc -l | tr -d ' ')"
	afirmar_igual "http://127.0.0.1:8080" "$(cat "$PROJ/verificador.log")"
}

app_que_nao_sobe_falha() {
	verificador_falso
	local saida status=0
	saida=$(VERIFICACAO_FALHA=1 "$PROJ/scripts/implantar.sh" v1.2.3 2>&1) || status=$?
	afirmar_igual 1 "$status"
	afirmar_contem "$saida" "A aplicação não ficou saudável"
}

pull_que_falha_para_antes_do_up() {
	verificador_falso
	local status=0
	DOCKER_FALHA=1 "$PROJ/scripts/implantar.sh" v1.2.3 > /dev/null 2>&1 || status=$?
	[[ "$status" != 0 ]] || falhar "deveria falhar"
	afirmar_nao_contem "$(cat "$LOG_DOCKER")" "up -d"
	[[ ! -e "$PROJ/verificador.log" ]] || falhar "não deveria verificar"
}

com_backup_sobe_o_banco_e_faz_o_dump_antes_da_troca() {
	verificador_falso
	DOCKER_SAIDA="PGDMP" "$PROJ/scripts/implantar.sh" v1.2.3 --backup > /dev/null
	afirmar_igual "APP_VERSAO= compose up -d --wait db" "$(sed -n 1p "$LOG_DOCKER")"
	afirmar_contem "$(sed -n 2p "$LOG_DOCKER")" "compose exec -T db"
	afirmar_igual "APP_VERSAO=v1.2.3 compose pull app" "$(sed -n 3p "$LOG_DOCKER")"
	afirmar_igual "APP_VERSAO=v1.2.3 compose up -d app" "$(sed -n 4p "$LOG_DOCKER")"
	afirmar_igual 1 "$(find "$PROJ/backups" -name 'doacoes-*.dump' | wc -l | tr -d ' ')"
}

backup_que_falha_para_antes_da_troca() {
	verificador_falso
	local saida status=0
	saida=$(DOCKER_FALHA_EM="exec" "$PROJ/scripts/implantar.sh" v1.2.3 --backup 2>&1) || status=$?
	afirmar_igual 1 "$status"
	afirmar_contem "$saida" "Backup falhou; nenhum arquivo foi gravado."
	afirmar_nao_contem "$(cat "$LOG_DOCKER")" "pull app"
	[[ ! -e "$PROJ/verificador.log" ]] || falhar "não deveria verificar"
}

opcao_desconhecida_e_recusada() {
	local status=0
	"$PROJ/scripts/implantar.sh" v1.2.3 --bakup > /dev/null 2>&1 || status=$?
	afirmar_igual 2 "$status"
	afirmar_igual "" "$(cat "$LOG_DOCKER")"
}

rodar_testes versao_invalida_e_recusada implanta_a_versao_pedida app_que_nao_sobe_falha \
	pull_que_falha_para_antes_do_up com_backup_sobe_o_banco_e_faz_o_dump_antes_da_troca \
	backup_que_falha_para_antes_da_troca opcao_desconhecida_e_recusada
