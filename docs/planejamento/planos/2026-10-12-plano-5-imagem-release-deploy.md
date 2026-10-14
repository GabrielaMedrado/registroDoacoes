# Plano 5 — Imagem Docker, Release, Deploy, Backup e README — Implementation Plan

**Goal:** Uma imagem Docker única (React embutido no JAR, usuário não-root) publicada no GHCR a cada tag `v*`, com GitHub Release, changelog e documentação da API no GitHub Pages; deploy manual em homologação e produção (com aprovação e backup antes); README para quem vai manter.

**Architecture:** O Spring serve o build do Vite a partir de `classpath:/static/`, com um resolvedor que devolve `index.html` para rotas do React (spec §3 "Monólito com SPA embutida"). O `Dockerfile` tem 3 estágios (node → maven → JRE). Os workflows ficam finos: a lógica de servidor mora em scripts de shell testáveis (`backup.sh`, `implantar.sh`, `verificar-implantacao.sh`, `changelog.sh`), executados no servidor via SSH ou no runner. O `ci.yml` vira reutilizável e a release o chama em vez de repetir passos.

**Tech Stack:** igual aos Planos 1–4, mais Docker BuildKit, GitHub Actions (`docker/*`, `actions/upload-pages-artifact`, `actions/deploy-pages`), `gh` CLI (já no runner), Swagger UI via CDN na página do Pages, ShellCheck e actionlint rodando por imagem Docker (sem instalar nada na máquina).

**Spec:** `docs/planejamento/especificacao/2026-10-08-gestao-doacoes-design.md` (§1 item 4; §2 hospedagem; §3 monólito com SPA; §5 documentação; §6 segredos, perfis, HTTPS; §7 estrutura; §9 CI/CD; §10 linha 22/10)

**Base:** branch `feature/plano-5` criada a partir de `feature/plano-3` (que já contém o Plano 4).

## Global Constraints

- Tudo dos Planos 1–4 continua valendo (Checkstyle, JaCoCo ≥ 70% nos `*Service`, oxlint `--deny-warnings`, textos pt-BR, Conventional Commits).
- Java 25, Node 26, PostgreSQL 16 (`postgres:16-alpine`, já no Compose).
- Imagem: `ghcr.io/gabrielamedrado/registrodoacoes` (minúsculas, exigência do GHCR); tags `vX.Y.Z` e `latest` (spec §9).
- Dockerfile em 3 estágios: node (build React) → maven (JAR com o React embutido) → JRE 25, **usuário não-root** (spec §7).
- Perfil da imagem: `prod` (cookie `Secure`, sem Swagger UI nem `/v3/api-docs`).
- HTTPS fica com o proxy do servidor (spec §6); a aplicação precisa reconhecer `X-Forwarded-Proto` de proxy interno.
- Health check: `GET /actuator/health` → `{"status":"UP"}` (spec §9).
- `deploy.yml`: só `workflow_dispatch`; job `homologacao` → job `producao` (GitHub Environment `production`, aprovação obrigatória, `backup.sh` antes); mesma versão nos dois (spec §9).
- Segredos do deploy: `DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_KEY` (spec §9), mais `DEPLOY_KNOWN_HOSTS` (ver Ruling no fim do plano) — como segredos **de Environment** (`homologacao` e `production`), com os mesmos nomes nos dois.
- No servidor, tudo fica em `~/registro-doacoes/` (`docker-compose.yml`, `scripts/`, `.env`, `backups/`).
- Backup: `pg_dump` em formato custom, arquivo `backups/doacoes-AAAAMMDD-HHMMSS.dump` (horário de Joinville); mantém os 30 mais recentes (`BACKUP_MANTER`).
- Changelog: seções `### Novidades` (`feat`), `### Correções` (`fix`), `### Outras mudanças` (resto); seção vazia não aparece; linha `- **escopo:** descrição (abc1234)` ou `- descrição (abc1234)`.
- Mensagens dos scripts (usar exatamente):
  - `Versão inválida: <valor>. Use o formato vX.Y.Z.` (saída 2)
  - `Backup falhou; nenhum arquivo foi gravado.` (saída 1)
  - `Backup gravado em <caminho>`
  - `A aplicação não ficou saudável em <N>s.` (saída 1)
  - `Implantação verificada: <url>`

## Review Focus

