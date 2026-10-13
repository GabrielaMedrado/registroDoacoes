# Plano 3 — Doadores e Doações — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Voluntários cadastram doadores e registram doações (itens ou dinheiro, anônimas ou não) com as regras R1–R4, R8 e R9 garantidas no servidor; todos consultam e filtram doações; ADMIN anonimiza doadores.

**Architecture:** Novos pacotes `doador/` e `doacao/` no backend no padrão dos Planos 1–2 (controller fino → service com regras → JPA; DTOs `record`; erros via `GlobalExceptionHandler`). `Doacao` é dona dos seus `ItemDoacao` (cascade + orphanRemoval). Filtros da listagem com JPA `Specification`. No frontend, um formulário único de doação serve para registrar e editar, com seletor de doador que permite cadastrar na hora.

**Tech Stack:** igual aos Planos 1–2.

**Spec:** `docs/superpowers/specs/2026-10-08-gestao-doacoes-design.md` (§3 `doador`, `doacao`, `item_doacao`, R1–R4, R8, R9; §4 itens 4 e 5; §5 endpoints de doações e doadores; §6 tabela de autorização)

**Base:** branch `feature/plano-3` criada a partir de `feature/plano-2`.

## Global Constraints

- Tudo dos Planos 1–2 continua valendo (`PaginaResponse`, `?pagina=&tamanho=`, `ProblemDetail`, `ApiTestSupport`, textos pt-BR, Checkstyle, JaCoCo ≥ 70% nos `*Service`, oxlint, TS estrito; nos testes de frontend, opções de `Select` e itens de `Menu` do Mantine são buscados com `{ hidden: true }`).
- Autorização (spec §6): registrar doação e cadastrar/editar doador → qualquer usuário logado; editar/excluir doação → o próprio autor (`registrado_por`) ou ADMIN (R8); anonimizar doador → ADMIN.
- Enums: `TipoDoacao { ITEM, DINHEIRO }`, `FormaPagamento { PIX, ESPECIE, OUTRO }`.
- Normalização no servidor: doação `ITEM` grava `valor` e `formaPagamento` como `null`; doação `DINHEIRO` grava sem itens.
- Anonimizar (R9): `nome = "Anônimo"`, `telefone`, `email` e `observacao` = `null`; as doações continuam ligadas ao doador.
- Datas: `dataDoacao` não pode ser futura (comparada com `LocalDate.now(clock)`); no frontend, o padrão "hoje" é a data **local**.
- Mensagens fixadas por este plano (usar exatamente):
  - `Só é possível registrar ou alterar doações em campanhas ativas.` (409)
  - `Adicione ao menos um item.` (campo `itens`, 400)
  - `Este item não pertence à campanha escolhida.` (campo `itens`, 400)
  - `O mesmo item foi informado mais de uma vez.` (campo `itens`, 400)
  - `Informe o valor.` (campo `valor`, 400) · `Escolha a forma de pagamento.` (campo `formaPagamento`, 400)
  - `A data da doação não pode ser futura.` (campo `dataDoacao`, 400)
  - `Doador não encontrado.` (campo `doadorId`, 400 no registro; 404 em `/api/doadores/{id}`)
  - `Você só pode alterar doações que você registrou.` (403, com `detail`)
  - `Doação não encontrada.` (404)
  - `Doação registrada.` (aviso de sucesso no frontend)

## Review Focus

1. **"Hoje" depois das 21h** — `new Date().toISOString()` em Brasília já é o dia seguinte; o campo Data deve vir com a data local → teste na Task 4.
2. **Item de outra campanha** — trocar a campanha no formulário depois de escolher itens (ou uma requisição forjada) → frontend limpa os itens ao trocar a campanha; backend responde 400 no campo `itens` → testes nas Tasks 2 e 6.
3. **Voluntário alterando doação de outra pessoa** — botões não aparecem; se tentar pela API, 403 com a mensagem fixada → testes nas Tasks 3 e 7.
4. **Telefone digitado com formatação diferente na busca** (`47 99999-1234` vs cadastrado `(47) 99999-1234`) → encontra o doador → teste na Task 1.
5. **Toque duplo em "Registrar"** no celular → uma única doação → teste na Task 6.

---

## Estrutura de arquivos

