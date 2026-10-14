# Sistema Web para Gestão de Doações e Necessidades em Campanhas Sociais — Especificação de Design

- **Data:** 2026-10-08
- **Autora:** Gabriela Dias Medrado Rogério Nolasco
- **Contexto:** Projeto Integrador de TI III (UFMS Digital, 2026.2) — Avaliação do Módulo 4 (Plano de CI/CD)
- **Prazo de entrega:** 2026-10-25
- **Status:** aprovado em conversa; aguardando revisão do documento escrito

---

## 1. Objetivo e contexto

Aplicação web para a **Igreja Adventista da Promessa de Joinville-SC** gerenciar campanhas sociais: cadastro de campanhas e de suas necessidades, registro de doações (itens e dinheiro) e acompanhamento do que foi arrecadado e do que ainda falta.

O sistema será **usado de verdade** pela igreja e **mantido pela autora** após a disciplina. O desenvolvimento é **local primeiro**; a escolha de hospedagem e o deploy em produção ficam para o final, sem que o design dependa de um provedor específico.

### Critérios de sucesso

1. Voluntários registram doações pelo celular em poucos toques.
2. Qualquer usuário vê, por campanha, meta × arrecadado × pendente de cada necessidade e o total em R$.
3. Relatório por campanha serve para prestação de contas.
4. Todas as práticas do Plano de CI/CD estão implementadas ou com esqueleto pronto: PR + CI obrigatório, testes automáticos, análise estática, build, imagem Docker, deploy (homologação automática / produção com aprovação), backup antes do deploy, documentação automática.

### Decisões tomadas

| Tema | Decisão |
|---|---|
| Usuários do sistema | Somente equipe interna (doadores não acessam) |
| Papéis | `ADMIN` e `VOLUNTARIO` |
| Tipos de doação | Itens **e** dinheiro |
| Doador | Opcional; doações anônimas permitidas |
| Frontend | React + TypeScript (exigência da disciplina) |
| Hospedagem | Adiada; tudo empacotado em uma imagem Docker |
| Sessão | Expira após 30 min de inatividade |
| Pacote Java | `br.org.iap.doacoes` (provisório) |

---

## 2. Arquitetura

```
Navegador ──► Spring Boot (um único JAR / container)
               ├── /api/**  → @RestController (JSON) → Services → Spring Data JPA → PostgreSQL
               └── /**      → build estático do React (index.html, JS, CSS)
```

- **Monólito com SPA embutida.** O build do React é copiado para os recursos estáticos do JAR. Uma imagem, um deploy, mesma origem (sem CORS).
- **Desenvolvimento:** Vite em `:5173` com proxy de `/api` para o Spring em `:8080`; PostgreSQL via Docker Compose.
- **Fallback de rotas:** qualquer requisição GET que não seja `/api/**`, `/actuator/**`, `/swagger-ui/**`, `/v3/api-docs/**` nem arquivo estático é encaminhada para `index.html`.

### Stack

**Backend**
- Java 25 (LTS), Maven (wrapper `./mvnw`)
- Spring Boot — versão estável mais recente, confirmada no start.spring.io na criação do projeto
- Spring Web, Spring Security, Spring Data JPA, Bean Validation, Flyway, driver PostgreSQL, Actuator, springdoc-openapi
- PostgreSQL 16

**Frontend**
- Vite + React + TypeScript
- Mantine (componentes, formulários, datas, notificações)
- React Router
- TanStack Query sobre um cliente `fetch` próprio
- Vitest + React Testing Library; ESLint

**Infra / processo**
- Docker (Dockerfile em 3 estágios) e Docker Compose
- Git + GitHub (monorepo), GitHub Actions, GHCR, GitHub Pages
- Checkstyle, JaCoCo
- Postman

---

## 3. Modelo de dados

```
usuario ──< campanha (criado_por)
usuario ──< doacao   (registrado_por)
campanha ──< necessidade
campanha ──< doacao >── doador (opcional)
doacao   ──< item_doacao >── necessidade
```

### Tabelas

**`usuario`** — `id`, `nome`, `email` (único, login), `senha_hash` (BCrypt), `papel` (`ADMIN`|`VOLUNTARIO`), `ativo` (bool), `criado_em`

