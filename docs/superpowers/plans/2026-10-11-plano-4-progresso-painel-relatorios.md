# Plano 4 — Progresso, Painel, Relatórios, OpenAPI e Postman — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Qualquer usuário vê, por campanha, meta × arrecadado × pendente e o total em R$ (detalhe e painel); relatórios em tela, CSV e impressão; texto de pendências para WhatsApp; API documentada pelo OpenAPI e coleção Postman versionada.

**Architecture:** Novo pacote `progresso/` concentra o cálculo (spec §7): consultas agregadas com `JdbcClient` (nada de totais armazenados — R6) e uma classe pura de cálculo (R5, percentual). Painel, detalhe da campanha e relatórios reutilizam o mesmo `ProgressoService`. Novo pacote `relatorio/` monta JSON, CSV (`comum/CsvWriter`) e texto puro. No frontend, componentes `BarraProgresso` e `TabelaProgresso` servem ao detalhe, ao painel e aos relatórios.

**Tech Stack:** igual aos Planos 1–3, mais `springdoc-openapi-starter-webmvc-ui` (linha 3.x compatível com Spring Boot 4.1 — confirmar a versão mais recente no Maven Central e na tabela de compatibilidade do springdoc) e `newman` via `npx` só para verificar a coleção.

**Spec:** `docs/superpowers/specs/2026-10-08-gestao-doacoes-design.md` (R5, R6; §4 itens 2, 3 e 6 e "percentual de progresso geral"; §5 progresso, painel, relatórios, documentação, Postman; §6 perfis; §8 testes)

**Base:** branch `feature/plano-4` criada a partir de `feature/plano-3`.

## Global Constraints

- Tudo dos Planos 1–3 continua valendo (`ProblemDetail`, `ApiTestSupport`, textos pt-BR, Checkstyle, JaCoCo ≥ 70% nos `*Service`, oxlint `--deny-warnings`, TS estrito; opções de `Select`/`Menu` do Mantine buscadas com `{ hidden: true }` nos testes).
- Autorização: todos os endpoints deste plano → qualquer usuário logado (spec §6: "Ver painel, campanhas, relatórios").
- R5: `pendente = max(meta − arrecadado, 0)`; `excedente = max(arrecadado − meta, 0)`.
- R6: arrecadado = `SUM(item_doacao.quantidade)` por necessidade; total em R$ = `SUM(doacao.valor)` das doações `DINHEIRO` da campanha. Nada é gravado.
- Percentual de uma necessidade: `floor(min(arrecadado / meta, 1) × 100)` (inteiro 0–100; só chega a 100 quando a meta é atingida). Percentual geral da campanha: `floor(média das frações min(arrecadado / meta, 1) × 100)`; `null` quando a campanha não tem necessidades (sem barra de itens). Percentual em R$: `floor(min(total / metaValor, 1) × 100)`; `null` sem `metaValor`.
- Formatos de texto no servidor (CSV e WhatsApp): quantidade pt-BR com até 2 casas, sem separador de milhar (`2,5`, `1500`); moeda `R$ 1.234,50` (espaço normal, não NBSP) no texto; no CSV os valores vão como número pt-BR sem símbolo (`1234,50`); datas `dd/MM/aaaa`.
- Rótulos de unidade iguais aos do frontend: `UNIDADE` "unidade(s)", `KG` "kg", `LITRO` "litro(s)", `PACOTE` "pacote(s)", `CAIXA` "caixa(s)", `PAR` "par(es)".
- CSV: UTF-8 **com BOM**, separador `;`, fim de linha `\r\n`; célula de texto com `;`, `"`, `\r` ou `\n` vai entre aspas (aspas internas dobradas); célula de texto que começa com `=`, `+`, `-` ou `@` recebe `'` na frente (injeção de fórmula).
- Perfis (spec §6): Swagger UI só no perfil `dev`; `/v3/api-docs` desligado no `prod`.
- Mensagens fixadas por este plano (usar exatamente):
  - `Informe a data inicial.` (campo `de`, 400) · `Informe a data final.` (campo `ate`, 400)
  - `A data final não pode ser anterior à inicial.` (campo `ate`, 400; também validado no frontend)
  - `Nenhuma pendência nas campanhas ativas.` (texto de pendências vazio)
  - `Nenhuma campanha ativa no momento.` · `Nenhuma pendência.` (painel)
  - `Nenhuma doação registrada.` (últimas doações do detalhe) · `Nenhuma doação no período.` (relatório)
  - `Texto copiado.` (notificação)

## Review Focus

1. **Doação acima da meta** (meta 10 kg, arrecadado 15 kg) → pendente 0, excedente 5 kg visível, barra em 100% (nunca 150%) → testes nas Tasks 1 e 3.
2. **Nome com `;`, aspas, quebra de linha ou começando com `=`** no CSV (doador `Silva; "Zé"`, título `=HYPERLINK(...)`) → célula íntegra, sem fórmula ao abrir no Excel → teste na Task 5.
3. **Acentos no CSV aberto no Excel do Windows** ("Feijão", "Doação") → BOM UTF-8 presente → teste na Task 5.
4. **Volta ao painel/detalhe depois de registrar ou excluir doação** → números atualizados, sem recarregar a página → teste na Task 3.
5. **Período invertido** (De 20/10, Até 10/10) → mensagem no campo Até, nenhuma requisição; pela API, 400 no campo `ate` → testes nas Tasks 5 e 7.

