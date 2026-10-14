# Plano 2 — Usuários, Campanhas e Necessidades — Implementation Plan

**Goal:** ADMIN gerencia usuários, campanhas e necessidades; todos os usuários logados consultam campanhas e suas necessidades — API REST + telas React, com sessão revalidada a cada requisição.

**Architecture:** Pacotes `usuario/` e `campanha/` no backend seguindo o padrão do Plano 1 (controller fino → service com regras → repositório JPA; DTOs `record`; erros via `GlobalExceptionHandler`). Autorização ADMIN com `@PreAuthorize`. No frontend, funções de API em `src/api/`, páginas com TanStack Query e formulários Mantine em modais.

**Tech Stack:** igual ao Plano 1 (Java 25, Spring Boot 4.1.1, Spring Security 7.1, PostgreSQL 16, Testcontainers; React 19, TypeScript, Mantine 9, React Router 8, TanStack Query 5, Vitest 5).

**Spec:** `docs/planejamento/especificacao/2026-10-08-gestao-doacoes-design.md` (§3 tabelas e R7/R10, §4 itens 3 e 7, §5 endpoints, §6 autorização)

**Base:** branch `feature/plano-2` criada a partir de `feature/plano-1-fundacao` (o Plano 1 ainda não foi integrado à `main`).

## Global Constraints

- Tudo do Plano 1 continua valendo (pacote `br.org.iap.doacoes`, `ProblemDetail`, DTOs `record`, textos em pt-BR, Checkstyle, JaCoCo ≥ 70% nos `*Service`, oxlint, TS estrito).
- Escrita em usuários, campanhas e necessidades: somente `ADMIN` (`@PreAuthorize("hasRole('ADMIN')")`); leitura de campanhas e necessidades: qualquer usuário logado; leitura de usuários: somente `ADMIN`.
- Listagens paginadas devolvem `PaginaResponse<T>(List<T> itens, int pagina, int tamanho, long total, int totalPaginas)`; parâmetros `?pagina=` (0-based, padrão 0) e `?tamanho=` (padrão 20, máximo 100).
- Usuários nunca são excluídos, só desativados (R10). Necessidade com doações não pode ser excluída (R7) → `409`.
- Unidades: `UNIDADE`, `KG`, `LITRO`, `PACOTE`, `CAIXA`, `PAR`. Status de campanha: `RASCUNHO`, `ATIVA`, `ENCERRADA` (qualquer transição é permitida neste plano).
- Valores e quantidades: `BigDecimal`, `@Positive`, `@Digits(integer = 10, fraction = 2)`.
- Datas trafegam como `"AAAA-MM-DD"` (`LocalDate`); exibidas como `DD/MM/AAAA`; moeda exibida como `R$ 1.234,50`.
- Senhas (criação, redefinição, troca): mínimo 8 caracteres e máximo 72 bytes UTF-8.
- Mensagens fixadas por este plano (usar exatamente):
  - `Já existe um usuário com este e-mail.` (campo `email`, 400)
  - `Você não pode desativar a própria conta nem alterar o próprio papel.` (409)
  - `A data de término deve ser igual ou posterior à de início.` (campo `dataFim`, 400)
  - `Esta necessidade já recebeu doações e não pode ser excluída.` (409)
  - `Usuário não encontrado.` / `Campanha não encontrada.` / `Necessidade não encontrada.` (404)

## Review Focus

1. **ADMIN desativando a si mesmo ou tirando o próprio papel de ADMIN** — ficaria sem acesso (e, se for o único ADMIN, trancaria o sistema); esperado `409` com a mensagem fixada → teste na Task 1.
2. **Usuário desativado (ou rebaixado) que continua com a sessão aberta** — esperado: na próxima requisição recebe `401` (e passa a valer o papel novo) → teste na Task 2.
3. **E-mail duplicado com caixa diferente** (`Maria@Igreja.org` já existe como `maria@igreja.org`) — esperado `400` no campo `email`, nunca `500` por violação de índice → teste na Task 1.
4. **Data exibida um dia antes** — `new Date('2026-10-11')` em fuso de Brasília mostra 10/10; esperado `11/10/2026` → teste na Task 5.
5. **Quantidade digitada com vírgula** (`2,5` kg no celular) — esperado: enviada como `2.5` → teste na Task 8.

---

## Estrutura de arquivos

