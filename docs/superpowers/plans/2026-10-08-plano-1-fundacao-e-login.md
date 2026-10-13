# Plano 1 — Fundação e Login Ponta a Ponta — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Monorepo funcionando localmente com banco versionado, CI verde nos dois jobs e login de ponta a ponta (API de autenticação com sessão + CSRF, tela de login React, rotas protegidas, layout com navegação, troca da própria senha, ADMIN inicial).

**Architecture:** Spring Boot (API REST sob `/api`, sessão no servidor, CSRF em cookie) + PostgreSQL via Docker Compose; React + TypeScript (Vite) em `frontend/` com proxy de `/api` para `:8080`. O embutimento do React no JAR fica para o Plano 5.

**Tech Stack:** Java 25, Spring Boot 4.1.1, Spring Security, Spring Data JPA, Flyway, PostgreSQL 16, Testcontainers, JUnit 5/Mockito/AssertJ, Checkstyle, JaCoCo; Vite, React, TypeScript, Mantine, React Router, TanStack Query, Vitest, React Testing Library; GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-10-08-gestao-doacoes-design.md` (seções 2, 3, 5, 6, 7, 8, 9)

## Roteiro dos planos

| Plano | Escopo | Datas |
|---|---|---|
| **1 (este)** | Fundação, CI, login ponta a ponta | 08–09/10 |
| 2 | Usuários, Campanhas, Necessidades (API + telas) | 10–13/10 |
| 3 | Doadores e Doações (API com regras R1–R9 + telas) | 14–17/10 |
| 4 | Progresso, Painel, Relatórios, OpenAPI, Postman | 18–21/10 |
| 5 | Dockerfile 3 estágios + SPA embutida, release, deploy, backup, README | 22/10 |

Cada plano é escrito ao final do anterior, com base no código que existir.

## Global Constraints

- Java 25 (LTS; JDK local: Zulu 25); Node 26; Spring Boot 4.1.1; Maven via wrapper (`./mvnw`, nunca `mvn` global).
- Pacote base `br.org.iap.doacoes`; organização por funcionalidade (spec §7).
- Spring Boot 4 moveu as anotações de teste de slice (`@WebMvcTest`, `@DataJpaTest`, `@AutoConfigureMockMvc`) para módulos próprios; use os starters de teste gerados pelo Initializr e importe do pacote que o projeto gerado expõe.
- Esquema do banco somente via Flyway; `spring.jpa.hibernate.ddl-auto=validate`; `spring.jpa.open-in-view=false`.
- Dinheiro e quantidades: `NUMERIC(12,2)` / `BigDecimal`.
- DTOs são `record`s (`*Request` / `*Response`); entidades JPA nunca entram nem saem da API.
- Erros: `ProblemDetail`; validação → `400` com propriedade `erros: [{campo, mensagem}]`; `401` não autenticado; `403` sem permissão; `404` não encontrado; `409` regra de negócio.
- Sessão: timeout `30m`; cookie `HttpOnly`, `SameSite=Lax`; `Secure` só no perfil `prod`.
- Senha: BCrypt, mínimo 8 caracteres.
- Bloqueio de login: 5 falhas consecutivas → 15 minutos.
- Mensagem de login inválido (única, genérica): `E-mail ou senha inválidos.`
- CSRF: cookie `XSRF-TOKEN`, cabeçalho `X-XSRF-TOKEN`.
- Segredos só em variáveis de ambiente; `.env` no `.gitignore`; `.env.example` com valores fictícios.
- Textos da interface em português do Brasil.
- Frontend: TypeScript estrito; `dangerouslySetInnerHTML` proibido via ESLint.
- Commits no padrão Conventional Commits; nada de push até o repositório GitHub existir (Task 8).

## Review Focus

1. **Sessão expira com formulário aberto (30 min)** — qualquer chamada recebe `401`; o usuário deve ir para `/login` vendo "Sua sessão expirou. Entre novamente." → teste na Task 7.
2. **E-mail digitado com maiúsculas/espaços** (`" Maria@Igreja.com "`) — login deve funcionar → teste na Task 4.
3. **Rotação do token CSRF após o login** — o primeiro POST depois de entrar não pode falhar com `403`; o cliente deve renovar o token após o login → teste na Task 7.
4. **Variações de caixa no bloqueio** — `A@x.com` e `a@x.com` contam como o mesmo e-mail → teste na Task 3.
5. **Banco vazio sem `ADMIN_EMAIL`/`ADMIN_SENHA`, ou senha curta** — a aplicação sobe com aviso no log quando faltam variáveis; falha ao iniciar com mensagem clara se a senha tiver < 8 caracteres → teste na Task 2.

---

## Estrutura de arquivos deste plano

```
.gitattributes, .gitignore, .env.example, docker-compose.yml, README.md
config/checkstyle.xml
.github/workflows/ci.yml
backend/  (gerado pelo Initializr)
  pom.xml                                        + Checkstyle, JaCoCo
  src/main/resources/application.yml, application-dev.yml, application-prod.yml
  src/main/resources/db/migration/V1__esquema_inicial.sql
  src/main/java/br/org/iap/doacoes/
    config/ClockConfig.java, SecurityConfig.java, AdminInicialRunner.java
    comum/GlobalExceptionHandler.java, RegraNegocioException.java, NaoEncontradoException.java
    usuario/Usuario.java, Papel.java, UsuarioRepository.java
    auth/UsuarioAutenticado.java, UsuarioDetailsService.java, LoginAttemptService.java,
         AuthController.java, LoginRequest.java, AlterarSenhaRequest.java, UsuarioLogadoResponse.java
  src/test/java/br/org/iap/doacoes/  (espelha os pacotes)