---

## Estrutura de arquivos

```
backend/src/main/java/br/org/iap/doacoes/
  campanha/Unidade.java                     (+ rotulo())
  campanha/CampanhaRepository.java          (+ findByStatusOrderByTituloAsc)
  comum/Formatos.java                       (quantidade, moeda, data em pt-BR)
  comum/CsvWriter.java
  progresso/CalculoProgresso.java           (puro: R5 e percentuais)
  progresso/ProgressoRepository.java        (@Repository com JdbcClient)
  progresso/ArrecadadoNecessidade.java      (linha da consulta)
  progresso/NecessidadeProgresso.java, ProgressoCampanhaResponse.java,
           PendenciaResponse.java, PainelResponse.java
  progresso/ProgressoService.java, ProgressoController.java (progresso + painel)
  relatorio/LinhaRelatorioDoacao.java, TotalItem.java, RelatorioDoacoesResponse.java
  relatorio/RelatorioService.java, RelatorioController.java
  config/OpenApiConfig.java
  resources/application*.yml               (springdoc por perfil)
frontend/src/
  api/client.ts (+ apiTexto), api/progresso.ts, api/relatorios.ts, api/doacoes.ts (+ tamanho)
  components/BarraProgresso.tsx, components/TabelaProgresso.tsx
  pages/CampanhaDetalhePage.tsx (progresso, total R$, últimas doações)
  pages/PainelPage.tsx, pages/RelatoriosPage.tsx
  estilos/impressao.css (importado em main.tsx)
  pages/EmConstrucaoPage.tsx (removido)
postman/registroDoacoes.postman_collection.json, postman/local.postman_environment.json
```

---

### Task 1: Cálculo e API de progresso da campanha

**Files:**
- Create: `progresso/CalculoProgresso.java`, `ArrecadadoNecessidade.java`, `ProgressoRepository.java`, `NecessidadeProgresso.java`, `ProgressoCampanhaResponse.java`, `ProgressoService.java`, `ProgressoController.java`
- Test: `progresso/CalculoProgressoTest.java`, `progresso/ProgressoApiTest.java`

**Interfaces:**
- Consumes: `CampanhaService.obter(Long)` (404 "Campanha não encontrada."), `ApiTestSupport`
- Produces:
  - `CalculoProgresso` (métodos estáticos): `BigDecimal pendente(BigDecimal meta, BigDecimal arrecadado)`, `BigDecimal excedente(BigDecimal meta, BigDecimal arrecadado)`, `int percentual(BigDecimal meta, BigDecimal arrecadado)`, `Integer percentualGeral(List<NecessidadeProgresso>)` (média das frações, não dos inteiros; `null` se vazia), `Integer percentualValor(BigDecimal metaValor, BigDecimal total)` (`null` se `metaValor` nulo)
  - `record ArrecadadoNecessidade(Long campanhaId, Long necessidadeId, String item, Unidade unidade, BigDecimal quantidadeMeta, String observacao, BigDecimal arrecadado)`
  - `ProgressoRepository`: `List<ArrecadadoNecessidade> arrecadadoPorNecessidade(Collection<Long> campanhaIds)` (`necessidade LEFT JOIN item_doacao`, `coalesce(sum, 0)`, ordem `campanha_id, item, id`); `Map<Long, BigDecimal> totalDinheiroPorCampanha(Collection<Long> campanhaIds)` (só `tipo = 'DINHEIRO'`; campanha sem doação fica fora do mapa). Coleção vazia → resultado vazio sem consultar.
  - `record NecessidadeProgresso(Long id, String item, Unidade unidade, BigDecimal quantidadeMeta, String observacao, BigDecimal arrecadado, BigDecimal pendente, BigDecimal excedente, int percentual)`
  - `record ProgressoCampanhaResponse(Long campanhaId, String titulo, StatusCampanha status, Integer percentualGeral, BigDecimal totalDinheiro, BigDecimal metaValor, Integer percentualValor, List<NecessidadeProgresso> necessidades)` (`totalDinheiro` = 0 sem doações, nunca nulo)
  - `ProgressoService`: `ProgressoCampanhaResponse daCampanha(Long campanhaId)`; `List<ProgressoCampanhaResponse> dasCampanhas(List<Campanha> campanhas)` (duas consultas no total, na ordem recebida)
  - Endpoint: `GET /api/campanhas/{id}/progresso` (logado) no `ProgressoController`

- [ ] **Step 1: Testes (falham)**