1. **Recarregar a página numa rota do React em produção** (F5 em `/campanhas/5`) → o app abre. Já `/api/qualquer-coisa` e `/assets/arquivo-antigo.js` → 404, nunca o HTML do React → testes na Task 1.
2. **Backup que falha** (banco parado, `pg_dump` com erro) → o deploy de produção para antes de trocar a versão, e não sobra um `.dump` truncado com cara de válido → testes na Task 3.
3. **Versão digitada errado ou maliciosa no disparo do deploy** (`latest`, `v1.0`, `v1.0.0; rm -rf ~`) → recusada antes de qualquer comando no servidor → testes na Task 3 e validação no workflow (Task 5).
4. **`.env` do servidor sem `DB_PASSWORD`** → o Compose falha na hora com mensagem clara, em vez de subir o banco sem senha → verificação na Task 2.
5. **Versão nova que não sobe** (migration quebrada, app sem ficar `UP`) → o job de deploy falha, não aparece verde → testes na Task 3.

---

### Task 1: SPA servida pelo Spring

**Files:**
- Create: `backend/src/main/java/br/org/iap/doacoes/config/SpaConfig.java`
- Create: `backend/src/test/resources/static/index.html`, `backend/src/test/resources/static/assets/app.js`
- Test: `backend/src/test/java/br/org/iap/doacoes/config/SpaConfigTest.java`
- Modify: `.gitignore` (acrescentar `backend/src/main/resources/static/`)

**Interfaces:**
- Produces: o Spring serve `classpath:/static/**`; qualquer GET que não for arquivo existente, não tiver `.` no último segmento e não começar com `api/`, `actuator/`, `v3/` ou `swagger-ui` recebe `index.html` (status 200, `text/html`). O Dockerfile (Task 2) copia `frontend/dist` para `backend/src/main/resources/static/`.

- [ ] **Step 1: Escrever os testes** (`SpaConfigTest extends ApiTestSupport`; fixtures: `index.html` contendo `<div id="root"></div>`, `assets/app.js` contendo `console.log('ok')`)

```java
@Test void raizServeOIndex()              // GET /            → 200, content-type text/html, corpo contém id="root"
@Test void rotaDoReactServeOIndex()       // GET /campanhas/5 → 200, text/html, id="root"
@Test void rotaAninhadaServeOIndex()      // GET /doacoes/3/editar → 200, id="root"
@Test void arquivoExistenteEServido()     // GET /assets/app.js → 200, content-type contém javascript, corpo console.log('ok')
@Test void arquivoInexistenteDa404()      // GET /assets/antigo-123.js → 404
@Test void apiInexistenteNaoServeOIndex() // logado (VOLUNTARIO): GET /api/nao-existe → 404 e corpo NÃO contém id="root"
@Test void apiSemSessaoContinua401()      // GET /api/painel sem sessão → 401
@Test void actuatorNaoExpostoNaoServeOIndex() // GET /actuator/env → 404 e corpo sem id="root"
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `cd backend && ./mvnw -q test -Dtest=SpaConfigTest`
Expected: FAIL nos três testes de rota do React (404). `raizServeOIndex` e `arquivoExistenteEServido` podem já passar (o Boot serve `static/` sozinho), o que é esperado.

- [ ] **Step 3: Implementar `SpaConfig implements WebMvcConfigurer`**

`addResourceHandlers`: `registry.addResourceHandler("/**").addResourceLocations("classpath:/static/").resourceChain(true).addResolver(new PathResourceResolver() { … })`. `getResource(caminho, local)`: devolve o arquivo quando ele existe e é legível. Caso contrário, devolve `null` se o caminho tiver `.` no último segmento ou começar com um dos prefixos reservados (`api/`, `actuator/`, `v3/`, `swagger-ui`). Senão, devolve `local.createRelative("index.html")`, ou `null` se esse arquivo não existir (backend rodando sem frontend embutido, como no `spring-boot:run` do dev). Escreva um Javadoc curto explicando por que existe (o React Router cuida das rotas no navegador).

- [ ] **Step 4: Rodar e ver passar**

Run: `cd backend && ./mvnw -q test -Dtest=SpaConfigTest`
Expected: PASS 8/8.

- [ ] **Step 5: Suíte completa**

Run: `cd backend && ./mvnw -B verify > /tmp/verify.log 2>&1; tail -30 /tmp/verify.log`
Expected: `BUILD SUCCESS`, 186 testes. Os do Swagger (`OpenApiTest`, `OpenApiProdTest`) continuam verdes: `/swagger-ui/index.html` fora do dev segue 404, porque `swagger-ui` é prefixo reservado.

- [ ] **Step 6: Commit**

```bash
git add .gitignore backend/src/main/java/br/org/iap/doacoes/config/SpaConfig.java backend/src/test
git commit -m "feat: Spring serve o React embutido e devolve index.html nas rotas do navegador"
```

---

### Task 2: Dockerfile, Compose com a aplicação e verificação da implantação

**Files:**
- Create: `scripts/verificar-implantacao.sh`
- Create: `Dockerfile`, `.dockerignore`
- Modify: `docker-compose.yml`, `backend/src/main/resources/application-prod.yml`, `backend/pom.xml`

**Interfaces:**
- Consumes: SPA da Task 1.
- Produces:
  - `scripts/verificar-implantacao.sh [URL]` (padrão `http://127.0.0.1:8080`; `ESPERA_SEGUNDOS`, padrão 90). Espera o health `UP`; se não ficar, imprime `A aplicação não ficou saudável em <N>s.` e sai com 1. Depois confere: `/` é HTML com `id="root"`; `/campanhas/1` é HTML; `/api/painel` sem sessão dá 401; `/api/auth/csrf` com `X-Forwarded-Proto: https` devolve o cookie `XSRF-TOKEN` com `Secure`. Termina com `Implantação verificada: <url>` e saída 0; qualquer falha diz qual verificação foi e sai com 1. Usado pelas Tasks 3 e 4.
  - Serviço `app` no Compose: `image: ghcr.io/gabrielamedrado/registrodoacoes:${APP_VERSAO:-latest}`, `build: .`.
  - Build arg `VERSAO` no Dockerfile → `-Drevision=${VERSAO}`.
  - `pom.xml`: `<version>${revision}</version>` com a propriedade `<revision>0.0.1-SNAPSHOT</revision>`.