```
backend/src/main/java/br/org/iap/doacoes/
  comum/AcessoNegadoException.java                 (novo; 403 com detail)
  comum/GlobalExceptionHandler.java                (+ handler 403)
  doador/Doador.java, DoadorRepository.java, DoadorRequest.java, DoadorResponse.java,
         DoadorService.java, DoadorController.java
  doacao/TipoDoacao.java, FormaPagamento.java, Doacao.java, ItemDoacao.java,
         DoacaoRepository.java, DoacaoFiltros.java (Specifications),
         DoacaoRequest.java, ItemDoacaoRequest.java, DoacaoResponse.java, ItemDoacaoResponse.java,
         DoacaoService.java, DoacaoController.java
frontend/src/
  api/doadores.ts, api/doacoes.ts, api/campanhas.ts (+ tamanho em listarCampanhas)
  util/formatos.ts (+ hojeISO, ROTULO_FORMA, resumoDoacao)
  pages/DoadoresPage.tsx, DoadorFormModal.tsx, DoadorDetalhePage.tsx
  pages/DoacaoFormPage.tsx, components/SeletorDoador.tsx
  pages/DoacoesPage.tsx
  pages/CampanhaDetalhePage.tsx (link "Registrar doação" + confirmação de status)
```

---

### Task 1: API de Doadores

**Files:**
- Create: `doador/Doador.java`, `DoadorRepository.java`, `DoadorRequest.java`, `DoadorResponse.java`, `DoadorService.java`, `DoadorController.java`
- Test: `doador/DoadorServiceTest.java`, `doador/DoadorApiTest.java`

**Interfaces:**
- Consumes: `PaginaResponse`, `ApiTestSupport`, exceções de `comum/`
- Produces:
  - Entidade `Doador` (tabela `doador`; `criadoEm` em `@PrePersist`); construtor `Doador(String nome, String telefone, String email, String observacao)`; `void anonimizar()`
  - `DoadorRepository`: `Page<Doador> buscar(String termo, String digitos, Pageable)` — consulta nativa: `nome ILIKE '%'||termo||'%'` OU (`digitos <> ''` E `regexp_replace(telefone, '\D', '', 'g') LIKE '%'||digitos||'%'`); sem termo, lista todos; ordem `nome` asc, `id` asc
  - `record DoadorRequest(@NotBlank @Size(max=120) String nome, @Size(max=30) String telefone, @Email @Size(max=160) String email, @Size(max=1000) String observacao)` (campos opcionais em branco gravados como `null`)
  - `record DoadorResponse(Long id, String nome, String telefone, String email, String observacao, OffsetDateTime criadoEm)`
  - `DoadorService`: `listar(String busca, int pagina, int tamanho)`, `buscar(Long id)`, `criar(DoadorRequest)`, `atualizar(Long id, DoadorRequest)`, `DoadorResponse anonimizar(Long id)`, `Doador obter(Long id)` (404 "Doador não encontrado.")
  - Endpoints: `GET /api/doadores?busca=&pagina=&tamanho=`, `GET /api/doadores/{id}`, `POST /api/doadores` → 201, `PUT /api/doadores/{id}` (logado); `POST /api/doadores/{id}/anonimizar` (ADMIN) → 200

- [ ] **Step 1: Testes (falham)**

```java
// DoadorServiceTest (repositório mockado)
@Test void criarGravaCamposVaziosComoNulo()   { /* telefone " ", email "" → null */ }
@Test void anonimizarApagaDadosPessoais()      { /* nome "Anônimo"; telefone, email, observacao null */ }
@Test void buscarInexistenteLanca404()         { /* NaoEncontradoException "Doador não encontrado." */ }

// DoadorApiTest (ADMIN ana, VOLUNTARIO vitor)
@Test void voluntarioCadastraEEditaDoador()    { /* POST 201 $.nome; PUT 200 */ }
@Test void nomeEmBrancoRetorna400()            { /* campo "nome" */ }
@Test void buscaPorNomeIgnoraCaixa()           { /* "Maria Souza" → ?busca=maria → total 1 */ }
@Test void buscaPorTelefoneIgnoraFormatacao()  { /* cadastrado "(47) 99999-1234" → ?busca=47 99999-1234 e ?busca=999991234 → total 1 */ }
@Test void voluntarioNaoAnonimiza()            { /* POST /anonimizar → 403 */ }
@Test void adminAnonimizaDoador()              { /* 200, $.nome "Anônimo", $.telefone null */ }
```