```java
// CalculoProgressoTest (puro)
@Test void metadeDaMeta()               { /* meta 10, arr 5 → pendente 5, excedente 0, percentual 50 */ }
@Test void acimaDaMetaLimitaEm100()     { /* meta 10, arr 15 → pendente 0, excedente 5, percentual 100 */ }
@Test void quaseCompletoNaoArredondaPara100() { /* meta 10, arr 9.99 → 99 */ }
@Test void fracaoArredondaParaBaixo()   { /* meta 3, arr 2 → 66 */ }
@Test void semDoacoes()                 { /* arr 0 → pendente = meta, percentual 0 */ }
@Test void geralEhMediaDasFracoes()     { /* necessidades em 50% e 150% → 75 (150 conta como 1) */ }
@Test void geralSemNecessidadesEhNulo() { }
@Test void percentualValor()            { /* meta 200, total 50 → 25; total 300 → 100; meta null → null */ }

// ProgressoApiTest (voluntário logado; campanha ATIVA "Agasalho" com Arroz 10 kg e Feijão 4 kg)
@Test void progressoSomaItensEDinheiro()  { /* doações: Arroz 3 + Arroz 2, Feijão 6, R$ 50 PIX + R$ 25,50 espécie
   → Arroz arrecadado 5, pendente 5, percentual 50; Feijão excedente 2, percentual 100;
     $.percentualGeral 75; $.totalDinheiro 75.5 */ }
@Test void necessidadeSemDoacaoVemComZero() { /* $.necessidades[?].arrecadado 0 */ }
@Test void campanhaSemNecessidadesNaoTemPercentualGeral() { /* $.percentualGeral null; $.necessidades vazio; $.totalDinheiro 0 */ }
@Test void doacoesDeOutraCampanhaNaoContam() { }
@Test void campanhaInexistenteRetorna404() { }
@Test void semLoginRetorna401() { }
```

- [ ] **Step 2: Rodar e ver falhar** — `./mvnw -B test -Dtest="CalculoProgressoTest,ProgressoApiTest"` → FAIL (classes inexistentes)
- [ ] **Step 3: Implementar** — `JdbcClient` com parâmetro de coleção (`in (:ids)`); `BigDecimal` com `RoundingMode.DOWN` para as frações (escala 6).
- [ ] **Step 4: Rodar e ver passar** — `./mvnw -B verify` → `BUILD SUCCESS`
- [ ] **Step 5: Commit** — `feat(progresso): cálculo e API de progresso da campanha`

---

### Task 2: API do painel

**Files:**
- Create: `progresso/PendenciaResponse.java`, `progresso/PainelResponse.java`
- Modify: `campanha/CampanhaRepository.java`, `progresso/ProgressoService.java`, `progresso/ProgressoController.java`
- Test: `progresso/PainelApiTest.java`

**Interfaces:**
- Consumes: Task 1
- Produces:
  - `CampanhaRepository.findByStatusOrderByTituloAsc(StatusCampanha)` → `List<Campanha>`
  - `record PendenciaResponse(Long campanhaId, String campanhaTitulo, Long necessidadeId, String item, Unidade unidade, BigDecimal pendente, int percentual)`
  - `record PainelResponse(List<ProgressoCampanhaResponse> campanhasAtivas, List<PendenciaResponse> maioresPendencias)`
  - `ProgressoService.painel()`: campanhas `ATIVA` em ordem de título; maiores pendências = necessidades dessas campanhas com `pendente > 0`, ordenadas por `percentual` asc, depois `pendente` desc, título da campanha e item; no máximo `10`. (Ordenar pelo percentual, não pela quantidade: "10 kg" e "100 unidades" não são comparáveis.)
  - Endpoint: `GET /api/painel` (logado)

- [ ] **Step 1: Testes (falham)**

```java
// PainelApiTest
@Test void painelListaSoCampanhasAtivasEmOrdemDeTitulo() { /* "Natal" ATIVA, "Agasalho" ATIVA, "Páscoa" RASCUNHO, "Inverno" ENCERRADA → títulos ["Agasalho", "Natal"] */ }
@Test void maioresPendenciasPorMenorPercentual()        { /* Arroz 0%, Feijão 50%, Óleo 100% → ["Arroz", "Feijão"]; Óleo ausente */ }
@Test void maioresPendenciasLimitadasA10()              { /* 12 necessidades pendentes → 10 */ }
@Test void pendenciasDeCampanhaEncerradaNaoAparecem()   { }
@Test void painelVazio()                                { /* sem campanhas → listas vazias, 200 */ }
```

- [ ] **Step 2: Rodar e ver falhar** — `./mvnw -B test -Dtest=PainelApiTest` → FAIL
- [ ] **Step 3: Implementar** — reutilize `dasCampanhas` (sem consultas extras por campanha).
- [ ] **Step 4: Rodar e ver passar** — `./mvnw -B verify` → `BUILD SUCCESS`
- [ ] **Step 5: Commit** — `feat(progresso): API do painel`

---

### Task 3: Progresso no detalhe da campanha

**Files:**
- Create: `frontend/src/api/progresso.ts`, `components/BarraProgresso.tsx`, `components/TabelaProgresso.tsx`
- Modify: `api/doacoes.ts` (`tamanho?: number` em `FiltroDoacoes`), `pages/CampanhaDetalhePage.tsx`, `pages/NecessidadeFormModal.tsx`, `pages/DoacaoFormPage.tsx`, `pages/DoacoesPage.tsx`
- Test: `pages/CampanhaDetalhePage.test.tsx`, `pages/DoacoesPage.test.tsx`

