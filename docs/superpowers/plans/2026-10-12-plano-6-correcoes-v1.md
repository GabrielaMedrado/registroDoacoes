# Plano 6: Correções antes da v1.0 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fechar quatro falhas que afetam o uso real antes da tag `v1.0.0`: backup que apaga tudo, workflows sem permissões mínimas e sem timeout, "Editar" oferecido em doação que não pode mais ser alterada e edição simultânea de doação que sobrescreve em silêncio.

**Architecture:** Cada correção fica na camada que tem a regra: validação no próprio `scripts/backup.sh`; `permissions`/`timeout-minutes` nos YAML, com um teste de shell que lê os arquivos; `podeAlterar` da resposta passa a incluir "campanha ativa" (o service já recusa com 409); travamento otimista com coluna `versao` + `@Version`, conferida contra a versão enviada pelo formulário.

**Tech Stack:** Bash + mini-framework de `scripts/testes/lib.sh`; GitHub Actions + actionlint; Spring Boot 4.1 / JPA / Flyway / MockMvc; React 19 + Vitest.

**Spec:** `docs/superpowers/specs/2026-10-08-gestao-doacoes-design.md`

**Branch:** `feature/plano-6`, criada a partir da `main` (já com o PR #1 mergeado).

## Global Constraints

- Erros: `ProblemDetail` com `erros: [{campo, mensagem}]` para validação; `400` validação, `403` sem permissão, `404` não encontrado, `409` violação de regra de negócio.
- R8: `VOLUNTARIO` edita/exclui apenas doações que ele registrou; `ADMIN` edita/exclui qualquer uma.
- "O frontend esconde ações não permitidas, mas não é a proteção" — toda regra continua no service.
- Textos visíveis em português, mesmo tom das mensagens existentes (ex.: `"Só é possível registrar ou alterar doações em campanhas ativas."`).
- Checkstyle e JaCoCo ≥ 70% nos `*Service` continuam passando (`./mvnw -B verify`).
- Scripts passam no ShellCheck (`koalaman/shellcheck:v0.10.0`) e workflows no actionlint (`rhysd/actionlint:1.7.7`). No Git Bash: `MSYS_NO_PATHCONV=1` ou `-v "$(pwd -W):/repo"` com `MSYS2_ARG_CONV_EXCL='/repo'`.
- Um commit local por tarefa, terminando com a linha `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. Duas pessoas abrem a mesma doação, ambas salvam: a segunda recebe 409 com a mensagem de recarregar, nunca sobrescreve em silêncio — inclusive quando as duas leituras passam pela conferência ao mesmo tempo (o `@Version` pega na gravação). Task 4.
2. `BACKUP_MANTER` vazio, `0`, `-1`, `abc`, `1.5`: recusado antes de qualquer `pg_dump`, sem apagar nada; vazio continua sendo o padrão 30. Task 1.
3. ADMIN abrindo `/doacoes/{id}/editar` de campanha encerrada pelo link direto: vê o aviso de campanha não ativa, não o de "só doações que você registrou". Task 3.
4. Campanha que volta de ENCERRADA para ATIVA: o "Editar" volta a aparecer (o valor é calculado, não gravado). Task 3.
5. Workflow chamado (`ci.yml` via `workflow_call`) com `permissions` menores que as do chamador continua funcionando no `release.yml`. Task 2.

---

### Task 1: `BACKUP_MANTER` inválido é recusado

**Files:**
- Modify: `scripts/backup.sh`
- Test: `scripts/testes/backup.test.sh`
- Modify: `README.md` (linha da tabela de configuração de `BACKUP_DIR`, `BACKUP_MANTER`)

**Interfaces:**
- Produces: `scripts/backup.sh` sai com status 2 e a mensagem `BACKUP_MANTER inválido: <valor>. Use um número inteiro maior que zero.` (stderr) antes de chamar o `docker`, quando `BACKUP_MANTER` está definido e não casa `^[1-9][0-9]*$`. Não definido ou vazio = 30.

- [ ] **Step 1: Escrever o teste que falha** em `scripts/testes/backup.test.sh` e incluí-lo no `rodar_testes`:

```bash
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
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `bash scripts/testes/backup.test.sh`
Expected: `manter_invalido_e_recusado_sem_apagar_nada` FALHA (com `0` o script grava e apaga tudo; status 0). `manter_vazio_usa_o_padrao` pode falhar também (`tail -n +1` apaga o novo).

- [ ] **Step 3: Implementar** em `scripts/backup.sh`: `manter="${BACKUP_MANTER:-30}"` já trata vazio; logo depois, antes do `mkdir`, validar com `[[ "$manter" =~ ^[1-9][0-9]*$ ]]` e sair com 2 e a mensagem exata acima. Atualizar o comentário do cabeçalho ("inteiro maior que zero").

- [ ] **Step 4: Rodar e ver passar**

Run: `bash scripts/testes/todos.sh`
Expected: todos os testes passam (17 no total).

- [ ] **Step 5: ShellCheck**

Run: `MSYS_NO_PATHCONV=1 docker run --rm -v "$(pwd -W):/repo" -w /repo koalaman/shellcheck:v0.10.0 scripts/*.sh scripts/testes/*.sh`
Expected: sem saída.

- [ ] **Step 6: README** — na tabela, `BACKUP_MANTER`: "quantos manter (padrão 30; inteiro maior que zero)".

- [ ] **Step 7: Commit**

```bash
git add scripts/backup.sh scripts/testes/backup.test.sh README.md
git commit -m "fix(backup): recusa BACKUP_MANTER inválido em vez de apagar todos os backups"
```

---

### Task 2: Workflows com permissões mínimas e timeout

**Files:**
- Create: `scripts/testes/workflows.test.sh`
- Modify: `.github/workflows/ci.yml`, `.github/workflows/release.yml`, `.github/workflows/deploy.yml`

**Interfaces:**
- Produces: cada workflow tem `permissions:` no nível do arquivo (`contents: read`); todo job com `runs-on:` tem `timeout-minutes:`. Jobs que já declaram `permissions` próprias (em `release.yml`: `imagem`, `pages`, `publicar`) continuam com as suas.

- [ ] **Step 1: Escrever o teste que falha** em `scripts/testes/workflows.test.sh` (usa `lib.sh` só pelas asserções; não precisa de `preparar_projeto` — lê os arquivos do repositório a partir de `$(dirname "$0")/../..`):

```bash
cada_workflow_declara_permissoes_no_topo() {
	local wf
	for wf in "$RAIZ"/.github/workflows/*.yml; do
		grep -q '^permissions:' "$wf" || falhar "sem permissions no topo: $(basename "$wf")"
	done
}

cada_job_com_runs_on_tem_timeout() {
	# Conta, por arquivo, jobs com runs-on e jobs com timeout-minutes (ambos na indentação de chave do job).
	local wf
	for wf in "$RAIZ"/.github/workflows/*.yml; do
		afirmar_igual "$(grep -c '^    runs-on:' "$wf")" "$(grep -c '^    timeout-minutes:' "$wf")"
	done
}

rodar_testes cada_workflow_declara_permissoes_no_topo cada_job_com_runs_on_tem_timeout
```

(Se `afirmar_igual` não mostrar o arquivo na falha, prefixe a comparação com uma mensagem via `falhar`; o importante é a falha apontar o workflow.)

- [ ] **Step 2: Rodar e ver falhar**

Run: `bash scripts/testes/workflows.test.sh`
Expected: as duas FALHAM (nenhum workflow tem `permissions` no topo; nenhum job tem timeout).

- [ ] **Step 3: Implementar** — `permissions:\n  contents: read` no topo dos três arquivos, e `timeout-minutes` por job:

| Workflow | Job | Minutos |
|---|---|---|
| ci.yml | backend | 20 |
| ci.yml | frontend | 10 |
| ci.yml | scripts | 5 |
| release.yml | preparar | 5 |
| release.yml | imagem | 30 |
| release.yml | pages | 15 |
| release.yml | publicar | 5 |
| deploy.yml | homologacao | 15 |
| deploy.yml | producao | 15 |

`release.yml/verificar` é `uses: ./.github/workflows/ci.yml` — não aceita `timeout-minutes` (os jobs do ci.yml já têm os seus) nem `runs-on`, então o teste não o conta.

- [ ] **Step 4: Rodar e ver passar + actionlint**

Run: `bash scripts/testes/todos.sh`
Expected: todos passam.

Run: `MSYS2_ARG_CONV_EXCL='/repo' docker run --rm -v "$(pwd -W):/repo" -w /repo rhysd/actionlint:1.7.7 -color`
Expected: sem saída.

- [ ] **Step 5: Commit**

```bash
git add .github/workflows scripts/testes/workflows.test.sh
git commit -m "ci: permissões mínimas e timeout em todos os workflows"
```

---

### Task 3: "Editar" só aparece em doação de campanha ativa

**Files:**
- Modify: `backend/src/main/java/br/org/iap/doacoes/doacao/DoacaoMontador.java`
- Modify: `backend/src/main/java/br/org/iap/doacoes/doacao/DoacaoResponse.java`
- Test: `backend/src/test/java/br/org/iap/doacoes/doacao/DoacaoApiTest.java`
- Modify: `frontend/src/api/doacoes.ts`, `frontend/src/pages/DoacaoFormPage.tsx`
- Test: `frontend/src/pages/DoacaoFormPage.test.tsx` (e ajustar as fixtures de `Doacao` nos testes do frontend que montam o tipo completo)

**Interfaces:**
- Consumes: `DoacaoMontador.podeAlterar(Doacao, UsuarioAutenticado)` (estático, só R8) — continua igual, porque o service o usa para o 403.
- Produces: `DoacaoResponse` ganha `boolean campanhaAtiva` (logo antes de `podeAlterar`); `podeAlterar` = R8 **e** campanha `ATIVA`. No frontend, `Doacao.campanhaAtiva: boolean`.

- [ ] **Step 1: Teste de API que falha** em `DoacaoApiTest`:

```java
@Test
void doacaoDeCampanhaEncerradaNaoPodeSerAlterada() throws Exception {
	long id = idDe(registrar(admin, doacaoDeItens(campanhaId, hoje, arrozId, "2")));
	mudarStatus(campanhaId, "ENCERRADA");
	mvc.perform(get("/api/doacoes/" + id).session(admin))
			.andExpect(jsonPath("$.campanhaAtiva").value(false))
			.andExpect(jsonPath("$.podeAlterar").value(false));
	mudarStatus(campanhaId, "ATIVA");
	mvc.perform(get("/api/doacoes/" + id).session(admin))
			.andExpect(jsonPath("$.campanhaAtiva").value(true))
			.andExpect(jsonPath("$.podeAlterar").value(true));
}
```

(Use os auxiliares que o arquivo já tem para registrar e extrair o id; se não existir `idDe`, extraia com `JsonPath.read(...)` como os outros testes fazem.)

- [ ] **Step 2: Rodar e ver falhar**

Run: `cd backend && ./mvnw -B -q test -Dtest=DoacaoApiTest -Dcheckstyle.skip -Djacoco.skip`
Expected: FALHA — `No value at JSON path "$.campanhaAtiva"`.

- [ ] **Step 3: Implementar** — no `montar(List<Doacao>, …)`, trocar o mapa de títulos por um mapa `Long → Campanha` (já é carregado em lote, sem consulta a mais) e preencher `campanhaAtiva = campanha.getStatus() == StatusCampanha.ATIVA` e `podeAlterar = podeAlterar(d, usuario) && campanhaAtiva`.

- [ ] **Step 4: Rodar e ver passar**

Run: o mesmo do Step 2. Expected: PASS.

- [ ] **Step 5: Teste de frontend que falha** em `DoacaoFormPage.test.tsx`:

```tsx
test('doação de campanha não ativa mostra aviso em vez do formulário', async () => {
  // buscarDoacao devolve { ...doacaoExistente, campanhaAtiva: false, podeAlterar: false }
  // render em /doacoes/7/editar
  expect(await screen.findByText('Só é possível alterar doações de campanhas ativas.')).toBeInTheDocument()
  expect(screen.queryByText('Você só pode alterar doações que você registrou.')).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Salvar' })).not.toBeInTheDocument()
})
```

(Siga o padrão de mock/render do teste `'editar carrega a doação e salva com atualizarDoacao'`; o nome do botão é o que o formulário de edição já usa.)

- [ ] **Step 6: Rodar e ver falhar**

Run: `cd frontend && npx vitest run src/pages/DoacaoFormPage.test.tsx`
Expected: FALHA — aparece "Você só pode alterar doações que você registrou.".

- [ ] **Step 7: Implementar** — `campanhaAtiva: boolean` no tipo `Doacao`; em `DoacaoFormPage`, quando `!podeAlterar`, a mensagem é `'Só é possível alterar doações de campanhas ativas.'` se `!campanhaAtiva`, senão a atual. Acrescentar `campanhaAtiva: true` às fixtures de `Doacao` existentes para o `typecheck` passar.

- [ ] **Step 8: Rodar e ver passar**

Run: `cd frontend && npm run lint && npm run typecheck && npm test`
Expected: tudo verde.

- [ ] **Step 9: Commit**

```bash
git add backend/src frontend/src
git commit -m "fix(doacoes): Editar só aparece em doação de campanha ativa"
```

---

### Task 4: Edição simultânea de doação dá 409 em vez de sobrescrever

**Files:**
- Create: `backend/src/main/resources/db/migration/V3__doacao_versao.sql`
- Modify: `Doacao.java`, `DoacaoRequest.java`, `DoacaoResponse.java`, `DoacaoMontador.java`, `DoacaoService.java`
- Modify: `backend/src/main/java/br/org/iap/doacoes/comum/GlobalExceptionHandler.java`
- Test: `DoacaoApiTest.java`, `DoacaoServiceTest.java`, Create `backend/src/test/java/br/org/iap/doacoes/comum/GlobalExceptionHandlerTest.java`
- Modify: `frontend/src/api/doacoes.ts`, `frontend/src/pages/DoacaoFormPage.tsx`
- Test: `frontend/src/pages/DoacaoFormPage.test.tsx`

**Interfaces:**
- Consumes: `DoacaoResponse` com `campanhaAtiva` (Task 3).
- Produces:
  - Migration `V3`: `alter table doacao add column versao bigint not null default 0;`
  - `Doacao`: `@Version private Long versao;` + `getVersao()`.
  - `DoacaoRequest`: último componente `Long versao` (ignorado no POST; obrigatório no PUT).
  - `DoacaoResponse`: `Long versao` como último componente.
  - `DoacaoService.MSG_DOACAO_ALTERADA = "Esta doação foi alterada por outra pessoa. Recarregue a página e tente de novo."`
  - `GlobalExceptionHandler`: `@ExceptionHandler(ObjectOptimisticLockingFailureException.class)` → 409 com `"Este registro foi alterado por outra pessoa. Recarregue a página e tente de novo."`
  - Frontend: `Doacao.versao: number`; `DoacaoDados.versao?: number`.

- [ ] **Step 1: Testes de API que falham** em `DoacaoApiTest`:

```java
@Test
void edicaoComVersaoDesatualizadaRetorna409() throws Exception {
	// registra (versao 0); PUT com versao 0 → 200 e "$.versao" == 1;
	// PUT de novo com versao 0 → 409, "$.detail" == "Esta doação foi alterada por outra pessoa. Recarregue a página e tente de novo."
	// GET → os dados são os do primeiro PUT
}

@Test
void edicaoSemVersaoRetorna400() throws Exception {
	// PUT sem "versao" → 400, "$.erros[0].campo" == "versao", mensagem "Recarregue a página e tente de novo."
}
```

Os JSONs de PUT dos testes existentes (`autorEditaEExcluiAPropriaDoacao`, `voluntarioRecebe403AoEditarDoacaoDeOutro`) passam a mandar `"versao"` lido do POST.

- [ ] **Step 2: Rodar e ver falhar**

Run: `cd backend && ./mvnw -B -q test -Dtest=DoacaoApiTest -Dcheckstyle.skip -Djacoco.skip`
Expected: FALHA — o segundo PUT devolve 200 (sobrescreve).

- [ ] **Step 3: Teste de unidade que falha** em `DoacaoServiceTest`: `atualizarComVersaoDiferenteLancaConflito` — doação com versão 2, request com versão 1 → `RegraNegocioException` com `MSG_DOACAO_ALTERADA`, e `doacoes.save` nunca chamado. Run: `./mvnw -B -q test -Dtest=DoacaoServiceTest -Dcheckstyle.skip -Djacoco.skip` → FALHA (não compila / não lança).

- [ ] **Step 4: Implementar o backend**
  - Migration e `@Version` como no bloco Interfaces.
  - `DoacaoService.atualizar`: depois de `obterParaAlterar` (403/409 de campanha continuam antes), se `req.versao() == null` → `CampoInvalidoException("versao", "Recarregue a página e tente de novo.")`; se diferente de `doacao.getVersao()` → `RegraNegocioException(MSG_DOACAO_ALTERADA)`. Depois disso, o fluxo atual. Usar `saveAndFlush` para o `@Version` falhar dentro da requisição e a resposta já trazer a versão nova.
  - `DoacaoMontador` preenche `versao`.
  - Handler de `ObjectOptimisticLockingFailureException` no `GlobalExceptionHandler` (cobre a corrida em que as duas leituras passam pela conferência).

- [ ] **Step 5: Teste do handler** em `GlobalExceptionHandlerTest.conflitoDeVersaoViraConflito`: chamar o método do handler com `new ObjectOptimisticLockingFailureException(Doacao.class, 1L)` e afirmar status 409 e o `detail` exato. (Escrever antes do handler, ver falhar por não compilar, depois passar.)

- [ ] **Step 6: Rodar e ver passar**

Run: `cd backend && ./mvnw -B verify`
Expected: BUILD SUCCESS, Checkstyle e JaCoCo ok. Se `MigracaoFlywayTest` conta migrations, ajustar a contagem esperada para 3.

- [ ] **Step 7: Teste de frontend que falha** em `DoacaoFormPage.test.tsx`: `'editar envia a versão carregada'` — doação com `versao: 4`; ao salvar, `atualizarDoacao` é chamado com `(7, expect.objectContaining({ versao: 4 }))`. Run: `npx vitest run src/pages/DoacaoFormPage.test.tsx` → FALHA.

- [ ] **Step 8: Implementar o frontend** — `versao` nos tipos; `paraDados` (ou o `mutationFn`) inclui `versao: doacao.versao` só na edição. A mensagem de 409 já aparece pelo `erroGeral` (`e.message`); acrescentar `versao` às fixtures de `Doacao`.

- [ ] **Step 9: Rodar e ver passar**

Run: `cd frontend && npm run lint && npm run typecheck && npm test && npm run build`
Expected: tudo verde.

- [ ] **Step 10: Verificação manual curta** — `docker compose up -d --build app`, abrir a mesma doação em duas abas, salvar na primeira e depois na segunda: a segunda mostra o alerta "Esta doação foi alterada por outra pessoa…". Depois `docker compose stop app`.

- [ ] **Step 11: Commit**

```bash
git add backend frontend
git commit -m "fix(doacoes): edição simultânea dá 409 em vez de sobrescrever (versão otimista)"
```

---

## Ao terminar

- Suítes: backend `./mvnw -B verify`, frontend `npm run lint && npm run typecheck && npm test && npm run build`, scripts `bash scripts/testes/todos.sh`.
- Revisão final da branch inteira, depois push da `feature/plano-6` e PR para `main`.