- [ ] **Step 2: Rodar e ver falhar** — `./mvnw -B test -Dtest="DoadorServiceTest,DoadorApiTest"` → FAIL
- [ ] **Step 3: Implementar**
- [ ] **Step 4: Rodar e ver passar** — `./mvnw -B verify` → `BUILD SUCCESS`
- [ ] **Step 5: Commit** — `feat(doador): API de doadores com busca e anonimização`

---

### Task 2: Registrar doação (regras R1–R4)

**Files:**
- Create: `doacao/TipoDoacao.java`, `FormaPagamento.java`, `Doacao.java`, `ItemDoacao.java`, `DoacaoRepository.java`, `DoacaoRequest.java`, `ItemDoacaoRequest.java`, `DoacaoResponse.java`, `ItemDoacaoResponse.java`, `DoacaoService.java`, `DoacaoController.java`
- Test: `doacao/DoacaoServiceTest.java` (Mockito), `doacao/DoacaoApiTest.java`

**Interfaces:**
- Consumes: `CampanhaService.obter(Long)`, `NecessidadeRepository`, `DoadorService.obter(Long)`, `UsuarioRepository`, `UsuarioAutenticado`, `Clock`
- Produces:
  - Entidade `Doacao` (tabela `doacao`; `campanhaId`, `doadorId`, `registradoPorId` como `Long`; `criadoEm` em `@PrePersist`; `@OneToMany(mappedBy = "doacao", cascade = ALL, orphanRemoval = true) List<ItemDoacao> itens`); `void substituirItens(List<ItemDoacao>)`
  - Entidade `ItemDoacao` (tabela `item_doacao`; `@ManyToOne Doacao doacao`; `necessidadeId` `Long`; `quantidade`)
  - `record ItemDoacaoRequest(@NotNull Long necessidadeId, @NotNull @Positive @Digits(integer=10, fraction=2) BigDecimal quantidade)`
  - `record DoacaoRequest(@NotNull Long campanhaId, Long doadorId, @NotNull TipoDoacao tipo, @Positive @Digits(integer=10, fraction=2) BigDecimal valor, FormaPagamento formaPagamento, @NotNull LocalDate dataDoacao, @Size(max=1000) String observacao, @Valid List<ItemDoacaoRequest> itens)` (`itens` nulo tratado como lista vazia)
  - `record ItemDoacaoResponse(Long necessidadeId, String item, Unidade unidade, BigDecimal quantidade)`
  - `record DoacaoResponse(Long id, Long campanhaId, String campanhaTitulo, Long doadorId, String doadorNome, TipoDoacao tipo, BigDecimal valor, FormaPagamento formaPagamento, LocalDate dataDoacao, String observacao, Long registradoPorId, String registradoPorNome, OffsetDateTime criadoEm, List<ItemDoacaoResponse> itens, boolean podeAlterar)` — `podeAlterar` = usuário atual é ADMIN ou o autor
  - `DoacaoService`: `DoacaoResponse registrar(DoacaoRequest, UsuarioAutenticado)`, `DoacaoResponse buscar(Long id, UsuarioAutenticado)`; método privado de validação reutilizado na Task 3
  - Endpoints: `POST /api/doacoes` → 201, `GET /api/doacoes/{id}` (logado)

Ordem das validações (a primeira falha vence): campanha existe (404) → R1 → `dataDoacao` não futura → doador existe (se informado) → por tipo: `ITEM` (lista não vazia; sem `necessidadeId` repetido; cada necessidade existe e é da campanha) / `DINHEIRO` (`valor` e `formaPagamento` presentes).

- [ ] **Step 1: Testes (falham)**