**Interfaces:**
- Consumes: Tasks 1–2 (formato JSON)
- Produces:
  - `api/progresso.ts`: tipos `NecessidadeProgresso`, `ProgressoCampanha`, `Pendencia`, `Painel` (espelham os records); `buscarProgresso(campanhaId: number): Promise<ProgressoCampanha>`; `buscarPainel(): Promise<Painel>`; `invalidarTotais(qc: QueryClient): Promise<void>` (invalida `['progresso']`, `['painel']` e `['relatorios']`)
  - Chaves de query: `['progresso', campanhaId]`, `['painel']`, `['relatorios', ...]`
  - `BarraProgresso({ percentual, rotulo }: { percentual: number; rotulo: string })` — `Progress` do Mantine com `aria-label={rotulo}` e texto `${percentual}%` ao lado
  - `TabelaProgresso({ necessidades, acoes }: { necessidades: NecessidadeProgresso[]; acoes?: (n: NecessidadeProgresso) => ReactNode })` — colunas Item (com observação embaixo, em texto pequeno) · Meta · Arrecadado · Pendente · Progresso; quando `excedente > 0`, a célula Pendente mostra `+{qtd} acima da meta` (ex.: `+2 kg acima da meta`); no celular, Meta e Progresso ficam ocultos (`visibleFrom="sm"`) e a barra vai embaixo do item
- Detalhe da campanha passa a usar só `['progresso', id]` para a tabela (sai a query `['necessidades', id]`; o modal de edição recebe `{ ...n, campanhaId: id }`). Acrescenta:
  - "Progresso dos itens" (`BarraProgresso`) quando `percentualGeral != null`
  - "Arrecadado em dinheiro: R$ X" + ` de R$ meta` e barra quando há `metaValor`
  - seção "Últimas doações": `listarDoacoes({ campanhaId, pagina: 0, tamanho: 5 })`, cada linha com data, `doadorNome ?? 'Anônimo'` e `resumoDoacao`; vazia → `Nenhuma doação registrada.`
- Salvar/excluir necessidade invalida `['progresso', campanhaId]`; registrar, editar e excluir doação chamam `invalidarTotais`.

- [ ] **Step 1: Testes (falham)**

```tsx
// CampanhaDetalhePage.test.tsx (atualizar mocks: buscarProgresso no lugar de listarNecessidades)
test('mostra meta, arrecadado e pendente de cada necessidade') // Arroz: "10 kg", "5 kg", "5 kg"; barra aria-label "Progresso de Arroz" com valor 50
test('arrecadado acima da meta mostra excedente e barra em 100%') // Feijão meta 4, arr 6 → "+2 kg acima da meta"; barra 100
test('mostra progresso geral e total em dinheiro')  // "Progresso dos itens" 75%; "Arrecadado em dinheiro: R$ 75,50 de R$ 200,00"
test('campanha sem necessidades não mostra barra de itens') // sem "Progresso dos itens"
test('lista as últimas doações')                    // "Anônimo", "Arroz 5 kg"; listarDoacoes chamado com tamanho 5
test('sem doações mostra mensagem')                 // "Nenhuma doação registrada."
// os testes existentes de editar/excluir necessidade continuam passando com os dados vindos do progresso

// DoacoesPage.test.tsx
test('excluir doação invalida os totais') // após Excluir → Confirmar, um buscarProgresso montado no mesmo QueryClient é chamado de novo (ou spy em invalidateQueries com ['progresso'] e ['painel'])
```

- [ ] **Step 2: Rodar e ver falhar** — `npm test -- CampanhaDetalhePage DoacoesPage` (em `frontend/`) → FAIL
- [ ] **Step 3: Implementar**
- [ ] **Step 4: Rodar e ver passar** — `npm run lint && npx tsc -b && npm test` → tudo verde
- [ ] **Step 5: Commit** — `feat(frontend): progresso, total em dinheiro e últimas doações no detalhe da campanha`

---

### Task 4: Painel

**Files:**
- Create: `pages/PainelPage.tsx`, `pages/PainelPage.test.tsx`
- Modify: `App.tsx` (rota `index` → `PainelPage`), `App.test.tsx` se ele procurar o título do placeholder
- Delete: `pages/EmConstrucaoPage.tsx` só depois da Task 7 (ela ainda serve `/relatorios`)

**Interfaces:**
- Consumes: `buscarPainel`, `BarraProgresso` (Task 3)
- Produces: `PainelPage` — título "Painel"; botão "Registrar doação" → `/doacoes/nova`; seção "Campanhas ativas" com um card por campanha (título com link para `/campanhas/{id}`, barra "Progresso dos itens de {titulo}" quando `percentualGeral != null`, linha `R$ X arrecadados` + ` de R$ meta` e barra "Arrecadação de {titulo}" quando há meta); vazio → `Nenhuma campanha ativa no momento.`; seção "Maiores pendências", uma linha por item: `{item}: faltam {formatarQuantidade(pendente, unidade)}` + título da campanha em texto pequeno; vazio → `Nenhuma pendência.`

- [ ] **Step 1: Testes (falham)**

