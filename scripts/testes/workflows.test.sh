#!/usr/bin/env bash
# Lê os workflows do repositório: permissões mínimas no topo e tempo máximo em todo job.
# shellcheck source=scripts/testes/lib.sh
source "$(dirname "$0")/lib.sh"

cada_workflow_declara_permissoes_no_topo() {
	local wf
	for wf in "$RAIZ"/.github/workflows/*.yml; do
		grep -q '^permissions:' "$wf" || falhar "sem permissions no topo: $(basename "$wf")"
	done
}

cada_job_com_runs_on_tem_timeout() {
	# Jobs que chamam outro workflow (uses:) não têm runs-on nem aceitam timeout-minutes.
	local wf jobs timeouts
	for wf in "$RAIZ"/.github/workflows/*.yml; do
		jobs=$(grep -c '^    runs-on:' "$wf")
		timeouts=$(grep -c '^    timeout-minutes:' "$wf")
		[[ "$jobs" == "$timeouts" ]] || falhar "$(basename "$wf"): $jobs jobs com runs-on, $timeouts com timeout-minutes"
	done
}

rodar_testes cada_workflow_declara_permissoes_no_topo cada_job_com_runs_on_tem_timeout