**`campanha`** — `id`, `titulo`, `descricao`, `data_inicio`, `data_fim` (nulo), `status` (`RASCUNHO`|`ATIVA`|`ENCERRADA`), `meta_valor` (numeric, nulo), `criado_por` (FK usuario), `criado_em`, `atualizado_em`

**`necessidade`** — `id`, `campanha_id` (FK), `item` (texto), `unidade` (`UNIDADE`|`KG`|`LITRO`|`PACOTE`|`CAIXA`|`PAR`), `quantidade_meta` (numeric > 0), `observacao`

**`doador`** — `id`, `nome`, `telefone`, `email`, `observacao`, `criado_em` (todos os dados pessoais opcionais)

**`doacao`** — `id`, `campanha_id` (FK), `doador_id` (FK, nulo = anônima), `tipo` (`ITEM`|`DINHEIRO`), `valor` (numeric, só DINHEIRO), `forma_pagamento` (`PIX`|`ESPECIE`|`OUTRO`, só DINHEIRO), `data_doacao`, `observacao`, `registrado_por` (FK usuario), `criado_em`

**`item_doacao`** — `id`, `doacao_id` (FK, cascade delete), `necessidade_id` (FK), `quantidade` (numeric > 0)

Valores monetários e quantidades usam `NUMERIC(12,2)` no banco e `BigDecimal` no Java. O esquema é criado e versionado exclusivamente pelo Flyway (`ddl-auto=validate`).

### Regras de negócio

| # | Regra |
|---|---|
| R1 | Doações só podem ser registradas em campanhas com status `ATIVA`. |
| R2 | Doação `ITEM` exige ≥ 1 item; toda `necessidade` referenciada pertence à mesma campanha da doação. |
| R3 | Doação `DINHEIRO` exige `valor > 0` e `forma_pagamento`, e não possui itens. |
| R4 | Quantidades e valores são sempre > 0. |
| R5 | Arrecadar acima da meta é permitido; `pendente = max(meta − arrecadado, 0)`; o excedente é exibido. |
| R6 | Totais nunca são armazenados: arrecadado = `SUM(item_doacao.quantidade)` por necessidade; total em R$ = `SUM(doacao.valor)` das doações `DINHEIRO` da campanha. |
| R7 | Necessidade com doações vinculadas não pode ser excluída (apenas editada). |
| R8 | `VOLUNTARIO` edita/exclui apenas doações que ele registrou; `ADMIN` edita/exclui qualquer uma. |
| R9 | `ADMIN` pode anonimizar um doador: dados pessoais apagados (nome substituído por "Anônimo"); doações preservadas. |
| R10 | Usuários não são excluídos, apenas desativados; usuário inativo não autentica. |

### Fora do escopo (v1.0)

Categorias de itens; controle de estoque/saída/distribuição; anexos e fotos; múltiplas igrejas; recuperação de senha por e-mail; 2FA; log de auditoria completo.

---

## 4. Funcionalidades e telas

Navegação: **Painel · Campanhas · Doações · Doadores · Relatórios · Usuários** (este último só para ADMIN). Layout responsivo (celular primeiro).

1. **Autenticação** — login (e-mail/senha), logout, "Minha conta" (alterar a própria senha). ADMIN redefine senha de outros usuários. Na primeira execução, um ADMIN inicial é criado a partir das variáveis `ADMIN_EMAIL` e `ADMIN_SENHA` se não existir nenhum usuário.
2. **Painel** — cards das campanhas ativas (progresso geral e R$ arrecadado/meta), lista das necessidades com maior pendência, atalho "Registrar doação".
3. **Campanhas** — lista com filtro por status; detalhe com dados, tabela de necessidades (meta / arrecadado / pendente / barra), total em R$ e últimas doações. ADMIN cria/edita campanha, altera status e gerencia necessidades no detalhe.
4. **Doações** — registrar (campanha ativa → tipo → itens [necessidade + quantidade, "+ item"] ou valor + forma de pagamento → doador existente / novo / anônimo → data, padrão hoje); lista com filtros (campanha, período, tipo); editar/excluir conforme R8.
5. **Doadores** — lista com busca por nome/telefone; cadastrar/editar; histórico de doações; ADMIN anonimiza.
6. **Relatórios** — em tela, exportáveis em CSV e imprimíveis (CSS de impressão do navegador):
   - Por campanha (necessidades meta/arrecadado/pendente + total R$)
   - Doações por período (filtros por datas, campanha, tipo; com totais)
   - Lista de pendências (texto pronto para WhatsApp)