```
backend/src/main/java/br/org/iap/doacoes/
  comum/PaginaResponse.java                     (novo)
  auth/PoliticaSenha.java                       (novo; ContaService passa a usar)
  auth/SessaoAtualizadaFilter.java              (novo)
  usuario/UsuarioService.java, UsuarioController.java,
          CriarUsuarioRequest.java, AtualizarUsuarioRequest.java,
          RedefinirSenhaRequest.java, UsuarioResponse.java
  campanha/StatusCampanha.java, Campanha.java, CampanhaRepository.java,
           CampanhaRequest.java, AlterarStatusRequest.java, CampanhaResponse.java,
           CampanhaService.java, CampanhaController.java,
           Unidade.java, Necessidade.java, NecessidadeRepository.java,
           NecessidadeRequest.java, NecessidadeResponse.java,
           NecessidadeService.java, NecessidadeController.java
frontend/src/
  api/tipos.ts, api/usuarios.ts, api/campanhas.ts
  util/formatos.ts, util/erros.ts
  pages/UsuariosPage.tsx (+ UsuarioFormModal.tsx, RedefinirSenhaModal.tsx)
  pages/CampanhasPage.tsx, pages/CampanhaFormModal.tsx
  pages/CampanhaDetalhePage.tsx, pages/NecessidadeFormModal.tsx
```

---

### Task 1: API de Usuários (ADMIN)

**Files:**
- Create: `comum/PaginaResponse.java`, `auth/PoliticaSenha.java`, `usuario/UsuarioService.java`, `usuario/UsuarioController.java`, `usuario/CriarUsuarioRequest.java`, `usuario/AtualizarUsuarioRequest.java`, `usuario/RedefinirSenhaRequest.java`, `usuario/UsuarioResponse.java`
- Modify: `auth/ContaService.java` (usar `PoliticaSenha`), `usuario/UsuarioRepository.java`
- Test: `usuario/UsuarioServiceTest.java` (Mockito), `usuario/UsuarioApiTest.java` (`@SpringBootTest` + MockMvc + Testcontainers)

**Interfaces:**
- Consumes: `Usuario`, `Papel`, `UsuarioRepository`, `CampoInvalidoException`, `RegraNegocioException`, `NaoEncontradoException`, `UsuarioAutenticado.getId()` (Plano 1)
- Produces:
  - `record PaginaResponse<T>(List<T> itens, int pagina, int tamanho, long total, int totalPaginas)` com `static <T> PaginaResponse<T> de(Page<T> page)` e `static Pageable pageable(int pagina, int tamanho, Sort sort)` (limita `tamanho` a 1..100)
  - `final class PoliticaSenha` com `static void validar(String campo, String senha)` → `CampoInvalidoException(campo, "A senha deve ter ao menos 8 caracteres.")` ou `CampoInvalidoException(campo, "A senha é longa demais. Use uma senha mais curta.")` (> 72 bytes UTF-8)
  - `UsuarioRepository.existsByEmailAndIdNot(String email, Long id)`, `existsByEmail(String email)`
  - `record UsuarioResponse(Long id, String nome, String email, Papel papel, boolean ativo, OffsetDateTime criadoEm)` com `static de(Usuario)`
  - `record CriarUsuarioRequest(@NotBlank @Size(max=120) String nome, @NotBlank @Email @Size(max=160) String email, @NotNull Papel papel, @NotBlank String senha)` (trim do e-mail no construtor, como `LoginRequest`)
  - `record AtualizarUsuarioRequest(@NotBlank @Size(max=120) String nome, @NotBlank @Email @Size(max=160) String email, @NotNull Papel papel, boolean ativo)`
  - `record RedefinirSenhaRequest(@NotBlank String novaSenha)`
  - `UsuarioService`: `PaginaResponse<UsuarioResponse> listar(int pagina, int tamanho)` (ordem `nome` asc), `UsuarioResponse criar(CriarUsuarioRequest)`, `UsuarioResponse atualizar(Long id, AtualizarUsuarioRequest, Long idAdminLogado)`, `void redefinirSenha(Long id, String novaSenha)`
  - Endpoints (classe toda `@PreAuthorize("hasRole('ADMIN')")`): `GET /api/usuarios?pagina=&tamanho=` → 200; `POST /api/usuarios` → 201; `PUT /api/usuarios/{id}` → 200; `POST /api/usuarios/{id}/redefinir-senha` → 204

- [ ] **Step 1: Testes (falham)**

`UsuarioServiceTest` (repositório mockado, `BCryptPasswordEncoder` real):
```java
@Test void criarNormalizaEmailESalvaHash()           { /* " Joao@Igreja.org " → email "joao@igreja.org"; encoder.matches(senha, hash) */ }
@Test void criarComEmailExistenteFalhaNoCampoEmail() { /* existsByEmail("maria@igreja.org") = true; criar com "Maria@Igreja.org" → CampoInvalidoException campo "email", msg "Já existe um usuário com este e-mail." */ }
@Test void criarComSenhaCurtaFalhaNoCampoSenha()     { /* "1234567" → CampoInvalidoException campo "senha" */ }
@Test void atualizarComEmailDeOutroUsuarioFalha()    { /* existsByEmailAndIdNot = true → campo "email" */ }
@Test void adminNaoPodeDesativarASiMesmo()           { /* atualizar(5, ativo=false, idAdminLogado=5) → RegraNegocioException msg fixada */ }
@Test void adminNaoPodeTirarOProprioPapel()          { /* atualizar(5, papel=VOLUNTARIO, idAdminLogado=5) → RegraNegocioException */ }
@Test void adminPodeEditarOProprioNome()             { /* atualizar(5, nome novo, papel ADMIN, ativo true, 5) → ok */ }
@Test void atualizarInexistenteLanca404()            { /* NaoEncontradoException "Usuário não encontrado." */ }
@Test void redefinirSenhaGravaNovoHash()             { }
@Test void redefinirSenhaLongaDemaisFalhaNoCampoNovaSenha() { /* "á".repeat(40) → campo "novaSenha" */ }
```