- [ ] **Step 1: Escrever `scripts/verificar-implantacao.sh`** (`#!/usr/bin/env bash`, `set -euo pipefail`, só `curl`; executável: `git update-index --chmod=+x` no commit)

- [ ] **Step 2: Ver falhar sem a imagem**

Run: `ESPERA_SEGUNDOS=5 bash scripts/verificar-implantacao.sh; echo "saida=$?"`
Expected: `A aplicação não ficou saudável em 5s.` e `saida=1`.

- [ ] **Step 3: Implementar a imagem e a configuração**
  - `pom.xml`: versão `${revision}` (Maven CI-friendly; o JAR não é instalado em repositório, então não precisa do flatten). O `build-info` passa a refletir a versão da release.
  - `application-prod.yml`: `server.forward-headers-strategy: native` (o `RemoteIpValve` do Tomcat só confia em proxies de rede interna, então um cliente de fora não consegue forjar o cabeçalho).
  - `Dockerfile` (BuildKit, `# syntax=docker/dockerfile:1`):
    1. `node:26-alpine AS frontend`: `npm ci` com cache montado em `/root/.npm` e `npm run build`.
    2. `eclipse-temurin:25-jdk AS backend`: copia `backend/`, `config/` (o Checkstyle lê `../config`) e o `dist` do estágio 1 para `src/main/resources/static/`. Roda `./mvnw -B package -DskipTests -Drevision=${VERSAO}` com cache montado em `/root/.m2`. Os testes rodam no CI, que tem o Testcontainers; o build da imagem não tem Docker.
    3. `eclipse-temurin:25-jre-alpine`: usuário `app` (uid 10001) sem shell, `COPY --from=backend` do JAR como `/app/app.jar`, `USER app`, `EXPOSE 8080`, `ENV SPRING_PROFILES_ACTIVE=prod JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"`, `HEALTHCHECK` com `wget -qO- http://127.0.0.1:8080/actuator/health | grep -q UP`, `ENTRYPOINT ["java","-jar","/app/app.jar"]`.
    4. `ARG VERSAO=0.0.0-dev`.
  - `.dockerignore`: `**/node_modules`, `frontend/dist`, `backend/target`, `.env`, `.git`, `backups`, `postman`, `docs`, `*.pdf`.
  - `docker-compose.yml`:
    - Serviço `app`: `depends_on: db: condition: service_healthy`, `restart: unless-stopped`, `ports: ["127.0.0.1:8080:8080"]`.
    - Variáveis do `app`: `DB_URL: jdbc:postgresql://db:5432/doacoes`, `DB_USER: ${DB_USER:?defina DB_USER no .env}`, `DB_PASSWORD: ${DB_PASSWORD:?defina DB_PASSWORD no .env}`, `ADMIN_EMAIL: ${ADMIN_EMAIL:-}`, `ADMIN_SENHA: ${ADMIN_SENHA:-}`.
    - Serviço `db`: porta `127.0.0.1:5432:5432` (o banco não fica exposto na rede do servidor), `restart: unless-stopped` e os mesmos `:?` nas variáveis.