```tsx
test('painel mostra cards das campanhas ativas com progresso') // "Agasalho", barra "Progresso dos itens de Agasalho" 75, "R$ 75,50 arrecadados de R$ 200,00"
test('campanha sem meta em dinheiro mostra só o arrecadado')   // "R$ 30,00 arrecadados", sem barra de arrecadação
test('lista as maiores pendências')                             // "Arroz: faltam 5 kg"
test('painel vazio mostra mensagens')                           // ambas as mensagens fixadas
test('card leva ao detalhe da campanha')                        // clicar no título → heading da campanha (mock buscarCampanha/buscarProgresso)
```

- [ ] **Step 2: Rodar e ver falhar** — `npm test -- PainelPage` → FAIL
- [ ] **Step 3: Implementar**
- [ ] **Step 4: Rodar e ver passar** — `npm run lint && npx tsc -b && npm test` → tudo verde
- [ ] **Step 5: Commit** — `feat(frontend): painel com campanhas ativas e maiores pendências`

---

### Task 5: Relatórios por campanha e por período (JSON e CSV)

**Files:**
- Create: `comum/Formatos.java`, `comum/CsvWriter.java`, `relatorio/LinhaRelatorioDoacao.java`, `TotalItem.java`, `RelatorioDoacoesResponse.java`, `RelatorioService.java`, `RelatorioController.java`
- Modify: `campanha/Unidade.java` (`String rotulo()`)
- Test: `comum/FormatosTest.java`, `comum/CsvWriterTest.java`, `relatorio/RelatorioApiTest.java`

**Interfaces:**
- Consumes: `ProgressoService.daCampanha`, `DoacaoRepository` + `DoacaoFiltros.de(...)`, `DoacaoMontador.montar(List<Doacao>, UsuarioAutenticado)`
- Produces:
  - `Formatos`: `static String quantidade(BigDecimal)` (`2,5`; `1500`), `static String numero(BigDecimal)` (CSV: `1234,50`, sempre 2 casas), `static String moeda(BigDecimal)` (`R$ 1.234,50`), `static String data(LocalDate)` (`15/10/2026`)
  - `CsvWriter`: `CsvWriter linha(String... celulas)`; `byte[] paraBytes()` (BOM + conteúdo UTF-8). Regras de célula: ver Global Constraints. Célula `null` → vazia.
  - `record LinhaRelatorioDoacao(Long id, LocalDate dataDoacao, String campanhaTitulo, String doadorNome, TipoDoacao tipo, String descricao, BigDecimal valor)` — `doadorNome` "Anônimo" quando nulo; `descricao`: ITEM → `Arroz 5 kg, Feijão 2,5 kg`; DINHEIRO → `PIX` / `Espécie` / `Outro`
  - `record TotalItem(String item, Unidade unidade, BigDecimal quantidade)` — agrupado por item (sem diferenciar maiúsculas) + unidade, ordem por item
  - `record RelatorioDoacoesResponse(LocalDate de, LocalDate ate, long quantidade, BigDecimal totalDinheiro, List<TotalItem> totaisItens, List<LinhaRelatorioDoacao> doacoes)` — doações em ordem `dataDoacao` asc, `id` asc; sem paginação
  - `RelatorioService`: `ProgressoCampanhaResponse campanha(Long id)`, `byte[] campanhaCsv(Long id)`, `RelatorioDoacoesResponse doacoes(LocalDate de, LocalDate ate, Long campanhaId, TipoDoacao tipo, UsuarioAutenticado)`, `byte[] doacoesCsv(...)` (mesmos parâmetros). `de`/`ate` nulos → `CampoInvalidoException` com as mensagens fixadas; `ate < de` → campo `ate`.
  - Endpoints (logado): `GET /api/relatorios/campanha/{id}` e `GET /api/relatorios/doacoes?de=&ate=&campanhaId=&tipo=`; com `?formato=csv` respondem `text/csv;charset=UTF-8` e `Content-Disposition: attachment; filename="campanha-{id}.csv"` / `filename="doacoes-{de}-a-{ate}.csv"` (datas ISO)
  - Layout do CSV da campanha:
    ```
    Campanha;{titulo}
    Item;Unidade;Meta;Arrecadado;Pendente;Excedente;Percentual
    Arroz;kg;10,00;5,00;5,00;0,00;50
    Total em dinheiro;75,50
    Meta em dinheiro;200,00            (célula vazia sem meta)
    ```
  - Layout do CSV de doações: `Data;Campanha;Doador;Tipo;Descrição;Valor`, tipo `Itens`/`Dinheiro`, valor vazio para ITEM; última linha `Total em dinheiro;;;;;{total}`

- [ ] **Step 1: Testes (falham)**