`UsuarioApiTest` (fixture: ADMIN `ana@igreja.org` e VOLUNTARIO `vitor@igreja.org`; reutilize o helper de CSRF real e de login do `AuthApiTest` — extraia-os para `src/test/java/br/org/iap/doacoes/ApiTestSupport.java` com `RequestPostProcessor csrf()` e `MockHttpSession login(String email, String senha)`):
```java
@Test void voluntarioNaoListaUsuarios()          { /* GET /api/usuarios → 403 */ }
@Test void voluntarioNaoCriaUsuario()            { /* POST → 403 */ }
@Test void adminListaUsuariosPaginadoPorNome()   { /* 200, $.itens[0].nome == "Ana", $.total == 2, $.pagina == 0, sem campo senhaHash */ }
@Test void adminCriaUsuarioQuePodeEntrar()       { /* POST → 201 $.email; depois login com o novo → 200 */ }
@Test void emailDuplicadoComCaixaDiferenteRetorna400() { /* "Vitor@Igreja.ORG" → 400, $.erros[0].campo == "email" */ }
@Test void adminDesativandoASiMesmoRetorna409()  { /* PUT /api/usuarios/{idAna} ativo=false → 409, $.detail msg fixada */ }
@Test void usuarioDesativadoNaoConsegueEntrar()  { /* PUT vitor ativo=false → 200; login vitor → 401 */ }
@Test void redefinirSenhaPermiteLoginComANova()  { /* 204; login com a nova → 200 */ }
@Test void tamanhoAcimaDe100ELimitado()          { /* ?tamanho=500 → $.tamanho == 100 */ }
```

- [ ] **Step 2: Rodar e ver falhar**

Run (em `backend/`): `./mvnw -B test -Dtest="UsuarioServiceTest,UsuarioApiTest"`
Expected: FAIL (classes inexistentes)

- [ ] **Step 3: Implementar** — `PoliticaSenha` substitui a checagem de bytes do `ContaService` (que passa a chamar `PoliticaSenha.validar("novaSenha", novaSenha)`); o `AuthApiTest` existente deve continuar verde. `UsuarioController` lê o id do ADMIN logado via `@AuthenticationPrincipal UsuarioAutenticado`.

- [ ] **Step 4: Rodar e ver passar**