- [ ] **Step 4: Ver passar com a imagem real**

Run:
```bash
docker compose build --build-arg VERSAO=0.0.0-local app
docker compose up -d
bash scripts/verificar-implantacao.sh
docker compose exec app id -u
```
Expected: `Implantação verificada: http://127.0.0.1:8080` e `10001`. O `.env` local já define as variáveis. Se a tag `25-jre-alpine` não existir no Docker Hub, use `eclipse-temurin:25-jre` com `curl` no `HEALTHCHECK` e registre um Ruling.

- [ ] **Step 5: Review Focus 4: falta de senha falha cedo**

Run: `DB_PASSWORD= docker compose --env-file /dev/null config > /dev/null; echo "saida=$?"`
Expected: mensagem `defina DB_PASSWORD no .env` (ou `DB_USER`) e `saida` diferente de 0.

- [ ] **Step 6: Conferir no navegador** (pane embutido, `http://localhost:8080`)

Entre com o admin do `.env`. Navegue até o detalhe de uma campanha, aperte F5 e confira que a página volta igual. Depois rode `docker compose stop app`; `docker compose up -d db` continua servindo o desenvolvimento normal.

- [ ] **Step 7: Suíte backend** (o `pom.xml` mudou)

Run: `cd backend && ./mvnw -B verify > /tmp/verify.log 2>&1; tail -5 /tmp/verify.log`
Expected: `BUILD SUCCESS`.

- [ ] **Step 8: Commit**

```bash
git add Dockerfile .dockerignore docker-compose.yml scripts/verificar-implantacao.sh backend/pom.xml backend/src/main/resources/application-prod.yml
git commit -m "build: imagem Docker em 3 estágios com o React embutido e serviço app no Compose"
```

---

### Task 3: Backup e implantação no servidor

**Files:**
- Create: `scripts/backup.sh`, `scripts/implantar.sh`
- Test: `scripts/testes/backup.test.sh`, `scripts/testes/implantar.test.sh`, `scripts/testes/lib.sh` (asserts e stubs)
- Modify: `.github/workflows/ci.yml` (novo job `scripts`)

**Interfaces:**
- Consumes: `scripts/verificar-implantacao.sh` (Task 2).
- Produces:
  - `scripts/backup.sh`. Roda da raiz do projeto (`cd "$(dirname "$0")/.."`). Executa `docker compose exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc'` gravando em `backups/doacoes-AAAAMMDD-HHMMSS.dump.parcial`; se der certo e o arquivo não estiver vazio, faz `mv` para `.dump`. Variáveis: `BACKUP_DIR` (padrão `backups`) e `BACKUP_MANTER` (padrão 30), que apaga os `.dump` mais antigos além desse número. Em qualquer falha, apaga o `.parcial`, imprime `Backup falhou; nenhum arquivo foi gravado.` e sai com 1. Usado pelo deploy (Task 5).
  - `scripts/implantar.sh vX.Y.Z`. Valida `^v[0-9]+\.[0-9]+\.[0-9]+$`; fora disso, imprime `Versão inválida: … Use o formato vX.Y.Z.` e sai com 2, sem chamar `docker`. Em seguida faz `export APP_VERSAO`, `docker compose pull app`, `docker compose up -d app` e `scripts/verificar-implantacao.sh "${APP_URL:-http://127.0.0.1:8080}"`; a saída é a do verificador. Usado pelo deploy (Task 5).

- [ ] **Step 1: Escrever os testes**

`lib.sh` cria um diretório temporário com um `docker` falso no `PATH`, configurado por variáveis: `DOCKER_FALHA=1` faz ele sair com 1, `DOCKER_SAIDA` é o que ele escreve no stdout, e cada chamada é registrada em `$TMP/docker.log`. Também traz `afirmar_igual`, `afirmar_contem` e `afirmar_arquivo_inexistente`. Cada teste roda o script numa cópia do projeto em `$TMP` (copiando `scripts/` e um `docker-compose.yml` vazio), para não tocar o `backups/` real.