7. **Usuários (ADMIN)** — listar, criar, editar, ativar/desativar, definir papel, redefinir senha.

**Percentual de progresso geral de uma campanha:** média, entre suas necessidades, de `min(arrecadado / meta, 1)`. Campanha sem necessidades não exibe barra de itens.

---

## 5. API REST

Todos os endpoints sob `/api`, JSON, autenticados (exceto login). DTOs são `record`s (`*Request` / `*Response`); entidades JPA nunca são expostas nem recebidas diretamente.

```
POST   /api/auth/login              POST /api/auth/logout          GET /api/auth/me
PUT    /api/auth/minha-senha

GET    /api/campanhas?status=       POST /api/campanhas            (ADMIN)
GET    /api/campanhas/{id}          PUT  /api/campanhas/{id}       (ADMIN)
PATCH  /api/campanhas/{id}/status   (ADMIN)
GET    /api/campanhas/{id}/necessidades
POST   /api/campanhas/{id}/necessidades   (ADMIN)
PUT    /api/necessidades/{id}       DELETE /api/necessidades/{id}  (ADMIN; R7)
GET    /api/campanhas/{id}/progresso
GET    /api/painel

GET    /api/doacoes?campanhaId=&de=&ate=&tipo=   POST /api/doacoes
GET    /api/doacoes/{id}            PUT  /api/doacoes/{id}         DELETE /api/doacoes/{id}  (R8)

GET    /api/doadores?busca=         POST /api/doadores
GET    /api/doadores/{id}           PUT  /api/doadores/{id}
GET    /api/doadores/{id}/doacoes
POST   /api/doadores/{id}/anonimizar   (ADMIN)

GET    /api/usuarios                POST /api/usuarios             (ADMIN)
PUT    /api/usuarios/{id}           POST /api/usuarios/{id}/redefinir-senha   (ADMIN)

GET    /api/relatorios/campanha/{id}         (?formato=csv)
GET    /api/relatorios/doacoes?de=&ate=&campanhaId=&tipo=   (?formato=csv)
GET    /api/relatorios/pendencias            (text/plain)
```

- **Paginação** em listagens: `?page=&size=&sort=` (padrão `size=20`).
- **Erros:** `ProblemDetail` (RFC 9457) com campo `erros: [{campo, mensagem}]` para validação. Códigos: `400` validação, `401` não autenticado, `403` sem permissão, `404` não encontrado, `409` violação de regra de negócio (ex.: R1, R7).
- **Documentação:** OpenAPI gerado pelo springdoc; Swagger UI em `/swagger-ui` (apenas perfil `dev`; o JSON da especificação é publicado no GitHub Pages a cada release).
- **Postman:** coleção versionada em `postman/` cobrindo login, CRUD de campanhas/necessidades, registro de doação e progresso.

---

## 6. Segurança

**Autenticação**
- Spring Security com sessão no servidor; login via `POST /api/auth/login` (JSON).
- Cookie de sessão `HttpOnly`, `SameSite=Lax`; `Secure` no perfil `prod`. Timeout de 30 min de inatividade.
- Senhas com BCrypt; mínimo 8 caracteres.
- Bloqueio de 15 min após 5 tentativas falhas consecutivas (controle em memória — suficiente para instância única).
- Mensagem de erro de login genérica.

**Autorização** — aplicada em duas camadas: regras por rota/método no `SecurityConfig` e verificações no service (R8). O frontend esconde ações não permitidas, mas não é a proteção.

| Ação | VOLUNTARIO | ADMIN |
|---|---|---|
| Ver painel, campanhas, relatórios | ✅ | ✅ |
| Registrar doação; cadastrar/editar doador | ✅ | ✅ |
| Editar/excluir as próprias doações | ✅ | ✅ |
| Editar/excluir qualquer doação | ❌ | ✅ |
| Criar/editar campanhas e necessidades | ❌ | ✅ |
| Anonimizar doador | ❌ | ✅ |
| Gerenciar usuários | ❌ | ✅ |