Run: `./mvnw -B verify`
Expected: `BUILD SUCCESS` (todos os testes anteriores + 19 novos)

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(usuario): API de usuários para ADMIN"
```

---

### Task 2: Sessão revalidada a cada requisição

**Files:**
- Create: `auth/SessaoAtualizadaFilter.java`
- Modify: `config/SecurityConfig.java` (registrar o filtro depois da autenticação da sessão), `auth/UsuarioAutenticado.java` se necessário
- Test: `auth/SessaoAtualizadaTest.java` (`@SpringBootTest` + MockMvc, usa `ApiTestSupport`)

**Interfaces:**
- Consumes: `UsuarioRepository.findById`, `UsuarioAutenticado`, `SecurityContextRepository` (Plano 1)
- Produces: `SessaoAtualizadaFilter extends OncePerRequestFilter` — só atua em `/api/**` com usuário autenticado. Recarrega o `Usuario` pelo id: se inexistente ou `!ativo` → invalida a sessão, limpa o contexto e responde `401`; se `papel` ou `nome` mudaram → substitui a autenticação por uma nova com `UsuarioAutenticado` atualizado e salva no `SecurityContextRepository`.

- [ ] **Step 1: Testes (falham)**

```java
@Test void usuarioDesativadoPerdeASessaoNaProximaRequisicao() {
  // vitor loga (sessão S); ADMIN desativa vitor; GET /api/auth/me com S → 401; GET /api/campanhas com S → 401
}
@Test void usuarioPromovidoAAdminGanhaAcessoSemNovoLogin() {
  // vitor loga (S); GET /api/usuarios com S → 403; ADMIN muda papel para ADMIN; GET /api/usuarios com S → 200
}
@Test void adminRebaixadoPerdeAcessoImediatamente() {
  // segundo ADMIN "bia" loga (S); ana rebaixa bia; GET /api/usuarios com S → 403
}
```

> `GET /api/campanhas` só existe a partir da Task 3; neste teste use `GET /api/usuarios` para o caso do ADMIN e `GET /api/auth/me` para o voluntário. Na Task 3, acrescente a asserção com `/api/campanhas`.

- [ ] **Step 2: Rodar e ver falhar**

Run: `./mvnw -B test -Dtest=SessaoAtualizadaTest`
Expected: FAIL em `usuarioPromovido...` (recebe 403) e `adminRebaixado...` (recebe 200). `usuarioDesativado...` já passa, porque o `/me` do Plano 1 relê o usuário do banco; a asserção com `/api/campanhas` acrescentada na Task 3 é a que comprova o filtro para usuários desativados (deve falhar se o filtro for removido).

- [ ] **Step 3: Implementar `SessaoAtualizadaFilter`** e registrar com `http.addFilterAfter(filtro, SecurityContextHolderFilter.class)`. Não declarar o filtro como `@Component` (evita registro duplicado no servlet); crie-o como `@Bean` em `SecurityConfig` e desative o registro automático com `FilterRegistrationBean#setEnabled(false)`, ou instancie direto no `SecurityConfig`.

- [ ] **Step 4: Rodar e ver passar**

Run: `./mvnw -B verify`
Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(auth): sessão reflete desativação e mudança de papel imediatamente"
```

---

### Task 3: API de Campanhas

**Files:**
- Create: `campanha/StatusCampanha.java`, `Campanha.java`, `CampanhaRepository.java`, `CampanhaRequest.java`, `AlterarStatusRequest.java`, `CampanhaResponse.java`, `CampanhaService.java`, `CampanhaController.java`
- Modify: `auth/SessaoAtualizadaTest.java` (asserção extra com `/api/campanhas`)
- Test: `campanha/CampanhaServiceTest.java`, `campanha/CampanhaApiTest.java`

**Interfaces:**
- Consumes: `PaginaResponse`, `UsuarioAutenticado`, exceções de `comum/`
- Produces:
  - `enum StatusCampanha { RASCUNHO, ATIVA, ENCERRADA }`
  - Entidade `Campanha` (tabela `campanha`; `criadoPorId` como `Long` na coluna `criado_por`; `atualizadoEm` atualizado em `@PreUpdate`); construtor `Campanha(String titulo, String descricao, LocalDate dataInicio, LocalDate dataFim, BigDecimal metaValor, Long criadoPorId)` com status inicial `RASCUNHO`
  - `CampanhaRepository`: `Page<Campanha> findByStatus(StatusCampanha, Pageable)`
  - `record CampanhaRequest(@NotBlank @Size(max=150) String titulo, @Size(max=2000) String descricao, @NotNull LocalDate dataInicio, LocalDate dataFim, @Positive @Digits(integer=10, fraction=2) BigDecimal metaValor)`
  - `record AlterarStatusRequest(@NotNull StatusCampanha status)`
  - `record CampanhaResponse(Long id, String titulo, String descricao, LocalDate dataInicio, LocalDate dataFim, StatusCampanha status, BigDecimal metaValor, OffsetDateTime criadoEm, OffsetDateTime atualizadoEm)`
  - `CampanhaService`: `listar(StatusCampanha statusOuNull, int pagina, int tamanho)` (ordem `dataInicio` desc, depois `id` desc), `buscar(Long id)`, `criar(CampanhaRequest, Long usuarioId)`, `atualizar(Long id, CampanhaRequest)`, `alterarStatus(Long id, StatusCampanha)`, e `Campanha obter(Long id)` (entidade ou 404; usado pela Task 4)
  - Endpoints: `GET /api/campanhas?status=&pagina=&tamanho=`, `GET /api/campanhas/{id}` (qualquer logado); `POST /api/campanhas` → 201, `PUT /api/campanhas/{id}`, `PATCH /api/campanhas/{id}/status` (ADMIN)

- [ ] **Step 1: Testes (falham)**

```java
// CampanhaServiceTest
@Test void criarComecaComoRascunhoERegistraAutor() { }
@Test void dataFimAntesDoInicioFalhaNoCampoDataFim() { /* início 2026-10-20, fim 2026-10-19 → CampoInvalidoException "dataFim", msg fixada */ }
@Test void dataFimIgualAoInicioEhPermitida() { }
@Test void atualizarInexistenteLanca404() { /* "Campanha não encontrada." */ }
@Test void alterarStatusMudaStatus() { /* RASCUNHO → ATIVA → ENCERRADA → ATIVA */ }