```java
// DoacaoServiceTest — Clock fixo em 2026-10-15; campanha 1 ATIVA com necessidades 10 (Arroz) e 11 (Feijão); campanha 2 com necessidade 20
@Test void registraDoacaoDeItensDoAutor()           { /* registradoPorId = usuário; 2 itens; valor e forma null */ }
@Test void registraDoacaoEmDinheiroSemItens()        { /* valor 50, PIX; itens enviados são descartados */ }
@Test void campanhaNaoAtivaLanca409()               { /* RASCUNHO e ENCERRADA → RegraNegocioException msg fixada */ }
@Test void dataFuturaFalhaNoCampoDataDoacao()       { /* 2026-10-16 → campo "dataDoacao" */ }
@Test void dataDeHojeEhPermitida()                  { }
@Test void itensVaziosFalhamNoCampoItens()          { /* "Adicione ao menos um item." */ }
@Test void itemDeOutraCampanhaFalha()               { /* necessidade 20 na campanha 1 → "Este item não pertence à campanha escolhida." */ }
@Test void itemRepetidoFalha()                      { /* 10 duas vezes → "O mesmo item foi informado mais de uma vez." */ }
@Test void dinheiroSemValorFalhaNoCampoValor()      { }
@Test void dinheiroSemFormaFalhaNoCampoFormaPagamento() { }
@Test void doadorInexistenteFalhaNoCampoDoadorId()  { }
@Test void podeAlterarVerdadeiroParaAutorEAdmin()   { /* buscar como autor → true; como outro voluntário → false; como ADMIN → true */ }

// DoacaoApiTest
@Test void voluntarioRegistraDoacaoAnonimaDeItens() { /* 201, $.doadorId null, $.itens[0].item "Arroz", $.registradoPorNome "Vitor" */ }
@Test void registraDoacaoEmDinheiroDeDoadorCadastrado() { /* 201, $.valor 50.0, $.formaPagamento "PIX", $.doadorNome "Maria" */ }
@Test void campanhaEncerradaRetorna409()            { }
@Test void itemDeOutraCampanhaRetorna400()          { /* $.erros[0].campo "itens" */ }
@Test void quantidadeZeroRetorna400()               { /* campo "itens[0].quantidade" */ }
@Test void doacaoGravaNoBancoComCheckRespeitado()   { /* jdbc: select valor, forma_pagamento from doacao → ambos null para ITEM */ }
```

- [ ] **Step 2: Rodar e ver falhar** — `./mvnw -B test -Dtest="DoacaoServiceTest,DoacaoApiTest"` → FAIL
- [ ] **Step 3: Implementar** — monte as respostas buscando títulos de campanha, nomes de doador e de usuário e os dados das necessidades em lote (um `findAllById` por tipo), não um por doação.
- [ ] **Step 4: Rodar e ver passar** — `./mvnw -B verify` → `BUILD SUCCESS`
- [ ] **Step 5: Commit** — `feat(doacao): registro de doações com as regras R1–R4`

---

### Task 3: Listar, editar e excluir doações (R8) e histórico do doador

**Files:**
- Create: `comum/AcessoNegadoException.java`, `doacao/DoacaoFiltros.java`
- Modify: `comum/GlobalExceptionHandler.java`, `doacao/DoacaoRepository.java` (`JpaSpecificationExecutor<Doacao>`), `DoacaoService.java`, `DoacaoController.java`, `doador/DoadorController.java`
- Test: `doacao/DoacaoServiceTest.java`, `doacao/DoacaoApiTest.java`, `doador/DoadorApiTest.java`

**Interfaces:**
- Consumes: Task 2
- Produces:
  - `AcessoNegadoException(String mensagem)` → `403` `ProblemDetail` com `detail`
  - `DoacaoFiltros`: `static Specification<Doacao> de(Long campanhaId, LocalDate de, LocalDate ate, TipoDoacao tipo, Long doadorId)` (cada filtro nulo é ignorado; `de`/`ate` inclusivos)
  - `DoacaoService`: `PaginaResponse<DoacaoResponse> listar(Long campanhaId, LocalDate de, LocalDate ate, TipoDoacao tipo, Long doadorId, int pagina, int tamanho, UsuarioAutenticado)` (ordem `dataDoacao` desc, `id` desc), `DoacaoResponse atualizar(Long id, DoacaoRequest, UsuarioAutenticado)`, `void excluir(Long id, UsuarioAutenticado)`
  - Regras de edição/exclusão: doação existe (404 "Doação não encontrada.") → R8 (`AcessoNegadoException` msg fixada) → campanha **atual** da doação ATIVA (409, msg fixada) → na edição, as mesmas validações do registro aplicadas à campanha do pedido. O autor (`registradoPorId`) nunca muda.
  - Endpoints: `GET /api/doacoes?campanhaId=&de=&ate=&tipo=&pagina=&tamanho=`, `PUT /api/doacoes/{id}`, `DELETE /api/doacoes/{id}` → 204 (logado, R8 no service); `GET /api/doadores/{id}/doacoes?pagina=` (logado; 404 se o doador não existe)