**Proteções web**
- CSRF ativo: token em cookie `XSRF-TOKEN` (legível por JS), enviado pelo cliente no cabeçalho `X-XSRF-TOKEN`.
- `401` em `/api/**` sem sessão (sem redirect HTML); o frontend redireciona para `/login`.
- XSS: React escapa saída; ESLint proíbe `dangerouslySetInnerHTML`.
- SQL injection: apenas JPA/consultas parametrizadas.
- Validação no servidor com Bean Validation (obrigatória), repetida no frontend (experiência do usuário).
- Cabeçalhos de segurança padrão do Spring Security.

**Segredos e perfis**
- Configuração via variáveis de ambiente; `.env` local no `.gitignore`; `.env.example` versionado com valores fictícios; segredos de CI/CD em GitHub Secrets.
- Perfis: `dev` (SQL no log, Swagger UI), `test` (Testcontainers), `prod` (logs enxutos, cookie `Secure`, Swagger UI desligado).

**LGPD** — dados de doadores apenas para usuários autenticados; coleta mínima e opcional; anonimização (R9).

**HTTPS** — responsabilidade do proxy/plataforma de hospedagem no deploy.

---

## 7. Estrutura do repositório

```
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/java/br/org/iap/doacoes/
│       │   ├── DoacoesApplication.java
│       │   ├── config/      SecurityConfig, OpenApiConfig, SpaForwardController, AdminInicialRunner
│       │   ├── comum/       BaseEntity, exceções de negócio, GlobalExceptionHandler, CsvWriter
│       │   ├── usuario/     entidade, Papel, repositório, service, controller, DTOs
│       │   ├── campanha/    Campanha, StatusCampanha, Necessidade, Unidade, repositórios, service, controller, DTOs
│       │   ├── doador/      entidade, repositório, service, controller, DTOs
│       │   ├── doacao/      Doacao, ItemDoacao, TipoDoacao, FormaPagamento, repositórios, service, controller, DTOs
│       │   ├── progresso/   ProgressoService (consultas agregadas), PainelController, DTOs
│       │   ├── relatorio/   RelatorioService, RelatorioController
│       │   └── auth/        AuthController, controle de tentativas de login
│       ├── main/resources/
│       │   ├── db/migration/   V1__esquema_inicial.sql, ...
│       │   └── application.yml, application-dev.yml, application-prod.yml
│       └── test/java/...       espelha os pacotes
├── frontend/
│   ├── package.json, vite.config.ts (proxy /api → :8080), tsconfig.json, eslint config
│   └── src/
│       ├── api/          cliente HTTP (CSRF, 401), tipos TS, hooks por recurso (useCampanhas, ...)
│       ├── auth/         AuthContext, RotaProtegida, RotaAdmin
│       ├── layout/       AppShell, navegação
│       ├── pages/        login, painel, campanhas, doacoes, doadores, relatorios, usuarios, minha-conta
│       └── components/   BarraProgresso, TabelaNecessidades, FormDoacao, ...
├── docker-compose.yml     postgres:16 (+ volume) e app
├── Dockerfile             estágios: node (build React) → maven (build JAR com o React embutido) → JRE 25 (usuário não-root)
├── .env.example
├── .github/workflows/     ci.yml, release.yml, deploy.yml
├── scripts/backup.sh      pg_dump com data/hora no nome do arquivo
├── config/checkstyle.xml
├── postman/
├── docs/planejamento/especificacao/
└── README.md              como rodar, variáveis de ambiente, backup/restauração
```

**Princípios:** pacotes por funcionalidade; controllers finos (sem acesso direto a repositórios); regras de negócio nos services; o cálculo de progresso concentrado em `progresso/` e reutilizado por painel, detalhe, relatórios e API.

**Fluxo Git:** `main` protegida (merge só por PR com CI verde); branches `feature/<nome>`; Conventional Commits (`feat:`, `fix:`, `test:`, `docs:`, `ci:`) para gerar changelog.