`backup.test.sh`:
- `grava_dump_com_data_no_nome`: `DOCKER_SAIDA=PGDMP…` → existe exatamente um `backups/doacoes-[0-9]{8}-[0-9]{6}.dump` com esse conteúdo; não sobra `.parcial`; a saída contém `Backup gravado em`.
- `falha_do_pg_dump_nao_deixa_arquivo`: `DOCKER_FALHA=1` → saída 1, mensagem `Backup falhou; nenhum arquivo foi gravado.`, nenhum arquivo em `backups/`.
- `dump_vazio_e_falha`: `DOCKER_SAIDA=` (vazio) → saída 1, nenhum arquivo.
- `mantem_so_os_mais_recentes`: 3 dumps antigos já existentes (`doacoes-20260101-000000.dump` …) e `BACKUP_MANTER=2` → sobram 2 arquivos, sendo um deles o novo; o mais antigo foi apagado.

`implantar.test.sh` (na cópia do projeto, `scripts/verificar-implantacao.sh` é trocado por um falso que registra a URL recebida e sai com 1 quando `VERIFICACAO_FALHA=1`, imprimindo `A aplicação não ficou saudável em 1s.`):
- `versao_invalida_e_recusada`: para `latest`, `v1.0`, `1.0.0` e `'v1.0.0; rm -rf ~'` → saída 2, mensagem `Versão inválida`, `docker.log` vazio.
- `implanta_a_versao_pedida`: `v1.2.3` → saída 0; `docker.log` contém `compose pull app` antes de `compose up -d app`; o `docker` falso registra `APP_VERSAO=v1.2.3` do ambiente; o verificador falso recebeu `http://127.0.0.1:8080`.
- `app_que_nao_sobe_falha`: `VERIFICACAO_FALHA=1` → saída 1, mensagem `A aplicação não ficou saudável`.
- `pull_que_falha_para_antes_do_up`: `DOCKER_FALHA=1` → saída diferente de 0, `docker.log` sem `up -d`.

- [ ] **Step 2: Ver falhar**

Run: `bash scripts/testes/backup.test.sh; bash scripts/testes/implantar.test.sh`
Expected: FAIL (scripts inexistentes).

- [ ] **Step 3: Implementar `scripts/backup.sh` e `scripts/implantar.sh`** (`set -euo pipefail`; data por `TZ=America/Sao_Paulo date +%Y%m%d-%H%M%S`; `trap` apagando o `.parcial`; executáveis)

- [ ] **Step 4: Ver passar**

Run: `bash scripts/testes/backup.test.sh && bash scripts/testes/implantar.test.sh`
Expected: todos `ok`, saída 0.

- [ ] **Step 5: ShellCheck**

Run: `docker run --rm -v "$PWD:/mnt" -w /mnt koalaman/shellcheck:v0.10.0 scripts/*.sh scripts/testes/*.sh`
Expected: sem avisos.

- [ ] **Step 6: Backup e restauração de verdade** (banco local do Compose)

Run:
```bash
bash scripts/backup.sh
docker compose exec -T db sh -c 'createdb -U "$POSTGRES_USER" doacoes_restauro'
docker compose exec -T db sh -c 'pg_restore -U "$POSTGRES_USER" -d doacoes_restauro' < backups/<arquivo>.dump
docker compose exec -T db sh -c 'psql -U "$POSTGRES_USER" -d doacoes_restauro -tAc "select count(*) from campanha"'
docker compose exec -T db sh -c 'dropdb -U "$POSTGRES_USER" doacoes_restauro'
```
Expected: a mesma contagem de campanhas do banco `doacoes`. Os comandos de restauração vão para o README (Task 6).

- [ ] **Step 7: Job `scripts` no `ci.yml`**: `ubuntu-latest`, checkout, ShellCheck pela mesma imagem pinada e os dois `*.test.sh`.

- [ ] **Step 8: Commit**

```bash
git add scripts .github/workflows/ci.yml
git commit -m "feat: scripts de backup (pg_dump) e implantação com verificação de saúde"
```

---

### Task 4: Release: imagem no GHCR, GitHub Release com changelog e Pages

**Files:**
- Create: `scripts/changelog.sh`, `scripts/testes/changelog.test.sh`
- Create: `.github/workflows/release.yml`
- Create: `docs/pages/index.html`, `docs/pages/api/index.html`
- Modify: `.github/workflows/ci.yml`