- [ ] **Step 1: Testes (falham)**

```java
// DoacaoServiceTest
@Test void voluntarioNaoAlteraDoacaoDeOutro()        { /* AcessoNegadoException msg fixada */ }
@Test void adminAlteraDoacaoDeQualquerUm()           { }
@Test void naoAlteraNemExcluiEmCampanhaEncerrada()   { /* 409 */ }
@Test void edicaoTrocaItensEMantemAutor()            { /* itens substituídos; registradoPorId igual */ }
@Test void edicaoDeItensParaDinheiroRemoveItens()    { }

// DoacaoApiTest
@Test void listaFiltraPorCampanhaPeriodoETipo()      { /* 3 doações em datas/tipos diferentes; cada filtro isolado e combinados */ }
@Test void listaVemDaMaisRecenteParaAMaisAntiga()    { }
@Test void voluntarioRecebe403AoEditarDoacaoDeOutro() { /* $.detail "Você só pode alterar doações que você registrou." */ }
@Test void autorEditaEExcluiAPropriaDoacao()         { /* PUT 200; DELETE 204; GET → 404 */ }
@Test void excluirDoacaoLiberaExclusaoDaNecessidade() { /* depois do DELETE da doação, DELETE /api/necessidades/{id} → 204 (R7 + cascade dos itens) */ }

// DoadorApiTest
@Test void historicoDoDoadorListaSuasDoacoes()       { /* 2 doações da Maria, 1 anônima → total 2 */ }
@Test void historicoContinuaAposAnonimizar()         { /* anonimiza → histórico ainda com 2, doadorNome "Anônimo" */ }
```

- [ ] **Step 2: Rodar e ver falhar** — `./mvnw -B test -Dtest="DoacaoServiceTest,DoacaoApiTest,DoadorApiTest"` → FAIL
- [ ] **Step 3: Implementar**
- [ ] **Step 4: Rodar e ver passar** — `./mvnw -B verify` → `BUILD SUCCESS`
- [ ] **Step 5: Commit** — `feat(doacao): listagem com filtros, edição e exclusão com a regra R8`

---

### Task 4: Base do frontend — API e formatação

**Files:**
- Create: `src/api/doadores.ts`, `src/api/doacoes.ts`
- Modify: `src/api/campanhas.ts` (`listarCampanhas` aceita `tamanho?`), `src/util/formatos.ts`
- Test: `src/api/doacoes.test.ts`, `src/util/formatos.test.ts`

**Interfaces:**
- Produces:
  - `doadores.ts`: `type Doador = { id: number; nome: string; telefone: string | null; email: string | null; observacao: string | null }`; `type DoadorDados = Omit<Doador, 'id'>`; `listarDoadores(busca: string, pagina: number)`, `buscarDoador(id)`, `criarDoador(dados)`, `atualizarDoador(id, dados)`, `anonimizarDoador(id)`, `listarDoacoesDoDoador(id, pagina)`
  - `doacoes.ts`: `type TipoDoacao = 'ITEM' | 'DINHEIRO'`; `type FormaPagamento = 'PIX' | 'ESPECIE' | 'OUTRO'`; `type ItemDoacao = { necessidadeId: number; item: string; unidade: Unidade; quantidade: number }`; `type Doacao = { id; campanhaId; campanhaTitulo; doadorId: number | null; doadorNome: string | null; tipo; valor: number | null; formaPagamento: FormaPagamento | null; dataDoacao: string; observacao: string | null; registradoPorNome: string; itens: ItemDoacao[]; podeAlterar: boolean }`; `type DoacaoDados = { campanhaId: number; doadorId: number | null; tipo: TipoDoacao; valor: number | null; formaPagamento: FormaPagamento | null; dataDoacao: string; observacao: string | null; itens: { necessidadeId: number; quantidade: number }[] }`; `type FiltroDoacoes = { campanhaId?: number; de?: string; ate?: string; tipo?: TipoDoacao; pagina: number }`; `listarDoacoes(filtro)`, `buscarDoacao(id)`, `registrarDoacao(dados)`, `atualizarDoacao(id, dados)`, `excluirDoacao(id)`
  - `formatos.ts`: `hojeISO(): string` (data **local** `AAAA-MM-DD`), `ROTULO_FORMA: Record<FormaPagamento, string>` (`PIX`, `Espécie`, `Outro`), `resumoDoacao(d: Doacao): string` (`'Arroz 5 kg, Feijão 2,5 kg'` ou `'R$ 50,00 (PIX)'`)