```java
// FormatosTest
@Test void quantidade() { /* 2.50 → "2,5"; 1500.00 → "1500"; 0.25 → "0,25" */ }
@Test void moeda()      { /* 1234.5 → "R$ 1.234,50", sem NBSP */ }
@Test void numero()     { /* 75.5 → "75,50" */ }

// CsvWriterTest
@Test void comecaComBom()                 { /* bytes 0..2 = EF BB BF */ }
@Test void separaPorPontoEVirgulaComCrlf() { }
@Test void escapaPontoEVirgulaAspasEQuebra() { /* Silva; "Zé" → "Silva; ""Zé""" ; "a\nb" entre aspas */ }
@Test void neutralizaFormula()            { /* "=HYPERLINK(x)" → "'=HYPERLINK(x)"; "+1", "-1", "@a" idem */ }
@Test void acentosSobrevivem()            { /* "Feijão" decodificado em UTF-8 igual */ }

// RelatorioApiTest
@Test void relatorioDaCampanhaEmJson()      { /* igual ao progresso */ }
@Test void relatorioDaCampanhaEmCsv()       { /* content-type text/csv; disposition filename="campanha-{id}.csv"; linha "Arroz;kg;10,00;5,00;5,00;0,00;50"; "Total em dinheiro;75,50" */ }
@Test void relatorioDeDoacoesFiltraEOrdena() { /* 3 doações em 01, 10 e 20/10; de=05 ate=20 → 2, em ordem crescente; totalDinheiro; totaisItens somados */ }
@Test void relatorioDeDoacoesAnonimaMostraAnonimo() { }
@Test void relatorioDeDoacoesEmCsv()        { /* cabeçalho fixado; linha "10/10/2026;Agasalho;Anônimo;Itens;Arroz 5 kg;"; última "Total em dinheiro;;;;;50,00" */ }
@Test void periodoObrigatorio()             { /* sem de → 400 campo "de"; sem ate → campo "ate" */ }
@Test void periodoInvertidoRetorna400()     { /* campo "ate", mensagem fixada */ }
```

- [ ] **Step 2: Rodar e ver falhar** — `./mvnw -B test -Dtest="FormatosTest,CsvWriterTest,RelatorioApiTest"` → FAIL
- [ ] **Step 3: Implementar** — `Formatos` com `DecimalFormat` e `DecimalFormatSymbols(Locale.of("pt", "BR"))`, nunca `NumberFormat.getCurrencyInstance` (usa NBSP).
- [ ] **Step 4: Rodar e ver passar** — `./mvnw -B verify` → `BUILD SUCCESS` (JaCoCo cobre `RelatorioService`)
- [ ] **Step 5: Commit** — `feat(relatorio): relatórios por campanha e por período com exportação CSV`

---

### Task 6: Texto de pendências para WhatsApp

**Files:**
- Modify: `relatorio/RelatorioService.java`, `relatorio/RelatorioController.java`
- Test: `relatorio/PendenciasTextoTest.java` (unidade, `ProgressoService` mockado), `relatorio/RelatorioApiTest.java`

**Interfaces:**
- Consumes: `ProgressoService.dasCampanhas`, `CampanhaRepository.findByStatusOrderByTituloAsc`, `Formatos.quantidade`, `Unidade.rotulo()`
- Produces: `String RelatorioService.pendencias(Long campanhaId)` e `GET /api/relatorios/pendencias?campanhaId=` → `text/plain;charset=UTF-8`. Só campanhas `ATIVA` (com `campanhaId`, apenas ela, se ativa); campanha sem item pendente fica de fora; itens em ordem alfabética; blocos separados por uma linha em branco; sem pendências → `Nenhuma pendência nas campanhas ativas.`

```
*Campanha do Agasalho*
Ainda precisamos de:
• Arroz: 5 kg
• Feijão: 2,5 kg

*Natal Solidário*
Ainda precisamos de:
• Brinquedo: 20 unidade(s)
```

- [ ] **Step 1: Testes (falham)**

```java
// PendenciasTextoTest
@Test void textoNoFormatoFixado()               { /* duas campanhas → string exata acima (sem \n final extra) */ }
@Test void campanhaSemPendenciaFicaDeFora()      { }
@Test void semPendenciasMostraMensagem()        { }
// RelatorioApiTest
@Test void pendenciasEmTextoPuro()               { /* content-type text/plain;charset=UTF-8; contém "• Arroz: 5 kg" */ }
@Test void pendenciasFiltraPorCampanha()         { /* ?campanhaId= da segunda → só ela */ }
@Test void campanhaEncerradaNaoTemPendencias()   { /* ?campanhaId= encerrada → mensagem vazia fixada */ }
```

- [ ] **Step 2: Rodar e ver falhar** — `./mvnw -B test -Dtest="PendenciasTextoTest,RelatorioApiTest"` → FAIL
- [ ] **Step 3: Implementar**
- [ ] **Step 4: Rodar e ver passar** — `./mvnw -B verify` → `BUILD SUCCESS`
- [ ] **Step 5: Commit** — `feat(relatorio): texto de pendências para WhatsApp`

---

### Task 7: Tela de Relatórios (tela, CSV, impressão, WhatsApp)

**Files:**
- Create: `api/relatorios.ts`, `pages/RelatoriosPage.tsx`, `pages/RelatoriosPage.test.tsx`, `estilos/impressao.css`
- Modify: `api/client.ts` (+ `apiTexto`), `api/client.test.ts`, `main.tsx` (importa `impressao.css`), `App.tsx` (rota `relatorios`)
- Delete: `pages/EmConstrucaoPage.tsx`