**Interfaces:**
- Consumes: Dockerfile (`VERSAO`), `verificar-implantacao.sh`, `pom.xml` com `${revision}`, `OpenApiTest` gravando `backend/target/openapi.json`.
- Produces:
  - `scripts/changelog.sh <tag>`. Lista os commits sem merge desde a tag anterior (`git describe --tags --abbrev=0 <tag>^`), ou desde o começo do histórico se não houver tag anterior, até `<tag>`. A saída é Markdown no formato das Global Constraints.
  - `ci.yml` aceita `workflow_call` com input `versao` (string, padrão `''`). O job backend roda `./mvnw -B verify ${VERSAO:+-Drevision=$VERSAO}` e publica o artefato `openapi` (`backend/target/openapi.json`). O `push` passa a ser só em branches (`branches: ['**']`), para a tag não disparar o CI duas vezes.

- [ ] **Step 1: Escrever `changelog.test.sh`** (cria um repositório git temporário com commits e tags)
  - `agrupa_por_tipo`: commits `feat(relatorios): copiar pendências`, `fix: CSV com tab`, `docs: README`, `Ajuste solto` e tag `v1.0.0` → saída contém `### Novidades` seguido de `- **relatorios:** copiar pendências (`, `### Correções` com `- CSV com tab (`, e `### Outras mudanças` com `- README (` e `- Ajuste solto (`.
  - `so_desde_a_tag_anterior`: depois de `v1.0.0`, um commit `feat: painel` e tag `v1.1.0` → `changelog.sh v1.1.0` contém `painel` e não contém `copiar pendências`.
  - `secao_vazia_nao_aparece`: só commits `fix` → nem `### Novidades` nem `### Outras mudanças` aparecem.
  - `ignora_merge`: um commit de merge → a mensagem `Merge branch` não aparece.

- [ ] **Step 2: Ver falhar** — Run: `bash scripts/testes/changelog.test.sh` · Expected: FAIL.

- [ ] **Step 3: Implementar `scripts/changelog.sh`**: `git log --no-merges --format='%h%x09%s' <intervalo>` seguido de um `case` com expressão regular `^(feat|fix|[a-z]+)(\(([^)]+)\))?!?: (.*)$`.

- [ ] **Step 4: Ver passar + ShellCheck** — Run: `bash scripts/testes/changelog.test.sh` e o ShellCheck da Task 3 · Expected: ok. Acrescente o teste ao job `scripts` do CI.

- [ ] **Step 5: Versão no OpenAPI**

Run: `cd backend && ./mvnw -q verify -Drevision=9.9.9 -Dtest=OpenApiTest -Djacoco.skip=true && grep -o '"version":"9.9.9"' target/openapi.json`
Expected: `"version":"9.9.9"`.

- [ ] **Step 6: Javadoc gera**

Run: `cd backend && ./mvnw -q javadoc:javadoc -Ddoclint=none && ls target/reports/apidocs/index.html`
Expected: o arquivo existe. Se o plugin gravar em outro diretório, ajuste o caminho no workflow e registre um Ruling.

- [ ] **Step 7: Páginas do Pages**
  - `docs/pages/index.html`: título "Gestão de Doações — documentação", com links para `api/` ("Especificação da API (OpenAPI)") e `javadoc/` ("Javadoc do backend").
  - `docs/pages/api/index.html`: Swagger UI do CDN `https://cdn.jsdelivr.net/npm/swagger-ui-dist@5.17.14/` (`swagger-ui.css` + `swagger-ui-bundle.js`), com `url: 'openapi.json'` e `supportedSubmitMethods: []` (só leitura: a API publicada não é um servidor).