- [ ] **Step 1: Testes (falham)**

```ts
test('hojeISO usa a data local mesmo depois das 21h em Brasília', () => {
  vi.useFakeTimers(); vi.setSystemTime(new Date('2026-10-12T02:30:00Z'))  // 23:30 de 11/10 em Brasília (UTC-3)
  expect(hojeISO()).toBe('2026-10-11'); vi.useRealTimers()
})
test('resumoDoacao de itens', () => { /* 'Arroz 5 kg, Feijão 2,5 kg' */ })
test('resumoDoacao em dinheiro', () => { /* 'R$ 50,00 (PIX)' (normalize espaço não separável) */ })
test('listarDoacoes envia só os filtros preenchidos', async () => { /* { campanhaId: 1, tipo: 'ITEM', pagina: 0 } → '/api/doacoes?campanhaId=1&tipo=ITEM&pagina=0' */ })
test('listarDoadores codifica a busca', async () => { /* 'José (47)' → '/api/doadores?busca=Jos%C3%A9+%2847%29&pagina=0' */ })
```

Defina `TZ=America/Sao_Paulo` para os testes em `vite.config.ts` (`test.env`) para que fusos de máquina/CI não mudem o resultado.

- [ ] **Step 2–4:** ver falhar → implementar → `npm run lint && npm run typecheck && npm test` verde
- [ ] **Step 5: Commit** — `feat(frontend): API de doadores e doações, data local e resumo de doação`

---

### Task 5: Telas de Doadores

**Files:**
- Create: `src/pages/DoadoresPage.tsx`, `src/pages/DoadorFormModal.tsx`, `src/pages/DoadorDetalhePage.tsx`
- Modify: `src/App.tsx` (rotas `doadores` e `doadores/:id`)
- Test: `src/pages/DoadoresPage.test.tsx`, `src/pages/DoadorDetalhePage.test.tsx`

**Interfaces:**
- Consumes: `api/doadores.ts`, `formatos.ts`, `aplicarErrosDeCampo`
- Produces:
  - `DoadorFormModal`: props `{ aberto: boolean; doador: Doador | null; aoFechar(): void; aoSalvar?(d: Doador): void }`; campos Nome, Telefone, E-mail, Observação; `closeOnClickOutside={false}`; reutilizado pela Task 6
  - `DoadoresPage`: título "Doadores", campo "Buscar por nome ou telefone" (busca ao digitar, com atraso de 300 ms via `useDebouncedValue`), botão "Novo doador", lista em cartões (nome, telefone, e-mail) com link para `/doadores/{id}`, `Nenhum doador encontrado.` quando vazio
  - `DoadorDetalhePage`: dados do doador, botão "Editar" (abre o modal), histórico (data, campanha, `resumoDoacao`) ou `Nenhuma doação registrada.`; para ADMIN, botão "Anonimizar" com confirmação `Apagar os dados pessoais de {nome}? As doações continuam registradas como anônimas.`

- [ ] **Step 1: Testes (falham)**

```tsx
test('busca chama a API com o termo digitado', async () => { /* digita "maria" → listarDoadores('maria', 0) */ })
test('cadastra doador e recarrega a lista', async () => { })
test('lista vazia mostra mensagem', async () => { })
test('detalhe mostra o histórico do doador', async () => { /* "Arroz 5 kg" e título da campanha */ })
test('admin anonimiza após confirmar', async () => { /* anonimizarDoador(3) só após confirmar */ })
test('voluntário não vê "Anonimizar"', async () => { })
```