**Interfaces:**
- Consumes: Tasks 3, 5, 6; `TabelaProgresso`, `formatarData`, `formatarMoeda`, `formatarQuantidade`, `hojeISO`
- Produces:
  - `apiTexto(caminho: string): Promise<string>` em `client.ts` — mesmo tratamento de erro/401 de `api` (extraia a parte comum), `Accept: text/plain`
  - `api/relatorios.ts`: tipos `LinhaRelatorioDoacao`, `TotalItem`, `RelatorioDoacoes`; `FiltroRelatorioDoacoes = { de: string; ate: string; campanhaId?: number; tipo?: TipoDoacao }`; `buscarRelatorioCampanha(id)`, `buscarRelatorioDoacoes(filtro)`, `buscarPendencias(campanhaId?: number): Promise<string>`, `urlCsvCampanha(id): string`, `urlCsvDoacoes(filtro): string`
  - `RelatoriosPage` com `Tabs`: "Por campanha" | "Doações por período" | "Pendências"
    - Por campanha: `Select` "Campanha" (todas, `tamanho: 100`); mostra título, `TabelaProgresso` e "Total em dinheiro"; botões "Baixar CSV" (`<a href={urlCsvCampanha(id)}>`) e "Imprimir" (`window.print()`)
    - Doações por período: De (padrão: dia 1 do mês atual, data local), Até (padrão `hojeISO()`), Campanha (opcional), Tipo (opcional); se `ate < de`, erro no campo Até com a mensagem fixada e nenhuma busca; tabela Data · Campanha · Doador · Descrição · Valor; resumo `{n} doações · Total em dinheiro: R$ X` e totais por item; vazio → `Nenhuma doação no período.`; "Baixar CSV" e "Imprimir"
    - Pendências: `Select` "Campanha" opcional ("Todas as campanhas ativas"); `Textarea` somente leitura com o texto; "Copiar texto" (`navigator.clipboard.writeText` → notificação `Texto copiado.`) e "Abrir no WhatsApp" (`https://wa.me/?text=` + `encodeURIComponent(texto)`, `target="_blank"`, `rel="noopener noreferrer"`)
  - `impressao.css`: em `@media print` esconde cabeçalho, menu lateral e todo elemento `.nao-imprimir` (filtros, abas e botões) e zera o recuo do `main` do AppShell

- [ ] **Step 1: Testes (falham)**

```tsx
// client.test.ts
test('apiTexto devolve o corpo como texto e trata 401 como api')

// RelatoriosPage.test.tsx
test('relatório por campanha mostra a tabela e o link do CSV')   // href "/api/relatorios/campanha/1?formato=csv"
test('doações por período usa o mês atual como padrão')          // vi.setSystemTime(2026-10-15T23:30 local) → De "01/10/2026", Até "15/10/2026"; buscarRelatorioDoacoes chamado com de "2026-10-01", ate "2026-10-15"
test('período invertido mostra erro e não busca')               // De 20/10, Até 10/10 → mensagem fixada; nenhuma chamada nova
test('mostra totais e link do CSV com os filtros')               // "2 doações · Total em dinheiro: R$ 50,00"; href com de, ate, campanhaId e formato=csv
test('período sem doações mostra mensagem')
test('pendências: copiar texto')                                 // clipboard mockado recebe o texto; "Texto copiado."
test('pendências: link do WhatsApp leva o texto codificado')
test('Imprimir chama window.print')
```

- [ ] **Step 2: Rodar e ver falhar** — `npm test -- RelatoriosPage client` → FAIL
- [ ] **Step 3: Implementar**
- [ ] **Step 4: Rodar e ver passar** — `npm run lint && npx tsc -b && npm test && npm run build` → tudo verde
- [ ] **Step 5: Commit** — `feat(frontend): tela de relatórios com CSV, impressão e pendências para WhatsApp`

---

### Task 8: Documentação OpenAPI

**Files:**
- Create: `config/OpenApiConfig.java`
- Modify: `backend/pom.xml` (springdoc), `application.yml`, `application-dev.yml`, `application-prod.yml`, todos os `*Controller.java` (`@Tag`)
- Test: `config/OpenApiTest.java`, `config/OpenApiProdTest.java`

**Interfaces:**
- Produces:
  - `OpenApiConfig`: bean `OpenAPI` com `title "API de Gestão de Doações — IAP Joinville"`, `version` = versão do projeto (de `BuildProperties` se disponível, senão `"dev"`), descrição curta em pt-BR explicando login por sessão e o cabeçalho `X-XSRF-TOKEN`; esquema de segurança `sessao` (apiKey, cookie `JSESSIONID`) aplicado globalmente
  - Configuração: `application.yml` → `springdoc.swagger-ui.enabled: false`; `application-dev.yml` → `springdoc.swagger-ui.enabled: true`, `springdoc.swagger-ui.path: /swagger-ui`; `application-prod.yml` → `springdoc.api-docs.enabled: false`
  - `@Tag` por controller: Autenticação, Usuários, Campanhas, Necessidades, Doadores, Doações, Progresso e painel, Relatórios
  - Arquivo gerado `backend/target/openapi.json` (usado pela release no Plano 5)

- [ ] **Step 1: Testes (falham)**