// CampanhaApiTest (ADMIN ana, VOLUNTARIO vitor)
@Test void voluntarioListaEConsultaCampanhas() { /* GET lista e GET /{id} → 200 */ }
@Test void voluntarioNaoCriaNemEditaNemMudaStatus() { /* POST, PUT, PATCH → 403 */ }
@Test void adminCriaCampanha() { /* 201, $.status == "RASCUNHO", $.metaValor == 500.00, $.dataInicio == "2026-10-20" */ }
@Test void filtraPorStatus() { /* uma ATIVA e uma RASCUNHO; ?status=ATIVA → total 1 */ }
@Test void tituloEmBrancoRetorna400() { /* $.erros[0].campo == "titulo" */ }
@Test void metaNegativaRetorna400() { /* campo "metaValor" */ }
@Test void dataFimAntesDoInicioRetorna400() { /* campo "dataFim" */ }
@Test void campanhaInexistenteRetorna404() { }
@Test void statusInvalidoRetorna400() { /* {"status":"XYZ"} → 400 (corpo ilegível) */ }
```

Para `statusInvalidoRetorna400`: trate `HttpMessageNotReadableException` no `GlobalExceptionHandler` → `400` com `detail` `Requisição inválida.`

- [ ] **Step 2: Rodar e ver falhar**

Run: `./mvnw -B test -Dtest="CampanhaServiceTest,CampanhaApiTest"`
Expected: FAIL

- [ ] **Step 3: Implementar**

- [ ] **Step 4: Rodar e ver passar**

Run: `./mvnw -B verify`
Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(campanha): API de campanhas"
```

---

### Task 4: API de Necessidades

**Files:**
- Create: `campanha/Unidade.java`, `Necessidade.java`, `NecessidadeRepository.java`, `NecessidadeRequest.java`, `NecessidadeResponse.java`, `NecessidadeService.java`, `NecessidadeController.java`
- Test: `campanha/NecessidadeServiceTest.java`, `campanha/NecessidadeApiTest.java`

**Interfaces:**
- Consumes: `CampanhaService.obter(Long id)` (Task 3)
- Produces:
  - `enum Unidade { UNIDADE, KG, LITRO, PACOTE, CAIXA, PAR }`
  - Entidade `Necessidade` (tabela `necessidade`; `campanhaId` como `Long` na coluna `campanha_id`)
  - `NecessidadeRepository`: `List<Necessidade> findByCampanhaIdOrderByItemAsc(Long campanhaId)`; `@Query(value = "select exists(select 1 from item_doacao where necessidade_id = :id)", nativeQuery = true) boolean possuiDoacoes(@Param("id") Long id)`
  - `record NecessidadeRequest(@NotBlank @Size(max=120) String item, @NotNull Unidade unidade, @NotNull @Positive @Digits(integer=10, fraction=2) BigDecimal quantidadeMeta, @Size(max=1000) String observacao)`
  - `record NecessidadeResponse(Long id, Long campanhaId, String item, Unidade unidade, BigDecimal quantidadeMeta, String observacao)`
  - `NecessidadeService`: `listar(Long campanhaId)` (404 se a campanha não existe), `criar(Long campanhaId, NecessidadeRequest)`, `atualizar(Long id, NecessidadeRequest)`, `excluir(Long id)` (R7 → `RegraNegocioException` msg fixada)
  - Endpoints: `GET /api/campanhas/{id}/necessidades` (logado); `POST /api/campanhas/{id}/necessidades` → 201, `PUT /api/necessidades/{id}`, `DELETE /api/necessidades/{id}` → 204 (ADMIN)

- [ ] **Step 1: Testes (falham)**

```java
// NecessidadeServiceTest
@Test void criarEmCampanhaInexistenteLanca404() { }
@Test void excluirComDoacoesLanca409() { /* possuiDoacoes = true → RegraNegocioException msg fixada; delete nunca chamado */ }
@Test void excluirSemDoacoesRemove() { }

// NecessidadeApiTest
@Test void adminCriaEListaNecessidadesOrdenadasPorItem() { /* cria "Feijão" e "Arroz" → GET → $[0].item == "Arroz", $[0].quantidadeMeta == 50.00, $[0].unidade == "KG" */ }
@Test void voluntarioListaMasNaoAltera() { /* GET 200; POST/PUT/DELETE → 403 */ }
@Test void quantidadeZeroRetorna400() { /* campo "quantidadeMeta" */ }
@Test void unidadeInvalidaRetorna400() { }
@Test void excluirNecessidadeComDoacaoRetorna409() {
  // insere doacao + item_doacao via JdbcTemplate para a necessidade → DELETE → 409, $.detail msg fixada
}
@Test void excluirNecessidadeSemDoacaoRetorna204() { /* depois GET não a contém */ }
@Test void necessidadesDeCampanhaInexistenteRetorna404() { }
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./mvnw -B test -Dtest="NecessidadeServiceTest,NecessidadeApiTest"`
Expected: FAIL

- [ ] **Step 3: Implementar**

- [ ] **Step 4: Rodar e ver passar**