- [ ] **Step 2–4:** ver falhar → implementar → lint, typecheck, test verdes
- [ ] **Step 5: Commit** — `feat(frontend): telas de doadores`

---

### Task 6: Formulário de doação (registrar e editar)

**Files:**
- Create: `src/components/SeletorDoador.tsx`, `src/pages/DoacaoFormPage.tsx`
- Modify: `src/App.tsx` (rotas `doacoes/nova` e `doacoes/:id/editar`)
- Test: `src/pages/DoacaoFormPage.test.tsx`

**Interfaces:**
- Consumes: `api/doacoes.ts`, `api/campanhas.ts` (`listarCampanhas({ status: 'ATIVA', pagina: 0, tamanho: 100 })`, `listarNecessidades`), `api/doadores.ts`, `DoadorFormModal`, `hojeISO`, `ROTULO_FORMA`, `aplicarErrosDeCampo`
- Produces:
  - `SeletorDoador`: props `{ value: number | null; onChange(id: number | null): void }`; `Select` pesquisável (busca no servidor com atraso de 300 ms) com a opção fixa "Anônimo" (valor nulo) e botão "Cadastrar novo doador" que abre `DoadorFormModal` e seleciona o doador criado
  - `DoacaoFormPage` (modo registrar em `/doacoes/nova`, aceita `?campanha={id}` para pré-selecionar; modo editar em `/doacoes/:id/editar`, carrega com `buscarDoacao`):
    - Campanha (`Select`, só ATIVAS), Tipo (`SegmentedControl` "Itens" / "Dinheiro"), Doador (`SeletorDoador`, padrão Anônimo), Data (`TextInput type="date"`, padrão `hojeISO()`), Observação
    - Itens: linhas com Item (`Select` das necessidades da campanha) + Quantidade (`NumberInput`, vírgula decimal) + botão "Remover"; botão "+ Adicionar item"; começa com uma linha vazia
    - Dinheiro: Valor (`NumberInput` com prefixo `R$ `, vírgula decimal) + Forma de pagamento (`Select` com `ROTULO_FORMA`)
    - Trocar a campanha limpa as linhas de itens (volta a uma linha vazia)
    - Validação local espelhando as mensagens fixadas; erros de campo da API aplicados com `aplicarErrosDeCampo`; 409/403 em `Alert`
    - Botão "Registrar" (ou "Salvar alterações" na edição) desabilitado enquanto envia
    - Após registrar: `Alert` verde `Doação registrada.`, formulário limpo para a próxima doação **mantendo campanha, tipo e data**; após editar: navega para `/doacoes`

- [ ] **Step 1: Testes (falham)** — `api/doacoes`, `api/campanhas`, `api/doadores` mockados; campanha 1 ATIVA com necessidades Arroz (10, KG) e Feijão (11, KG)

```tsx
test('data vem preenchida com hoje (local)', async () => { /* valor do campo Data === hojeISO() (a regra do fuso é testada na Task 4) */ })
test('registra doação anônima de dois itens', async () => {
  // escolhe campanha, Arroz 5, "+ Adicionar item", Feijão "2,5" → registrarDoacao({ campanhaId: 1, doadorId: null, tipo: 'ITEM', valor: null, formaPagamento: null, dataDoacao: hoje, observacao: null, itens: [{ necessidadeId: 10, quantidade: 5 }, { necessidadeId: 11, quantidade: 2.5 }] })
})
test('registra doação em dinheiro', async () => { /* "50,00", PIX → valor 50, formaPagamento 'PIX', itens [] */ })
test('após registrar mostra sucesso e limpa itens mantendo a campanha', async () => { })
test('trocar a campanha limpa os itens escolhidos', async () => { })
test('toque duplo em Registrar cria uma única doação', async () => { /* registrarDoacao com promessa pendente; dois cliques → chamado 1 vez */ })
test('sem itens válidos mostra "Adicione ao menos um item." e não chama a API', async () => { })
test('409 de campanha não ativa aparece em alerta', async () => { })
test('pré-seleciona a campanha vinda de ?campanha=1', async () => { })
test('editar carrega a doação e salva com atualizarDoacao', async () => { })
test('cadastrar novo doador no seletor já o seleciona', async () => { /* criarDoador → doadorId do novo no envio */ })
```