**Execução local:**
- `docker compose up -d db` + `./mvnw spring-boot:run` (em `backend/`) + `npm run dev` (em `frontend/`)
- ou `docker compose up --build` para rodar a imagem completa, como em produção

---

## 8. Testes

| Camada | Ferramentas | Escopo |
|---|---|---|
| Unidade (backend) | JUnit 5, Mockito, AssertJ | Regras R1–R10, cálculo de pendente/excedente/percentual, autorização R8 — escritas com TDD |
| Persistência | `@DataJpaTest` + Testcontainers (PostgreSQL 16) | Consultas agregadas de progresso; migrations Flyway do zero |
| Web/segurança | `@WebMvcTest` + MockMvc + spring-security-test | Status HTTP, `ProblemDetail`, 401/403 por papel, CSRF |
| Fluxo completo | `@SpringBootTest` + Testcontainers | Criar campanha → registrar doação → conferir progresso |
| Frontend | Vitest + React Testing Library | FormDoacao (itens, item×dinheiro, erros da API), proteção de rotas, exibição de progresso |
| Estático | Checkstyle; ESLint + `tsc --noEmit` | Padrões de código e tipos |
| API manual | Postman | Fluxos principais da API |

**Meta de cobertura:** JaCoCo ≥ 70% de linhas nos pacotes de service (o build falha abaixo disso). Sem meta para controllers e frontend.

---

## 9. CI/CD (GitHub Actions)

**`ci.yml`** — em push e pull request; dois jobs paralelos, ambos obrigatórios no branch protection:
- `backend`: Java 25 (Zulu) com cache Maven → `./mvnw verify` (testes, Checkstyle, JaCoCo) → artefatos de relatórios.
- `frontend`: Node 26 com cache npm → `npm ci` → lint → `tsc --noEmit` → `vitest run` → `npm run build`.

**`release.yml`** — em tag `v*`:
- Repete as verificações; build da imagem Docker (3 estágios) e push para o GHCR com tags `vX.Y.Z` e `latest`.
- GitHub Release com changelog gerado a partir dos commits.
- Publicação no GitHub Pages: especificação OpenAPI (renderizada) e Javadoc.

**`deploy.yml`** — preparado, disparo manual (`workflow_dispatch`) até a hospedagem ser definida:
- Job `homologacao`: SSH no servidor → `docker compose pull && docker compose up -d`.
- Job `producao`: GitHub Environment `production` com aprovação obrigatória → `scripts/backup.sh` (pg_dump) → deploy da mesma versão validada em homologação.
- Segredos: `DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_KEY`.

**Monitoramento** — indicadores do Plano (taxa de sucesso dos testes, falhas, tempo de pipeline, frequência de implantações) acompanhados pelos relatórios do GitHub Actions; health check em `/actuator/health`.

---

## 10. Cronograma

| Datas | Entrega |
|---|---|
| 08–09/10 | Monorepo, Compose, Flyway V1, CI com os dois jobs verdes, esqueleto React (Vite + Mantine + Router), **login ponta a ponta** (API de auth, tela de login, rota protegida, ADMIN inicial) |
| 10–11/10 | API de Usuários, Campanhas e Necessidades, com testes |
| 12–13/10 | Telas de Campanhas (lista, detalhe, formulário), Necessidades e Usuários |
| 14–15/10 | API de Doadores e **Doações** (regras via TDD) |
| 16–17/10 | Telas de Registrar doação, Lista de doações e Doadores |
| 18–19/10 | Progresso e Painel (backend e frontend) |
| 20–21/10 | Relatórios (tela + CSV), texto de pendências, coleção Postman |
| 22/10 | Dockerfile 3 estágios, `release.yml`, `deploy.yml`, `backup.sh`, README |
| 23–25/10 | Margem: teste com usuário da igreja, correções, tag `v1.0.0` |

**Ordem de corte se atrasar:** (1) texto de pendências para WhatsApp → (2) relatório de doações por período → (3) exportação CSV (mantém impressão) → (4) testes de frontend além do FormDoacao.

**Inegociáveis na v1.0:** login e papéis; campanhas e necessidades; registro de doações; progresso e painel; API documentada; CI verde.