Run: `./mvnw -B verify`
Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(campanha): API de necessidades com proteção da regra R7"
```

---

### Task 5: Base do frontend — tipos, API e formatação

**Files:**
- Create: `src/api/tipos.ts`, `src/api/usuarios.ts`, `src/api/campanhas.ts`, `src/util/formatos.ts`, `src/util/erros.ts`
- Modify: `src/pages/MinhaContaPage.tsx` (usar `aplicarErrosDeCampo`)
- Test: `src/util/formatos.test.ts`, `src/util/erros.test.ts`, `src/api/campanhas.test.ts`

**Interfaces:**
- Consumes: `api()`, `ApiError` (Plano 1)
- Produces:
  - `tipos.ts`: `type Pagina<T> = { itens: T[]; pagina: number; tamanho: number; total: number; totalPaginas: number }`
  - `usuarios.ts`: `type Usuario = { id: number; nome: string; email: string; papel: Papel; ativo: boolean; criadoEm: string }`; `listarUsuarios(pagina: number): Promise<Pagina<Usuario>>`; `criarUsuario(dados: { nome: string; email: string; papel: Papel; senha: string }): Promise<Usuario>`; `atualizarUsuario(id: number, dados: { nome: string; email: string; papel: Papel; ativo: boolean }): Promise<Usuario>`; `redefinirSenhaUsuario(id: number, novaSenha: string): Promise<void>`
  - `campanhas.ts`: `type StatusCampanha = 'RASCUNHO' | 'ATIVA' | 'ENCERRADA'`; `type Unidade = 'UNIDADE' | 'KG' | 'LITRO' | 'PACOTE' | 'CAIXA' | 'PAR'`; `type Campanha = { id: number; titulo: string; descricao: string | null; dataInicio: string; dataFim: string | null; status: StatusCampanha; metaValor: number | null }`; `type CampanhaDados = { titulo: string; descricao: string | null; dataInicio: string; dataFim: string | null; metaValor: number | null }`; `type Necessidade = { id: number; campanhaId: number; item: string; unidade: Unidade; quantidadeMeta: number; observacao: string | null }`; `type NecessidadeDados = Omit<Necessidade, 'id' | 'campanhaId'>`; funções `listarCampanhas(filtro: { status?: StatusCampanha; pagina: number })`, `buscarCampanha(id)`, `criarCampanha(dados)`, `atualizarCampanha(id, dados)`, `alterarStatusCampanha(id, status)`, `listarNecessidades(campanhaId)`, `criarNecessidade(campanhaId, dados)`, `atualizarNecessidade(id, dados)`, `excluirNecessidade(id)`
  - `formatos.ts`: `formatarData(iso: string | null): string` (`'2026-10-11'` → `'11/10/2026'`, `null` → `'—'`; sem `new Date`), `formatarMoeda(valor: number | null): string` (`1234.5` → `'R$ 1.234,50'` via `Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })`, `null` → `'—'`), `formatarQuantidade(qtd: number, unidade: Unidade): string` (`2.5, 'KG'` → `'2,5 kg'`), `ROTULO_STATUS: Record<StatusCampanha, string>` (`Rascunho`, `Ativa`, `Encerrada`), `COR_STATUS` (`gray`, `green`, `red`), `ROTULO_UNIDADE: Record<Unidade, string>` (`unidade(s)`, `kg`, `litro(s)`, `pacote(s)`, `caixa(s)`, `par(es)`)
  - `erros.ts`: `aplicarErrosDeCampo(form: { setFieldError(campo: string, msg: string): void }, erro: unknown): boolean` (true se aplicou algum erro de campo)

- [ ] **Step 1: Testes (falham)**

```ts
test('formatarData não desloca o dia por fuso horário', () => {
  expect(formatarData('2026-10-11')).toBe('11/10/2026')
  expect(formatarData('2026-01-01')).toBe('01/01/2026')
})
test('formatarData de nulo mostra travessão', () => { expect(formatarData(null)).toBe('—') })
test('formatarMoeda usa o padrão brasileiro', () => {
  expect(formatarMoeda(1234.5).replace(/\s/g, ' ')).toBe('R$ 1.234,50')   // Intl usa espaço não separável
})
test('formatarQuantidade usa vírgula e rótulo da unidade', () => {
  expect(formatarQuantidade(2.5, 'KG')).toBe('2,5 kg')
  expect(formatarQuantidade(10, 'PACOTE')).toBe('10 pacote(s)')
})
test('aplicarErrosDeCampo aplica cada erro e devolve true', () => { /* ApiError com 2 erros → setFieldError chamado 2× */ })
test('aplicarErrosDeCampo devolve false sem erros de campo', () => { /* Error comum → false */ })
test('listarCampanhas monta a query com status e página', async () => {
  // fetch mockado; listarCampanhas({ status: 'ATIVA', pagina: 2 }) → URL '/api/campanhas?status=ATIVA&pagina=2'
})
test('listarCampanhas sem status omite o parâmetro', async () => { /* '/api/campanhas?pagina=0' */ })
```

- [ ] **Step 2: Rodar e ver falhar** — Run (em `frontend/`): `npm test` → Expected: FAIL (módulos inexistentes)

- [ ] **Step 3: Implementar**; trocar o laço de erros de `MinhaContaPage` por `aplicarErrosDeCampo` (seus testes continuam verdes).

- [ ] **Step 4: Rodar e ver passar** — Run: `npm run lint && npm run typecheck && npm test` → Expected: tudo verde

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(frontend): API de usuários e campanhas, formatação pt-BR"
```