- [ ] **Step 2–4:** ver falhar → implementar → lint, typecheck, test verdes
- [ ] **Step 5: Commit** — `feat(frontend): formulário de registro e edição de doações`

---

### Task 7: Lista de doações, atalho na campanha e confirmação de status

**Files:**
- Create: `src/pages/DoacoesPage.tsx`
- Modify: `src/App.tsx` (rota `doacoes`), `src/pages/CampanhaDetalhePage.tsx`
- Test: `src/pages/DoacoesPage.test.tsx`, `src/pages/CampanhaDetalhePage.test.tsx`

**Interfaces:**
- Consumes: `api/doacoes.ts`, `api/campanhas.ts`, `formatos.ts`
- Produces:
  - `DoacoesPage`: título "Doações", botão "Registrar doação" (→ `/doacoes/nova`), filtros Campanha (`Select` com "Todas"), De / Até (`TextInput type="date"`), Tipo (`Select`: Todos / Itens / Dinheiro); cartões com data, campanha, doador (`doadorNome ?? 'Anônimo'`), `resumoDoacao`, "Registrado por {nome}"; menu "⋮" (`aria-label="Ações da doação de {data}"`) com Editar (→ `/doacoes/{id}/editar`) e Excluir (confirmação `Excluir esta doação?`) só quando `podeAlterar`; `Nenhuma doação encontrada.`; paginação; query key `['doacoes', filtro]`
  - `CampanhaDetalhePage`: botão "Registrar doação" (→ `/doacoes/nova?campanha={id}`) visível para todos quando a campanha está ATIVA; "Encerrar" e "Voltar para rascunho" pedem confirmação (`Encerrar a campanha? Novas doações ficarão bloqueadas.` / `Voltar a campanha para rascunho? Novas doações ficarão bloqueadas.`); "Ativar" não pede

- [ ] **Step 1: Testes (falham)**

```tsx
// DoacoesPage
test('lista doações com resumo, doador e autor', async () => { /* "Anônimo", "Arroz 5 kg", "Registrado por Vitor" */ })
test('filtrar por tipo Dinheiro recarrega com tipo', async () => { /* listarDoacoes({ tipo: 'DINHEIRO', pagina: 0 }) */ })
test('sem podeAlterar não há menu de ações', async () => { })
test('excluir pede confirmação e chama a API', async () => { })
test('403 ao excluir mostra a mensagem da API', async () => { /* "Você só pode alterar doações que você registrou." */ })

// CampanhaDetalhePage
test('campanha ativa mostra "Registrar doação" com a campanha pré-selecionada', async () => { /* href '/doacoes/nova?campanha=1' */ })
test('encerrar pede confirmação antes de chamar a API', async () => { })
```

Os testes existentes "admin encerra a campanha" passam a confirmar no modal antes de verificar a chamada.

- [ ] **Step 2–4:** ver falhar → implementar → `npm run lint && npm run typecheck && npm test && npm run build` verde
- [ ] **Step 5: Commit** — `feat(frontend): lista de doações, atalho na campanha e confirmação de status`

---

### Task 8: Verificação ponta a ponta

- [ ] **Step 1:** Subir `db`, backend (`dev`) e frontend.
- [ ] **Step 2:** Como voluntário (reativar o voluntário de teste do Plano 2 pela tela de Usuários): pela campanha ativa, "Registrar doação" → 2 itens anônimos (um com vírgula) → sucesso e formulário pronto para a próxima; registrar doação em dinheiro de um doador novo cadastrado no seletor.
- [ ] **Step 3:** Em Doações: filtros por tipo e período; editar a própria doação; tentar alterar doação do ADMIN → sem menu.
- [ ] **Step 4:** Como ADMIN: encerrar a campanha (com confirmação) → voluntário tenta registrar → alerta de campanha não ativa; reativar.
- [ ] **Step 5:** Doadores: buscar por telefone com outra formatação; histórico; ADMIN anonimiza → histórico preservado como "Anônimo".
- [ ] **Step 6:** 375 px: formulário de doação utilizável sem rolagem horizontal; parar servidores; registrar no ledger.