- [ ] **Step 8: `release.yml`** (`on: push: tags: ['v*']`)
  - **`verificar`:** `uses: ./.github/workflows/ci.yml` com `versao` igual à tag sem o `v` (`${GITHUB_REF_NAME#v}`, calculado num job `preparar` que expõe o output `versao`).
  - **`imagem`** (`needs: verificar`; `permissions: packages: write`):
    1. `docker/setup-buildx-action@v3` e `docker/login-action@v3` (GHCR com `GITHUB_TOKEN`).
    2. `docker/metadata-action@v5` com imagem `ghcr.io/${{ github.repository }}` (o metadata já põe em minúsculas) e tags `type=ref,event=tag` + `type=raw,value=latest`.
    3. `docker/build-push-action@v6` com `load: true`, `build-args: VERSAO=…` e cache `type=gha`.
    4. Teste de fumaça: `docker compose up -d` (com `APP_VERSAO` = tag, `DB_USER`/`DB_PASSWORD` de teste no `env`), depois `scripts/verificar-implantacao.sh` e `docker compose down -v`.
    5. Só então `docker push --all-tags ghcr.io/<owner>/registrodoacoes`, com o nome em minúsculas por `${GITHUB_REPOSITORY,,}`.
  - **`pages`** (`needs: verificar`; `permissions: pages: write, id-token: write`; `environment: github-pages`):
    1. Checkout e setup Java 25.
    2. `actions/download-artifact@v4` do `openapi`.
    3. `./mvnw -B javadoc:javadoc -Ddoclint=none -Dcheckstyle.skip`.
    4. Monta `site/` com `docs/pages/*`, `site/api/openapi.json` e `site/javadoc/` (o `apidocs`).
    5. `actions/upload-pages-artifact@v3` e `actions/deploy-pages@v4`.
  - **`publicar`** (`needs: imagem`; `permissions: contents: write`): checkout com `fetch-depth: 0`, `scripts/changelog.sh "$GITHUB_REF_NAME" > notas.md`, `gh release create "$GITHUB_REF_NAME" --title "$GITHUB_REF_NAME" --notes-file notas.md` (`GH_TOKEN`).
  - Todo `${{ … }}` que vira texto de shell passa por `env:`, nunca é interpolado direto no `run:`.

- [ ] **Step 9: actionlint**

Run: `docker run --rm -v "$PWD:/repo" -w /repo rhysd/actionlint:1.7.7 -color`
Expected: sem erros.

- [ ] **Step 10: Commit**

```bash
git add scripts/changelog.sh scripts/testes/changelog.test.sh .github/workflows docs/pages
git commit -m "ci: release com imagem no GHCR, changelog e documentação da API no GitHub Pages"
```

---

### Task 5: Deploy manual (homologação → produção com aprovação)

**Files:**
- Create: `.github/actions/implantar/action.yml`
- Create: `.github/workflows/deploy.yml`

**Interfaces:**
- Consumes: `scripts/backup.sh`, `scripts/implantar.sh` (Task 3), `docker-compose.yml` (Task 2).
- Produces:
  - Ação composta `implantar`. Inputs: `host`, `usuario`, `chave`, `known-hosts`, `versao` e `backup` (`'true'`/`'false'`).
  - Passos da ação:
    1. Grava a chave e o `known_hosts` em `~/.ssh` com permissão 600, sem `StrictHostKeyChecking=no`.
    2. `ssh … 'mkdir -p ~/registro-doacoes/scripts'`.
    3. `scp docker-compose.yml` e `scripts/{backup,implantar,verificar-implantacao}.sh`.
    4. `ssh … "cd ~/registro-doacoes && { [ $BACKUP != true ] || ./scripts/backup.sh; } && ./scripts/implantar.sh $VERSAO"`.

- [ ] **Step 1: `deploy.yml`**
  - `on: workflow_dispatch` com input `versao` (obrigatório; descrição `Tag publicada pela release, ex.: v1.0.0`).
  - `concurrency: deploy`, sem cancelar a execução em andamento.
  - **`homologacao`** (`environment: homologacao`):
    1. Valida `versao` com a mesma expressão regular do `implantar.sh` num passo `run`, recebendo o valor por `env`. Se não bater, imprime `Versão inválida: …` e sai com 2.
    2. Checkout em `ref: ${{ inputs.versao }}`, para que os arquivos levados ao servidor sejam os da versão.
    3. Ação `implantar` com `backup: 'false'`.
  - **`producao`** (`needs: homologacao`, `environment: production`): a mesma validação e o mesmo checkout, depois a ação com `backup: 'true'`.
  - Segredos `DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_KEY` e `DEPLOY_KNOWN_HOSTS` de cada Environment.

- [ ] **Step 2: actionlint** — mesmo comando da Task 4 · Expected: sem erros.

- [ ] **Step 3: Ensaio local da sequência remota** (o servidor é a própria máquina; prova que os scripts funcionam juntos)

Run: `docker tag ghcr.io/gabrielamedrado/registrodoacoes:latest ghcr.io/gabrielamedrado/registrodoacoes:v0.0.0` e depois `bash scripts/backup.sh && bash scripts/implantar.sh v0.0.0`.
Expected: `Backup gravado em …` e, em seguida, a falha no `docker compose pull app` (a imagem não está publicada no GHCR). Isso prova que o pull é obrigatório e que o erro interrompe o fluxo com saída diferente de 0. Registre o resultado no ledger; o caminho de sucesso já foi coberto pelos testes da Task 3 e pela verificação da Task 2.