---

### Task 6: Tela de Usuários (ADMIN)

**Files:**
- Create: `src/pages/UsuariosPage.tsx`, `src/pages/UsuarioFormModal.tsx`, `src/pages/RedefinirSenhaModal.tsx`
- Modify: `src/App.tsx` (rota `usuarios` → `UsuariosPage`)
- Test: `src/pages/UsuariosPage.test.tsx` (`vi.mock('../api/usuarios')` + `mockAuth` com ADMIN)

**Interfaces:**
- Consumes: `api/usuarios.ts`, `aplicarErrosDeCampo`, `useAuth()` (Task 5, Plano 1)
- Produces:
  - `UsuariosPage`: título "Usuários", botão "Novo usuário", tabela (`Table.ScrollContainer`) com colunas Nome, E-mail, Papel (`Administrador`/`Voluntário`), Situação (`Ativo`/`Inativo`), e ações "Editar" e "Redefinir senha" por linha; `Pagination` quando `totalPaginas > 1`; query key `['usuarios', pagina]`, invalidada após salvar.
  - `UsuarioFormModal`: props `{ aberto: boolean; usuario: Usuario | null; aoFechar(): void }`; criação pede Nome, E-mail, Papel e Senha; edição pede Nome, E-mail, Papel e o switch "Ativo"; botão "Salvar".
  - `RedefinirSenhaModal`: props `{ usuario: Usuario | null; aoFechar(): void }`; campo "Nova senha"; botão "Redefinir".

- [ ] **Step 1: Testes (falham)**

```tsx
test('lista usuários com papel e situação', async () => { /* "Ana Admin", "Administrador", "Ativo"; "Vitor", "Voluntário", "Inativo" */ })
test('cria usuário e recarrega a lista', async () => { /* preenche, Salvar → criarUsuario chamado com {nome, email, papel:'VOLUNTARIO', senha}; listarUsuarios chamado de novo */ })
test('erro de e-mail duplicado aparece no campo', async () => { /* ApiError 400 erros[{campo:'email', mensagem:'Já existe um usuário com este e-mail.'}] → texto visível */ })
test('409 ao desativar a si mesmo mostra a mensagem', async () => { /* ApiError 409 detail msg fixada → Alert visível */ })
test('redefine a senha de um usuário', async () => { /* redefinirSenhaUsuario(2, 'nova-senha-1') */ })
test('senha curta é bloqueada no formulário de criação', async () => { /* "1234567" → "A senha deve ter ao menos 8 caracteres." sem chamar a API */ })
```

- [ ] **Step 2: Rodar e ver falhar** — `npm test` → FAIL

- [ ] **Step 3: Implementar**

- [ ] **Step 4: Rodar e ver passar** — `npm run lint && npm run typecheck && npm test` → verde

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(frontend): tela de usuários"
```

---

### Task 7: Lista de Campanhas e formulário

**Files:**
- Create: `src/pages/CampanhasPage.tsx`, `src/pages/CampanhaFormModal.tsx`
- Modify: `src/App.tsx` (rota `campanhas` → `CampanhasPage`)
- Test: `src/pages/CampanhasPage.test.tsx`

**Interfaces:**
- Consumes: `api/campanhas.ts`, `formatos.ts`, `aplicarErrosDeCampo`, `useAuth()`
- Produces:
  - `CampanhasPage`: título "Campanhas"; `SegmentedControl` com `Ativas` (padrão) / `Rascunho` / `Encerradas` / `Todas`; cartões (um por campanha, link para `/campanhas/{id}`) com título, `Badge` de status (`ROTULO_STATUS`/`COR_STATUS`), período `formatarData(início) – formatarData(fim)` e meta `formatarMoeda`; texto `Nenhuma campanha encontrada.` quando vazio; botão "Nova campanha" só para ADMIN; query key `['campanhas', status, pagina]`.
  - `CampanhaFormModal`: props `{ aberto: boolean; campanha: Campanha | null; aoFechar(): void; aoSalvar?(c: Campanha): void }`; campos Título, Descrição (`Textarea`), Data de início e Data de término (`TextInput type="date"`), Meta em R$ (`NumberInput` com `decimalSeparator=","`, `thousandSeparator="."`, `prefix="R$ "`, `decimalScale={2}`, opcional); validação local: título obrigatório, data de início obrigatória, término ≥ início (mesma mensagem fixada); campos vazios enviados como `null`.

- [ ] **Step 1: Testes (falham)**

```tsx
test('mostra campanhas ativas por padrão', async () => { /* listarCampanhas chamado com { status: 'ATIVA', pagina: 0 }; título e "Ativa" visíveis; período "20/10/2026 – 30/11/2026" */ })
test('trocar o filtro para Todas recarrega sem status', async () => { /* listarCampanhas({ status: undefined, pagina: 0 }) */ })
test('lista vazia mostra mensagem', async () => { })
test('voluntário não vê "Nova campanha"', async () => { })
test('admin cria campanha com meta em reais', async () => {
  // digita título, início 2026-10-20, meta "1.500,50" → criarCampanha({ titulo, descricao: null, dataInicio: '2026-10-20', dataFim: null, metaValor: 1500.5 })
})
test('término antes do início é bloqueado no formulário', async () => { /* msg fixada; API não chamada */ })
```

- [ ] **Step 2: Rodar e ver falhar** — `npm test` → FAIL

- [ ] **Step 3: Implementar**

- [ ] **Step 4: Rodar e ver passar** — `npm run lint && npm run typecheck && npm test` → verde

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(frontend): lista e cadastro de campanhas"
```