frontend/  (gerado pelo Vite)
  vite.config.ts, eslint.config.js, package.json
  src/main.tsx, src/App.tsx
  src/api/client.ts, src/api/auth.ts
  src/auth/AuthContext.tsx, RotaProtegida.tsx, RotaAdmin.tsx
  src/layout/AppShellLayout.tsx
  src/pages/LoginPage.tsx, MinhaContaPage.tsx, EmConstrucaoPage.tsx
  src/test/setup.ts, src/test/render.tsx
```

---

### Task 0: Ambiente de desenvolvimento (manual, feito pela Gabriela)

Hoje a máquina tem apenas o Git. Instalações são feitas por você, no PowerShell.

- [ ] **Step 1: Instalar JDK 25, Node 26 e Docker Desktop (+ WSL)**

```bash
wsl --install
```
```bash
winget install OpenJS.NodeJS.LTS
```
```bash
winget install Docker.DockerDesktop
```

Abra o Docker Desktop uma vez (ele pode pedir para habilitar o WSL 2 e reiniciar).

- [ ] **Step 2: Verificar (em um terminal novo)**

Run: `java -version` → `openjdk version "25...`
Run: `node --version` → `v22...` ou superior
Run: `docker run --rm hello-world` → `Hello from Docker!`

---

### Task 1: Repositório, banco e esqueleto do backend

**Files:**
- Create: `.gitattributes`, `.gitignore`, `.env.example`, `docker-compose.yml`, `config/checkstyle.xml`
- Create: `backend/` (Initializr), `backend/src/main/resources/application*.yml`, `backend/src/main/resources/db/migration/V1__esquema_inicial.sql`
- Modify: `backend/pom.xml` (Checkstyle, JaCoCo)
- Test: `backend/src/test/java/br/org/iap/doacoes/MigracaoFlywayTest.java`

**Interfaces:**
- Produces: tabelas `usuario`, `campanha`, `necessidade`, `doador`, `doacao`, `item_doacao` (spec §3); classe de teste `TestcontainersConfiguration` (gerada) com `PostgreSQLContainer` `postgres:16-alpine` e `@ServiceConnection`; variáveis `DB_URL`, `DB_USER`, `DB_PASSWORD`, `ADMIN_EMAIL`, `ADMIN_SENHA`.

- [ ] **Step 1: `git init` e arquivos de raiz**

```bash
git init -b main
```

- `.gitattributes`: `* text=auto eol=lf`, `*.cmd text eol=crlf`, `*.bat text eol=crlf` (o CI roda em Linux; evita `mvnw` quebrado por CRLF).
- `.gitignore`: `.env`, `backend/target/`, `frontend/node_modules/`, `frontend/dist/`, `.idea/`, `*.iml`, `.vscode/`, `backups/`.
- `.env.example`:
  ```
  DB_URL=jdbc:postgresql://localhost:5432/doacoes
  DB_USER=doacoes
  DB_PASSWORD=troque-esta-senha
  ADMIN_EMAIL=admin@exemplo.org
  ADMIN_SENHA=troque-esta-senha
  ```
- Copie para `.env` e troque as senhas.
- `docker-compose.yml`: serviço `db` com `postgres:16-alpine`, `POSTGRES_DB=doacoes`, usuário/senha vindos de `${DB_USER}`/`${DB_PASSWORD}`, porta `5432:5432`, volume nomeado `dados-postgres`, healthcheck `pg_isready`.

- [ ] **Step 2: Gerar o backend**

```bash
curl.exe https://start.spring.io/starter.zip -d type=maven-project -d language=java -d bootVersion=4.1.1 -d javaVersion=25 -d groupId=br.org.iap -d artifactId=doacoes -d name=doacoes -d packageName=br.org.iap.doacoes -d baseDir=backend -d dependencies=web,security,data-jpa,validation,flyway,postgresql,actuator,testcontainers,devtools -o backend.zip
```

Extraia na raiz (`Expand-Archive backend.zip .`), apague o zip e confirme `backend/src/main/java/br/org/iap/doacoes/DoacoesApplication.java`. Na classe `TestcontainersConfiguration` gerada, fixe a imagem em `postgres:16-alpine`.

```bash
git add --chmod=+x backend/mvnw
```

- [ ] **Step 3: Configuração**

- Renomeie `application.properties` → `application.yml` com:
  - `spring.datasource.url: ${DB_URL:jdbc:postgresql://localhost:5432/doacoes}`, `username: ${DB_USER:doacoes}`, `password: ${DB_PASSWORD:}` (os padrões evitam falha de placeholder nos testes/CI, onde o Testcontainers substitui a conexão)
  - `spring.jpa.hibernate.ddl-auto: validate`, `spring.jpa.open-in-view: false`
  - `server.servlet.session.timeout: 30m`, `server.servlet.session.cookie.http-only: true`, `same-site: lax`
  - `management.endpoints.web.exposure.include: health`
  - `app.admin-inicial.email: ${ADMIN_EMAIL:}`, `app.admin-inicial.senha: ${ADMIN_SENHA:}`