- [ ] **Step 4: Commit**

```bash
git add .github/actions .github/workflows/deploy.yml
git commit -m "ci: deploy manual em homologação e produção com aprovação e backup antes"
```

---

### Task 6: README e `.env.example`

**Files:**
- Modify: `README.md`, `.env.example`

- [ ] **Step 1: README**: acrescentar seções às já existentes, sem remover nenhuma.
  - **Rodar como em produção:** `cp .env.example .env`, `docker compose up --build`, http://localhost:8080.
  - **Variáveis:** tabela atualizada com `APP_VERSAO`, `BACKUP_DIR` e `BACKUP_MANTER`.
  - **Documentação da API:** Swagger UI local em http://localhost:8080/swagger-ui (perfil dev), a publicada em https://gabrielamedrado.github.io/registroDoacoes/api/, e a coleção Postman em `postman/README.md`.
  - **Publicar uma versão:**
    - `git tag v1.0.0 && git push origin v1.0.0`.
    - O que a release faz (verificações, imagem, Release, Pages).
    - Pré-requisitos no GitHub: Settings → Pages → Source "GitHub Actions"; o pacote do GHCR público, ou `docker login ghcr.io` no servidor com um token `read:packages`.
  - **Preparar o servidor:**
    - Docker e Compose instalados; criar `~/registro-doacoes/.env` com senhas fortes.
    - Proxy HTTPS na frente da porta `127.0.0.1:8080`, com exemplo de `Caddyfile` de 3 linhas (`dominio { reverse_proxy 127.0.0.1:8080 }`).
    - `ssh-keyscan <host>` para obter o `DEPLOY_KNOWN_HOSTS`.
  - **Deploy:**
    - Environments `homologacao` e `production` (este com "Required reviewers") e os 4 segredos em cada um.
    - Actions → Deploy → Run workflow → versão.
    - Lembrete: `docker compose up -d` manual sem `APP_VERSAO` volta para `latest`.
  - **Backup e restauração:**
    - `./scripts/backup.sh`.
    - Agendamento diário com `crontab` (`0 3 * * * cd ~/registro-doacoes && ./scripts/backup.sh >> backups/backup.log 2>&1`).
    - Restauração por `pg_restore --clean --if-exists` (comando completo, o mesmo validado na Task 3), com o aviso de parar o `app` antes.
    - Copiar `backups/` para fora do servidor.
  - **Estrutura:** acrescentar `scripts/`, `postman/` e `docs/pages/`.

- [ ] **Step 2: `.env.example`**: acrescentar `# APP_VERSAO=v1.0.0` com um comentário dizendo que, no servidor, quem define a versão é o deploy.

- [ ] **Step 3: Conferir os comandos citados**: cada comando do README que já foi executado nas Tasks 2 a 5 está copiado exatamente como rodou. Links relativos válidos: `ls postman/README.md docs/pages/index.html scripts/backup.sh`.

- [ ] **Step 4: Commit**

```bash
git add README.md .env.example
git commit -m "docs: README com imagem Docker, release, deploy, backup e restauração"
```

---

## Rulings já tomados no plano

- **`SpaConfig` em vez de `SpaForwardController`** (spec §7). Um controller com `forward:` captura também `/assets/*.js` inexistentes e devolveria HTML no lugar do JS. O resolvedor de recursos só entra quando o arquivo não existe. Custo se estiver errado: renomear uma classe.
- **Segredo `DEPLOY_KNOWN_HOSTS` além dos 3 da spec.** Sem ele, o runner aceitaria qualquer servidor que respondesse no `DEPLOY_HOST`, e a chave SSH e o deploy poderiam ir para um impostor. Custo: um segredo a mais para configurar.
- **Lógica do servidor em scripts (`implantar.sh`, `verificar-implantacao.sh`) em vez de inline no YAML.** Assim ela é testável localmente e fica igual em homologação, produção e no teste de fumaça da release. A spec só descreve o comportamento.
- **A release testa a imagem antes de publicar** (fumaça com Compose). A spec não exige, mas evita publicar `latest` quebrado.
- **CI em `push` só de branches.** A tag passa a ser verificada pela release, via `workflow_call`, sem rodar duas vezes.