```java
// OpenApiTest (ApiTestSupport, sem perfil)
@Test void especificacaoPublicaContemOsEndpoints() { /* GET /v3/api-docs sem login → 200; paths contêm "/api/doacoes", "/api/campanhas/{id}/progresso", "/api/painel", "/api/relatorios/pendencias"; grava o corpo em target/openapi.json */ }
@Test void swaggerUiDesligadoForaDoDev()          { /* GET /swagger-ui/index.html → 404 */ }
// OpenApiProdTest (@ActiveProfiles("prod"))
@Test void apiDocsDesligadoEmProducao()           { /* GET /v3/api-docs → 404 */ }
```

- [ ] **Step 2: Rodar e ver falhar** — `./mvnw -B test -Dtest="OpenApiTest,OpenApiProdTest"` → FAIL (404 em `/v3/api-docs`)
- [ ] **Step 3: Implementar** — se o perfil `prod` exigir variáveis ausentes no teste, defina-as em `@TestPropertySource` do próprio teste.
- [ ] **Step 4: Rodar e ver passar** — `./mvnw -B verify` → `BUILD SUCCESS`; `target/openapi.json` existe. Conferir à mão `http://localhost:8080/swagger-ui` com o perfil `dev`.
- [ ] **Step 5: Commit** — `docs(api): especificação OpenAPI com springdoc e Swagger UI no perfil dev`

---

### Task 9: Coleção Postman

**Files:**
- Create: `postman/registroDoacoes.postman_collection.json`, `postman/local.postman_environment.json`, `postman/README.md`

**Interfaces:**
- Consumes: todos os endpoints
- Produces:
  - Ambiente `local`: `baseUrl` `http://localhost:8080`, `adminEmail` e `adminSenha` **vazios** (preenchidos por quem roda; nunca versionar senha)
  - Script de coleção pós-resposta: se a resposta trouxer o cookie `XSRF-TOKEN`, guarda em `{{xsrf}}`; toda requisição não-GET envia `X-XSRF-TOKEN: {{xsrf}}`
  - Pastas e requisições, em ordem, cada uma com `pm.test` do status e dos campos principais, guardando ids em variáveis da coleção:
    1. Autenticação: CSRF (`GET /api/auth/csrf`), Login, Eu (`/me`)
    2. Campanhas: Criar (título com `{{$timestamp}}`), Ativar (`PATCH .../status`), Buscar, Listar
    3. Necessidades: Criar Arroz 10 KG, Listar
    4. Doações: Registrar itens (Arroz 4, anônima), Registrar dinheiro (R$ 50 PIX), Listar por campanha
    5. Progresso: Progresso da campanha (testa arrecadado 4, pendente 6, `totalDinheiro` 50), Painel
    6. Relatórios: Campanha (JSON), Campanha (CSV — `Content-Type` contém `text/csv`), Pendências (contém `• Arroz: 6 kg`)
    7. Logout
  - `postman/README.md`: como importar no Postman e como rodar com newman (comando abaixo)

- [ ] **Step 1: Rodar contra o backend local e ver falhar** — com `docker compose up -d db` e o backend em `dev` rodando, `npx --yes newman@6 run postman/registroDoacoes.postman_collection.json -e postman/local.postman_environment.json --env-var adminEmail=<ADMIN_EMAIL do .env> --env-var adminSenha=<ADMIN_SENHA do .env>` com a coleção só com a pasta 1 e um teste do progresso → FAIL nos testes ausentes/errados (prova que os `pm.test` checam algo)
- [ ] **Step 2: Completar a coleção**
- [ ] **Step 3: Rodar de novo** → `0 failed` em todas as asserções
- [ ] **Step 4: Commit** — `docs(postman): coleção cobrindo login, campanhas, necessidades, doações, progresso e relatórios`

---

### Task 10: Verificação ponta a ponta no navegador

Sem código novo, a menos que apareça defeito. Defeito encontrado → teste que falha, correção, suíte verde e commit `fix: ...`.

- [ ] **Step 1:** Subir banco, backend (`dev`) e `npm run dev`; entrar como ADMIN e como voluntário.
- [ ] **Step 2: Conferir**, registrando o resultado no ledger:
  - Painel: cards das campanhas ativas, barras e R$; maiores pendências; "Registrar doação" abre o formulário
  - Registrar uma doação de itens e voltar ao painel e ao detalhe → números atualizados sem recarregar
  - Doação acima da meta → "+N acima da meta", barra 100%
  - Detalhe: últimas doações; campanha sem necessidades sem barra de itens
  - Relatório por campanha: "Baixar CSV" baixa o arquivo; abrir o CSV mostra "Feijão" com acento e `Silva; "Zé"` numa célula só
  - Relatório por período: padrão do mês; período invertido mostra o erro; CSV com os filtros
  - "Imprimir": a pré-visualização não mostra menu, filtros nem botões
  - Pendências: "Copiar texto" e "Abrir no WhatsApp"
  - Swagger UI em `http://localhost:8080/swagger-ui` lista os grupos
  - Celular (375 px): painel, detalhe e relatórios sem rolagem horizontal
- [ ] **Step 3: Suíte final** — `./mvnw -B verify` (backend) e `npm run lint && npx tsc -b && npm test && npm run build` (frontend) → tudo verde