---

### Task 8: Detalhe da campanha e necessidades

**Files:**
- Create: `src/pages/CampanhaDetalhePage.tsx`, `src/pages/NecessidadeFormModal.tsx`
- Modify: `src/App.tsx` (rota `campanhas/:id`)
- Test: `src/pages/CampanhaDetalhePage.test.tsx`

**Interfaces:**
- Consumes: `api/campanhas.ts`, `formatos.ts`, `aplicarErrosDeCampo`, `CampanhaFormModal` (Task 7), `useAuth()`
- Produces:
  - `CampanhaDetalhePage`: lê `:id`; mostra título, badge de status, período, descrição, meta; seção "Necessidades" com tabela Item / Meta (`formatarQuantidade`) / Observação, ou `Nenhuma necessidade cadastrada.`; para ADMIN: botões "Editar campanha" (abre `CampanhaFormModal`), menu de status com as transições para os outros dois status (`Ativar`, `Encerrar`, `Voltar para rascunho`), "Adicionar necessidade", e por linha "Editar"/"Excluir" (excluir pede confirmação em modal: `Excluir "{item}"?`); 409 na exclusão mostra a mensagem da API em `Alert`; campanha inexistente (404) mostra `Campanha não encontrada.` com link "Voltar para campanhas". Query keys `['campanha', id]` e `['necessidades', id]`.
  - `NecessidadeFormModal`: props `{ campanhaId: number; necessidade: Necessidade | null; aberto: boolean; aoFechar(): void }`; campos Item, Unidade (`Select` com `ROTULO_UNIDADE`), Quantidade (`NumberInput` com `decimalSeparator=","`, `decimalScale={2}`, mínimo > 0), Observação.

> As colunas Arrecadado / Pendente e a barra de progresso chegam no Plano 4.

- [ ] **Step 1: Testes (falham)**

```tsx
test('mostra dados da campanha e necessidades', async () => { /* "Arroz", "50 kg" */ })
test('voluntário não vê botões de edição', async () => { })
test('admin adiciona necessidade com quantidade decimal digitada com vírgula', async () => {
  // Item "Feijão", Unidade KG, Quantidade "2,5" → criarNecessidade(1, { item: 'Feijão', unidade: 'KG', quantidadeMeta: 2.5, observacao: null })
})
test('admin encerra a campanha', async () => { /* menu → "Encerrar" → alterarStatusCampanha(1, 'ENCERRADA') */ })
test('excluir pede confirmação e chama a API', async () => { /* excluirNecessidade(10) só após confirmar */ })
test('409 ao excluir mostra a mensagem da regra', async () => { /* "Esta necessidade já recebeu doações e não pode ser excluída." */ })
test('campanha inexistente mostra mensagem e link de volta', async () => { /* buscarCampanha rejeita ApiError 404 */ })
```

- [ ] **Step 2: Rodar e ver falhar** — `npm test` → FAIL

- [ ] **Step 3: Implementar**; nos cartões da `CampanhasPage`, garantir que o link leva a `/campanhas/{id}`.

- [ ] **Step 4: Rodar e ver passar** — `npm run lint && npm run typecheck && npm test && npm run build` → verde

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(frontend): detalhe da campanha e gestão de necessidades"
```

---

### Task 9: Verificação ponta a ponta

- [ ] **Step 1:** Subir `db`, backend (perfil `dev`) e frontend (como no README).
- [ ] **Step 2:** Como ADMIN no navegador: criar um usuário VOLUNTARIO; criar uma campanha com meta `1.500,50`; ativá-la; adicionar necessidades `Arroz 50 kg` e `Óleo 2,5 litro(s)`; editar uma; excluir outra.
- [ ] **Step 3:** Em outra sessão, entrar como o voluntário: vê a campanha e as necessidades, não vê "Usuários" nem botões de edição.
- [ ] **Step 4:** Como ADMIN, desativar o voluntário; na sessão do voluntário, a próxima ação leva ao login com "Sua sessão expirou. Entre novamente."
- [ ] **Step 5:** Largura de celular (375 px): lista de campanhas e tabela de necessidades legíveis, sem rolagem horizontal da página.
- [ ] **Step 6:** Parar os servidores; registrar os resultados no ledger.