- `application-dev.yml`: `spring.config.import: optional:file:../.env[.properties]` e `spring.jpa.show-sql: true`.
- `application-prod.yml`: `server.servlet.session.cookie.secure: true`.

- [ ] **Step 4: Escrever o teste de migração (falha: sem V1)**

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class MigracaoFlywayTest {
    @Autowired JdbcTemplate jdbc;

    @Test
    void criaTodasAsTabelasDoModelo() {
        List<String> tabelas = jdbc.queryForList(
            "select table_name from information_schema.tables where table_schema = 'public'", String.class);
        assertThat(tabelas).contains("usuario", "campanha", "necessidade", "doador", "doacao", "item_doacao");
    }

    @Test
    void rejeitaDoacaoEmDinheiroSemValor() {
        // insere usuario e campanha mínimos, depois:
        assertThatThrownBy(() -> jdbc.update(
            "insert into doacao (campanha_id, tipo, data_doacao, registrado_por) values (?, 'DINHEIRO', current_date, ?)",
            campanhaId, usuarioId)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejeitaQuantidadeZeroEmItemDoacao() { /* insert com quantidade 0 → DataIntegrityViolationException */ }
}
```

- [ ] **Step 5: Rodar e ver falhar**

Run (em `backend/`): `./mvnw test -Dtest=MigracaoFlywayTest`
Expected: FAIL (tabelas inexistentes)

- [ ] **Step 6: Escrever `V1__esquema_inicial.sql`**

Tabelas e colunas exatamente como na spec §3. Decisões:
- PKs `bigint generated always as identity`; datas/horas `timestamptz not null default now()`; `data_doacao date not null`.
- Enums como `varchar(20)` com `CHECK (... in (...))`.
- `usuario.email` `unique`, sempre gravado em minúsculas pela aplicação.
- `CHECK` em `necessidade.quantidade_meta > 0`, `item_doacao.quantidade > 0`, `campanha.meta_valor is null or meta_valor > 0`.
- `CHECK` em `doacao`: `(tipo = 'DINHEIRO' and valor > 0 and forma_pagamento is not null) or (tipo = 'ITEM' and valor is null and forma_pagamento is null)`.
- `item_doacao.doacao_id` com `on delete cascade`; demais FKs sem cascade.
- Índices em todas as FKs.

- [ ] **Step 7: Rodar e ver passar**

Run: `./mvnw test -Dtest=MigracaoFlywayTest`
Expected: PASS (3 testes)

- [ ] **Step 8: Checkstyle e JaCoCo no `pom.xml`**

- `config/checkstyle.xml`:
  - `LineLength` max 120.
  - `TreeWalker` com: `AvoidStarImport`, `UnusedImports`, `RedundantImport`, `NeedBraces`, `EmptyCatchBlock`, `EqualsHashCode`, `OneStatementPerLine`, `MultipleVariableDeclarations`, `TypeName`, `MethodName`, `ConstantName`, `LocalVariableName`, `MemberName`, `ParameterName`.
  - `RegexpSinglelineJava` proibindo `System\.(out|err)\.print`.
- `maven-checkstyle-plugin`:
  - Com dependência explícita na versão mais recente de `com.puppycrawl.tools:checkstyle` (suporte a Java 25).
  - `configLocation` `${project.basedir}/../config/checkstyle.xml`.
  - `includeTestSourceDirectory` `true`, `failOnViolation` `true`.
  - Goal `check` na fase `validate`.
- `jacoco-maven-plugin`:
  - `prepare-agent`; `report` e `check` na fase `verify`.
  - Regra: elemento `CLASS`, `includes` `br.org.iap.doacoes.**.*Service`, `LINE` `COVEREDRATIO` mínimo `0.70`.

- [ ] **Step 9: Verificar build completo**

Run: `./mvnw verify`
Expected: `BUILD SUCCESS`

- [ ] **Step 10: Commit**

```bash
git add -A
git commit -m "chore: esqueleto do backend, banco versionado e qualidade de código"
```

---

### Task 2: Usuário, carregamento para o Spring Security e ADMIN inicial

**Files:**
- Create: `usuario/Papel.java`, `usuario/Usuario.java`, `usuario/UsuarioRepository.java`
- Create: `auth/UsuarioAutenticado.java`, `auth/UsuarioDetailsService.java`
- Create: `config/AdminInicialRunner.java`, `config/ClockConfig.java`
- Test: `auth/UsuarioDetailsServiceTest.java`, `config/AdminInicialRunnerTest.java`

**Interfaces:**
- Produces:
  - `enum Papel { ADMIN, VOLUNTARIO }`
  - `Usuario` (entidade `usuario`; `setEmail` normaliza com `trim().toLowerCase(Locale.ROOT)`; getters `getId(): Long`, `getNome()`, `getEmail()`, `getSenhaHash()`, `getPapel()`, `isAtivo()`; `setSenhaHash(String)`)
  - `UsuarioRepository extends JpaRepository<Usuario, Long>` com `Optional<Usuario> findByEmail(String emailNormalizado)`
  - `UsuarioAutenticado implements UserDetails` com `Long getId()`, `String getNome()`, `Papel getPapel()`; autoridade `ROLE_<PAPEL>`; `isEnabled()` = `ativo`
  - `UsuarioDetailsService implements UserDetailsService` (normaliza o e-mail antes da busca)
  - `@Bean Clock clock()` = `Clock.systemDefaultZone()`
  - `@Bean PasswordEncoder` fica na Task 4 (`SecurityConfig`); nos testes desta task use `new BCryptPasswordEncoder()`.

- [ ] **Step 1: Testes (falham)**

```java
class UsuarioDetailsServiceTest {
    // UsuarioRepository mockado
    @Test void carregaPorEmailNormalizado() {
        // repo.findByEmail("maria@igreja.org") retorna usuário VOLUNTARIO ativo
        UserDetails u = service.loadUserByUsername("  Maria@Igreja.org ");
        assertThat(u.getUsername()).isEqualTo("maria@igreja.org");
        assertThat(u.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_VOLUNTARIO");
        assertThat(((UsuarioAutenticado) u).getId()).isEqualTo(7L);
    }
    @Test void usuarioInativoFicaDesabilitado() { /* isEnabled() == false */ }
    @Test void emailInexistenteLancaUsernameNotFound() { /* assertThatThrownBy(...).isInstanceOf(UsernameNotFoundException.class) */ }
}

class AdminInicialRunnerTest {
    // UsuarioRepository mockado, BCryptPasswordEncoder real
    @Test void criaAdminQuandoBancoVazio() {
        // count() = 0; email "Admin@Igreja.org", senha "segredo123"
        // verify(repo).save(captor) → email "admin@igreja.org", papel ADMIN, ativo, encoder.matches("segredo123", hash)
    }
    @Test void naoFazNadaQuandoJaExisteUsuario() { /* count() = 1 → verify(repo, never()).save(any()) */ }
    @Test void naoQuebraQuandoVariaveisAusentes() { /* email e senha vazios, count() = 0 → sem exceção, sem save */ }
    @Test void falhaComMensagemClaraSeSenhaCurta() {
        // senha "1234567" → IllegalStateException com mensagem "ADMIN_SENHA deve ter ao menos 8 caracteres"
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./mvnw test -Dtest="UsuarioDetailsServiceTest,AdminInicialRunnerTest"`
Expected: FAIL (classes inexistentes)

- [ ] **Step 3: Implementar**

`AdminInicialRunner implements ApplicationRunner`:
- Construtor `(UsuarioRepository, PasswordEncoder, @Value("${app.admin-inicial.email}") String email, @Value("${app.admin-inicial.senha}") String senha)`.
- Se `count() > 0`, não faz nada.
- Se e-mail ou senha estiverem vazios, `log.warn("Nenhum usuário cadastrado e ADMIN_EMAIL/ADMIN_SENHA não definidos")`.
- Senha com menos de 8 caracteres lança `IllegalStateException`.
- Nome do ADMIN criado: `"Administrador"`.

- [ ] **Step 4: Rodar e ver passar**

Run: `./mvnw test -Dtest="UsuarioDetailsServiceTest,AdminInicialRunnerTest"`
Expected: PASS (7 testes)

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(auth): entidade de usuário, UserDetailsService e ADMIN inicial"
```

---

### Task 3: Controle de tentativas de login

**Files:**
- Create: `auth/LoginAttemptService.java`
- Test: `auth/LoginAttemptServiceTest.java`

**Interfaces:**
- Consumes: `Clock` (Task 2)
- Produces: `LoginAttemptService` (`@Service`, construtor `(Clock)`) com:
  - `boolean estaBloqueado(String email)`
  - `void registrarFalha(String email)`
  - `void registrarSucesso(String email)`
  - Constantes `MAX_TENTATIVAS = 5`, `BLOQUEIO = Duration.ofMinutes(15)`
  - Chave = e-mail com `trim().toLowerCase(Locale.ROOT)`; estado em `ConcurrentHashMap`

- [ ] **Step 1: Testes (falham)** — usar um `Clock` mutável de teste (ex.: classe interna `RelogioAjustavel extends Clock` com `avancar(Duration)`).

```java
@Test void quatroFalhasNaoBloqueiam()                 { /* 4× registrarFalha → estaBloqueado == false */ }
@Test void quintaFalhaBloqueia()                      { /* 5× → true */ }
@Test void bloqueioExpiraApos15Minutos()              { /* 5×; avancar(14min59s) → true; avancar(1s) → false */ }
@Test void aposExpirarContadorRecomeca()              { /* 5×; avancar(15min); 1× falha → false */ }
@Test void sucessoZeraContador()                      { /* 4×; registrarSucesso; 4× → false */ }
@Test void emailComCaixaEEspacosDiferentesEOMesmo()   { /* 3× "A@x.com", 2× " a@X.COM " → estaBloqueado("a@x.com") == true */ }
@Test void emailsDiferentesSaoIndependentes()         { /* 5× "a@x.com" → estaBloqueado("b@x.com") == false */ }
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./mvnw test -Dtest=LoginAttemptServiceTest`
Expected: FAIL

- [ ] **Step 3: Implementar `LoginAttemptService`**

Por e-mail, guarde `(falhas, bloqueadoAte)`. Ao atingir 5 falhas, `bloqueadoAte = agora + 15min`. Quando `estaBloqueado` encontrar o bloqueio expirado, remove a entrada.

- [ ] **Step 4: Rodar e ver passar**

Run: `./mvnw test -Dtest=LoginAttemptServiceTest`
Expected: PASS (7 testes)

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(auth): bloqueio após 5 tentativas de login falhas"
```

---

### Task 4: Segurança HTTP e API de autenticação

**Files:**
- Create: `config/SecurityConfig.java`
- Create: `comum/GlobalExceptionHandler.java`, `comum/RegraNegocioException.java`, `comum/NaoEncontradoException.java`, `comum/CampoInvalidoException.java`
- Create: `auth/AuthController.java`, `auth/LoginRequest.java`, `auth/AlterarSenhaRequest.java`, `auth/UsuarioLogadoResponse.java`
- Test: `auth/AuthApiTest.java` (`@SpringBootTest` + `@AutoConfigureMockMvc` + `@Import(TestcontainersConfiguration.class)`)

**Interfaces:**
- Consumes: `UsuarioRepository`, `UsuarioDetailsService`, `UsuarioAutenticado`, `LoginAttemptService`
- Produces:
  - `@Bean PasswordEncoder` (BCrypt); `@Bean AuthenticationManager`; `@Bean SecurityContextRepository` (`HttpSessionSecurityContextRepository`); `@EnableMethodSecurity` (usado nos próximos planos)
  - Endpoints:
    - `GET /api/auth/csrf` → `204`, garante o cookie `XSRF-TOKEN`
    - `POST /api/auth/login` (`LoginRequest(@NotBlank @Email String email, @NotBlank String senha)`) → `200 UsuarioLogadoResponse(Long id, String nome, String email, Papel papel)`
    - `POST /api/auth/logout` → `204`
    - `GET /api/auth/me` → `200 UsuarioLogadoResponse` | `401`
    - `PUT /api/auth/minha-senha` (`AlterarSenhaRequest(@NotBlank String senhaAtual, @NotBlank @Size(min = 8) String novaSenha)`) → `204`
  - `RegraNegocioException(String mensagem)` → `409`; `NaoEncontradoException(String mensagem)` → `404`
  - Validação → `400` `ProblemDetail` com `erros: List<{campo, mensagem}>`
  - Erro de domínio em campo: `400` com `erros=[{campo:"senhaAtual", mensagem:"Senha atual incorreta."}]` via `CampoInvalidoException(String campo, String mensagem)`, também tratada pelo `GlobalExceptionHandler`

- [ ] **Step 1: Testes (falham)**

Fixture `@BeforeEach`: apaga usuários e salva
- `maria@igreja.org` / `senha-forte-1`, VOLUNTARIO, ativo
- `inativo@igreja.org` / `senha-forte-1`, inativo

Helper `MockHttpSession login(email, senha)` faz o POST com `.with(csrf())` e devolve a sessão.

```java
@Test void csrfDevolveCookieXsrf()            { /* GET /api/auth/csrf → 204, cookie "XSRF-TOKEN" presente */ }
@Test void meSemSessaoRetorna401()            { /* GET /api/auth/me → 401 */ }
@Test void loginSemCsrfRetorna403()           { /* POST /api/auth/login sem .with(csrf()) → 403 */ }
@Test void loginValidoRetornaUsuarioEAbreSessao() {
    // POST → 200, jsonPath $.email == "maria@igreja.org", $.papel == "VOLUNTARIO", sem campo senha/senhaHash
    // GET /api/auth/me com a sessão → 200, $.nome == "Maria"
}
@Test void loginAceitaEmailComCaixaEEspacos() { /* email " Maria@Igreja.ORG " → 200 */ }
@Test void loginSenhaErradaRetorna401Generico() { /* 401, $.detail == "E-mail ou senha inválidos." */ }
@Test void loginEmailInexistenteRetornaMesmaMensagem() { /* 401, mesmo $.detail */ }
@Test void loginUsuarioInativoRetornaMesmaMensagem()   { /* 401, mesmo $.detail */ }
@Test void loginEmailMalformadoRetorna400ComErros()    { /* email "abc" → 400, $.erros[0].campo == "email" */ }
@Test void bloqueiaAposCincoFalhasMesmoComSenhaCorreta() {
    // 5× senha errada → 401; 6ª com senha correta → 429, $.detail == "Muitas tentativas. Tente novamente em 15 minutos."
}
@Test void logoutInvalidaSessao()             { /* POST logout (csrf) → 204; GET me com a mesma sessão → 401 */ }
@Test void minhaSenhaComSenhaAtualErradaRetorna400() { /* $.erros[0].campo == "senhaAtual" */ }
@Test void minhaSenhaCurtaRetorna400()        { /* novaSenha "1234567" → 400, campo "novaSenha" */ }
@Test void minhaSenhaTrocaEPermiteNovoLogin() { /* 204; login com a nova → 200; com a antiga → 401 */ }
@Test void minhaSenhaSemSessaoRetorna401()    { }
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./mvnw test -Dtest=AuthApiTest`
Expected: FAIL

- [ ] **Step 3: Implementar `SecurityConfig`**

- `permitAll`: `/api/auth/csrf`, `/api/auth/login`, `/actuator/health`.
- `/api/**`: `authenticated()`.
- Demais rotas: `permitAll` (servirão a SPA no Plano 5).
- `formLogin` e `httpBasic` desabilitados.
- `exceptionHandling`: `HttpStatusEntryPoint(UNAUTHORIZED)` (sem redirect).
- CSRF: `csrf.spa()` se disponível na versão do Spring Security gerada. Caso contrário, `CookieCsrfTokenRepository.withHttpOnlyFalse()` com o `SpaCsrfTokenRequestHandler` descrito na seção "Single-Page Applications" da referência do Spring Security.
- `sessionManagement().sessionFixation().changeSessionId()`.

- [ ] **Step 4: Implementar `AuthController` e `GlobalExceptionHandler`**

Fluxo do login:
1. Se `estaBloqueado(email)`, retorna `429`.
2. Chama `authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(email, senha))`.
3. Se lançar `AuthenticationException` (inclui `DisabledException`), chama `registrarFalha` e retorna `401` genérico.
4. Em caso de sucesso:
   - chama `registrarSucesso`;
   - cria um `SecurityContext` novo com a autenticação;
   - chama `securityContextRepository.saveContext(ctx, request, response)`;
   - chama `request.changeSessionId()` se já houver sessão;
   - retorna `200`.

Logout: `new SecurityContextLogoutHandler().logout(request, response, auth)`.

`minha-senha`: carrega o `Usuario` pelo id do `UsuarioAutenticado`, confere a senha atual com `PasswordEncoder.matches`, grava o novo hash.

- [ ] **Step 5: Rodar e ver passar**

Run: `./mvnw test -Dtest=AuthApiTest`
Expected: PASS (15 testes)

- [ ] **Step 6: Build completo**

Run: `./mvnw verify`
Expected: `BUILD SUCCESS`, incluindo Checkstyle e JaCoCo (≥ 70% nos `*Service`).

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat(auth): API de login com sessão, CSRF, logout e troca de senha"
```

---

### Task 5: Esqueleto do frontend

**Files:**
- Create: `frontend/` (Vite `react-ts`), `frontend/src/test/setup.ts`, `frontend/src/test/render.tsx`
- Modify: `frontend/vite.config.ts`, `frontend/eslint.config.js`, `frontend/package.json`, `frontend/src/main.tsx`, `frontend/src/App.tsx`
- Create: `frontend/postcss.config.cjs`
- Test: `frontend/src/App.test.tsx`

**Interfaces:**
- Produces:
  - `renderComProviders(ui: ReactElement, opcoes?: { rota?: string }): RenderResult` em `src/test/render.tsx`. Envolve `MantineProvider`, `QueryClientProvider` (novo `QueryClient` com `retry: false`), `MemoryRouter initialEntries={[rota ?? '/']}`.
  - Scripts npm: `dev`, `build`, `lint`, `typecheck` (`tsc -p tsconfig.app.json --noEmit`), `test` (`vitest run`).

- [ ] **Step 1: Gerar e instalar**

```bash
npm create vite@latest frontend -- --template react-ts
```
```bash
npm --prefix frontend install @mantine/core @mantine/hooks @mantine/form @mantine/notifications react-router @tanstack/react-query
```
```bash
npm --prefix frontend install -D postcss postcss-preset-mantine postcss-simple-vars vitest jsdom @testing-library/react @testing-library/user-event @testing-library/jest-dom
```

- [ ] **Step 2: Configurar**

- `vite.config.ts`:
  - `server.proxy: { '/api': 'http://localhost:8080' }`
  - `test: { environment: 'jsdom', globals: true, setupFiles: './src/test/setup.ts' }`
- `postcss.config.cjs`: `postcss-preset-mantine` e `postcss-simple-vars`, conforme o guia de instalação do Mantine para Vite.
- `src/test/setup.ts`: importa `@testing-library/jest-dom/vitest` e define polyfills de `window.matchMedia` e `ResizeObserver` (o Mantine exige ambos no jsdom).
- `eslint.config.js`: regra `no-restricted-syntax` com seletor `JSXAttribute[name.name='dangerouslySetInnerHTML']` e mensagem `"Proibido: risco de XSS"`.
- `main.tsx`: importa `@mantine/core/styles.css` e `@mantine/notifications/styles.css`; envolve `<App/>` com `MantineProvider`, `Notifications`, `QueryClientProvider` e `BrowserRouter`.
- Remova o conteúdo de exemplo do Vite.

- [ ] **Step 3: Teste de fumaça (falha)**

```tsx
test('renderiza o título do sistema', () => {
  renderComProviders(<App />);
  expect(screen.getByText('Gestão de Doações')).toBeInTheDocument();
});
```

Run (em `frontend/`): `npm test`
Expected: FAIL

- [ ] **Step 4: `App.tsx` mínimo**

Renderiza `Gestão de Doações`; será substituído pelas rotas na Task 7.

- [ ] **Step 5: Verificar tudo**

Run: `npm run lint`, `npm run typecheck`, `npm test`, `npm run build`
Expected: todos sem erro.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "chore(frontend): esqueleto React + TypeScript com Mantine, Router, Query e Vitest"
```

---

### Task 6: Cliente HTTP e API de autenticação no frontend

**Files:**
- Create: `frontend/src/api/client.ts`, `frontend/src/api/auth.ts`
- Test: `frontend/src/api/client.test.ts`, `frontend/src/api/auth.test.ts`

**Interfaces:**
- Produces (`client.ts`):
  - `type ErroCampo = { campo: string; mensagem: string }`
  - `type ProblemDetail = { status: number; title?: string; detail?: string; erros?: ErroCampo[] }`
  - `class ApiError extends Error { status: number; problem?: ProblemDetail }` (`message` = `problem.detail ?? 'Erro inesperado. Tente novamente.'`)
  - `function definirAoNaoAutenticado(fn: () => void): void`
  - `async function api<T>(caminho: string, opcoes?: { method?: 'GET'|'POST'|'PUT'|'PATCH'|'DELETE'; body?: unknown }): Promise<T>`. Retorna `undefined as T` em `204`.
- Produces (`auth.ts`):
  - `type Papel = 'ADMIN' | 'VOLUNTARIO'`
  - `type UsuarioLogado = { id: number; nome: string; email: string; papel: Papel }`
  - `obterCsrf(): Promise<void>`
  - `entrar(email: string, senha: string): Promise<UsuarioLogado>`
  - `sair(): Promise<void>`
  - `usuarioAtual(): Promise<UsuarioLogado | null>` (`401` → `null`)
  - `alterarSenha(senhaAtual: string, novaSenha: string): Promise<void>`

- [ ] **Step 1: Testes (falham)** — `fetch` mockado com `vi.stubGlobal`; cookie via `document.cookie`.

```ts
test('envia X-XSRF-TOKEN lido do cookie em métodos que alteram dados', async () => {
  document.cookie = 'XSRF-TOKEN=abc123';
  // api('/api/x', { method: 'POST', body: { a: 1 } })
  // fetch chamado com headers['X-XSRF-TOKEN'] === 'abc123', 'Content-Type' 'application/json', credentials 'same-origin'
});
test('não envia X-XSRF-TOKEN em GET', async () => {});
test('lança ApiError com problem e erros de campo', async () => {
  // resposta 400 { status: 400, detail: 'Dados inválidos', erros: [{ campo: 'email', mensagem: 'inválido' }] }
  // rejects ApiError com status 400 e problem.erros[0].campo === 'email'
});
test('401 fora de /api/auth chama o callback de não autenticado', async () => {});
test('401 em /api/auth/login, /me ou /logout não chama o callback (401 em /api/auth/minha-senha chama: sessão expirada)', async () => {});
test('204 resolve undefined', async () => {});
test('usuarioAtual devolve null em 401', async () => {});
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `npm test -- src/api`
Expected: FAIL

- [ ] **Step 3: Implementar `client.ts` e `auth.ts`**

O cabeçalho CSRF é enviado para `POST`, `PUT`, `PATCH` e `DELETE`. O cookie é lido a cada chamada, nunca guardado em cache.

- [ ] **Step 4: Rodar e ver passar**

Run: `npm test -- src/api`
Expected: PASS (7 testes)

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(frontend): cliente HTTP com CSRF, tratamento de erros e API de autenticação"
```

---

### Task 7: Sessão no React, login, rotas protegidas, layout e "Minha conta"

**Files:**
- Create: `src/auth/AuthContext.tsx`, `src/auth/RotaProtegida.tsx`, `src/auth/RotaAdmin.tsx`
- Create: `src/layout/AppShellLayout.tsx`
- Create: `src/pages/LoginPage.tsx`, `src/pages/MinhaContaPage.tsx`, `src/pages/EmConstrucaoPage.tsx`
- Modify: `src/App.tsx`
- Test: `src/auth/auth.test.tsx`, `src/pages/LoginPage.test.tsx`, `src/layout/AppShellLayout.test.tsx`, `src/pages/MinhaContaPage.test.tsx`

**Interfaces:**
- Consumes: `api/auth.ts`, `definirAoNaoAutenticado` (Task 6)
- Produces:
  - `AuthProvider`
  - `useAuth(): { usuario: UsuarioLogado | null; carregando: boolean; entrar(email: string, senha: string): Promise<void>; sair(): Promise<void>; sessaoExpirou: boolean }`
  - `<RotaProtegida/>` e `<RotaAdmin/>` (renderizam `<Outlet/>`)
  - `<EmConstrucaoPage titulo: string/>`

Rotas em `App.tsx`:
```
/login                      LoginPage (pública)
/  (RotaProtegida → AppShellLayout)
  index                     EmConstrucaoPage "Painel"
  campanhas | doacoes | doadores | relatorios   EmConstrucaoPage
  minha-conta               MinhaContaPage
  usuarios (RotaAdmin)      EmConstrucaoPage "Usuários"
*                           redireciona para /
```

- [ ] **Step 1: Testes (falham)** — `api/auth` mockado com `vi.mock`.

```tsx
// auth.test.tsx
test('sem usuário, rota protegida redireciona para /login', async () => {});
test('enquanto carrega, mostra indicador e não redireciona', () => {});
test('VOLUNTARIO em /usuarios é redirecionado para /', async () => {});
test('ADMIN acessa /usuarios', async () => {});
test('entrar renova o token CSRF depois do login', async () => {
  // ordem das chamadas: obterCsrf (montagem) → entrar → obterCsrf
});
test('401 durante o uso leva ao login com aviso de sessão expirada', async () => {
  // dispara o callback registrado em definirAoNaoAutenticado → tela de login mostra "Sua sessão expirou. Entre novamente."
});

// LoginPage.test.tsx
test('login válido navega para o painel', async () => {});
test('401 mostra "E-mail ou senha inválidos."', async () => {});
test('429 mostra a mensagem de bloqueio vinda da API', async () => {});
test('campos vazios mostram validação sem chamar a API', async () => {});

// AppShellLayout.test.tsx
test('VOLUNTARIO não vê o item "Usuários" no menu', () => {});
test('ADMIN vê Painel, Campanhas, Doações, Doadores, Relatórios e Usuários', () => {});
test('Sair chama sair() e volta ao login', async () => {});

// MinhaContaPage.test.tsx
test('nova senha com menos de 8 caracteres é bloqueada no formulário', async () => {});
test('confirmação diferente da nova senha é bloqueada', async () => {});
test('erro de campo senhaAtual da API aparece no campo', async () => {});
test('sucesso mostra "Senha alterada com sucesso." e limpa o formulário', async () => {});
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `npm test`
Expected: FAIL

- [ ] **Step 3: Implementar**

`AuthProvider`:
- Na montagem: `obterCsrf()`, depois `usuarioAtual()`.
- `entrar()`: chama a API, depois `obterCsrf()` de novo.
- Registra `definirAoNaoAutenticado`: zera o usuário, marca `sessaoExpirou`, navega para `/login`.

`LoginPage`:
- Formulário com `@mantine/form`: e-mail obrigatório e válido, senha obrigatória.
- Mostra `ApiError.message` num `Alert`.
- Se o usuário já estiver logado, redireciona para `/`.

`AppShellLayout`:
- `AppShell` do Mantine com `Burger` no celular.
- Links de navegação na ordem da spec §4.
- Nome do usuário, "Minha conta" e botão "Sair".

`MinhaContaPage`:
- Campos: senha atual, nova senha, confirmar nova senha.
- Usa `useMutation` com `alterarSenha`.
- Erros de campo vindos de `ApiError.problem.erros` vão para `form.setFieldError`.

- [ ] **Step 4: Rodar e ver passar**

Run: `npm test`, `npm run lint`, `npm run typecheck`
Expected: PASS / sem erros

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(frontend): login, sessão, rotas protegidas, layout e troca de senha"
```

---

### Task 8: CI, README e verificação ponta a ponta

**Files:**
- Create: `.github/workflows/ci.yml`, `README.md`

- [ ] **Step 1: `ci.yml`**

Gatilhos: `push` em qualquer branch e `pull_request` para `main`. Dois jobs em `ubuntu-latest`:
- **`backend`** (`working-directory: backend`):
  - `actions/setup-java` (`zulu`, `25`, `cache: maven`) → `./mvnw -B verify`.
  - `actions/upload-artifact` com `target/surefire-reports` e `target/site/jacoco` (`if: always()`).
- **`frontend`** (`working-directory: frontend`):
  - `actions/setup-node` (`26`, `cache: npm`, `cache-dependency-path: frontend/package-lock.json`).
  - Depois: `npm ci` → `npm run lint` → `npm run typecheck` → `npm test` → `npm run build`.

- [ ] **Step 2: `README.md`**

Seções:
- Pré-requisitos (Task 0).
- Configuração (`.env` a partir de `.env.example`).
- Como rodar:
  - `docker compose up -d db`
  - em `backend/`: `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`
  - em `frontend/`: `npm run dev`
  - abrir `http://localhost:5173`
- Como testar.
- Estrutura do repositório.
- Link para a spec.

- [ ] **Step 3: Verificação manual ponta a ponta**

1. Com `.env` preenchido, suba o banco, o backend e o frontend.
2. Entre com `ADMIN_EMAIL`/`ADMIN_SENHA` → o painel aparece com o menu completo, incluindo "Usuários".
3. Nas DevTools > Application > Cookies, o cookie de sessão está `HttpOnly` e `SameSite=Lax`, e o `XSRF-TOKEN` existe.
4. Em "Minha conta", troque a senha; saia; entre com a nova senha.
5. Erre a senha 5 vezes → aparece a mensagem de bloqueio.
6. Recarregue a página logado → continua logado.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "ci: pipeline com jobs de backend e frontend; README"
```

- [ ] **Step 5: GitHub (manual, quando você conectar o GitHub)**

1. Crie o repositório (privado) no GitHub.
2. `git remote add origin <url>` e `git push -u origin main`.
3. Confirme os dois jobs verdes na aba Actions.
4. Em Settings → Branches, proteja a `main`: PR obrigatório e checks obrigatórios `backend` e `frontend`.
