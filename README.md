# Gestão de Doações

Sistema web para gestão de campanhas sociais, necessidades e doações da Igreja Adventista da Promessa de Joinville-SC.

Projeto Integrador de Tecnologia da Informação III — UFMS Digital (2026.2).

- Especificação: [docs/superpowers/specs/2026-10-08-gestao-doacoes-design.md](docs/superpowers/specs/2026-10-08-gestao-doacoes-design.md)
- Planos de implementação: [docs/superpowers/plans/](docs/superpowers/plans/)
- Banco de dados: [diagrama entidade-relacionamento](docs/banco/diagrama-er.png) e [exemplos de INSERT, SELECT, UPDATE e DELETE](docs/banco/exemplos-dml.sql) (o esquema está nas migrations em `backend/src/main/resources/db/migration/`)
- Documentação da API: anexada a cada [Release](https://github.com/GabrielaMedrado/registroDoacoes/releases) (`openapi.json` e `javadoc-vX.Y.Z.zip`); localmente, no perfil `dev`, em http://localhost:8080/swagger-ui

## Pré-requisitos

- Java 25 (JDK)
- Node.js 26
- Docker Desktop (com WSL 2 no Windows)

## Configuração

Copie o arquivo de exemplo e troque as senhas:

```bash
cp .env.example .env
```

| Variável | Uso |
|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | Conexão com o PostgreSQL. `DB_USER` e `DB_PASSWORD` são obrigatórias: sem elas o Compose para com `defina DB_PASSWORD no .env` |
| `ADMIN_EMAIL`, `ADMIN_SENHA` | Primeiro usuário ADMIN, criado automaticamente quando o banco não tem usuários (senha com ao menos 8 caracteres) |
| `APP_VERSAO` | Versão da imagem que o Compose usa (padrão `latest`). No servidor quem define é o deploy |
| `BACKUP_DIR`, `BACKUP_MANTER` | Pasta dos backups (padrão `backups`) e quantos manter (padrão 30; inteiro maior que zero) |

O `.env` não é versionado.

## Como rodar localmente

1. Banco de dados:

   ```bash
   docker compose up -d db
   ```

2. Backend (em `backend/`), na porta 8080:

   ```bash
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
   ```

3. Frontend (em `frontend/`), na porta 5173 — encaminha `/api` para o backend:

   ```bash
   npm install
   npm run dev
   ```

4. Acesse http://localhost:5173 e entre com `ADMIN_EMAIL` / `ADMIN_SENHA`.

No perfil `dev`, a documentação interativa da API fica em http://localhost:8080/swagger-ui. A coleção Postman está em [postman/](postman/README.md).

### Como em produção (imagem Docker)

A imagem junta o React e a API num só contêiner, no perfil `prod`:

```bash
docker compose up --build
```

Acesse http://localhost:8080. Para voltar ao desenvolvimento normal, `docker compose stop app` (o banco continua no ar).

## Testes

Backend (requer Docker, usado pelo Testcontainers) — testes, Checkstyle e cobertura JaCoCo:

```bash
cd backend && ./mvnw verify
```

Frontend — lint, tipos, testes e build:

```bash
cd frontend && npm run lint && npm run typecheck && npm test && npm run build
```

Scripts de shell (backup, implantação, changelog):

```bash
bash scripts/testes/todos.sh
```

O GitHub Actions (`.github/workflows/ci.yml`) executa os três conjuntos em todo push e pull request, além do ShellCheck.

## Publicar uma versão

Com a `main` verde, crie e envie uma tag no formato `vX.Y.Z`:

```bash
git tag v1.0.0
git push origin v1.0.0
```

O workflow `release.yml` então:

1. repete todas as verificações do CI;
2. constrói a imagem, sobe ela com um banco de teste e confere que está saudável;
3. publica `ghcr.io/gabrielamedrado/registrodoacoes` com as tags `v1.0.0` e `latest`;
4. gera o Javadoc;
5. cria a Release no GitHub com o changelog (Novidades, Correções e Outras mudanças, a partir dos commits) e anexa a especificação OpenAPI (`openapi.json`) e o Javadoc (`javadoc-vX.Y.Z.zip`).

O repositório é privado e o GitHub Pages não é usado: a documentação fica nos anexos de cada Release, visível para quem tem acesso ao repositório. Para ler o Javadoc, descompacte o `.zip` e abra o `index.html`; o `openapi.json` abre no [Swagger Editor](https://editor.swagger.io/) ou no Postman (Import).

Para o servidor baixar a imagem (o pacote no GHCR é privado como o repositório), faça nele uma vez `docker login ghcr.io` com um token que tenha só `read:packages`.

## Preparar o servidor

1. Instale o Docker com o plugin Compose.
2. Crie a pasta e o `.env` com senhas fortes (o deploy leva o resto):

   ```bash
   mkdir -p ~/registro-doacoes
   nano ~/registro-doacoes/.env
   ```

   Basta `DB_USER`, `DB_PASSWORD`, `ADMIN_EMAIL` e `ADMIN_SENHA`. O endereço do banco já vem do `docker-compose.yml`.

3. Coloque um proxy HTTPS na frente da aplicação, que escuta só em `127.0.0.1:8080`. Com o [Caddy](https://caddyserver.com/), o `Caddyfile` é:

   ```
   doacoes.exemplo.org {
       reverse_proxy 127.0.0.1:8080
   }
   ```

   A aplicação reconhece o `X-Forwarded-Proto` do proxy e marca os cookies como `Secure`.

## Deploy

O workflow `deploy.yml` é disparado à mão: **Actions → Deploy → Run workflow**, informando a versão já publicada (ex.: `v1.0.0`). Ele implanta primeiro em homologação. Depois de uma aprovação, faz o backup da produção e implanta a mesma versão lá. Em cada servidor, os passos são:

1. copiar `docker-compose.yml` e os scripts para `~/registro-doacoes/`;
2. rodar `scripts/implantar.sh vX.Y.Z`, que baixa a imagem, troca o contêiner e confere com `scripts/verificar-implantacao.sh` (health, páginas, 401 da API, cookie seguro). Se a aplicação não ficar saudável em 90 s, o job falha;
3. só na produção, antes da troca (`implantar.sh vX.Y.Z --backup`): subir o banco (no primeiro deploy ele ainda não existe) e rodar `scripts/backup.sh`. Se o backup falhar, o deploy para ali e a versão em uso continua no ar.

Configuração única no GitHub (**Settings → Environments**):

- crie `homologacao` e `production`; em `production`, marque **Required reviewers**;
- em cada um, cadastre os segredos:

| Segredo | Conteúdo |
|---|---|
| `DEPLOY_HOST` | Endereço do servidor |
| `DEPLOY_USER` | Usuário SSH (precisa poder usar o Docker) |
| `DEPLOY_KEY` | Chave privada SSH, sem senha, usada só para o deploy |
| `DEPLOY_KNOWN_HOSTS` | Saída de `ssh-keyscan <servidor>`. Sem ela, a conexão é recusada, para a chave nunca ir para outro servidor |

Atenção: um `docker compose up -d` feito à mão no servidor, sem `APP_VERSAO`, volta para a imagem `latest`. Para fixar uma versão, use `./scripts/implantar.sh vX.Y.Z`.

### Se o deploy falhar

Um deploy que falha na verificação já trocou o contêiner, e a versão nova pode ficar reiniciando sem funcionar. Para voltar ao normal:

1. Rode o **Deploy** de novo com a versão anterior (a que estava funcionando; veja em Releases). Ela é implantada do mesmo jeito e conferida.
2. Se a versão nova chegou a mudar o banco (uma migration nova, por exemplo) e a anterior não sobe mais, restaure o backup feito por esse deploy. Ele é o `.dump` mais recente em `~/registro-doacoes/backups/`, e a restauração está descrita abaixo. Depois, implante a versão anterior.
3. Para entender o erro, veja no servidor `docker compose logs app`.

## Backup e restauração

Na pasta do projeto (no servidor, `~/registro-doacoes`), com o banco no ar:

```bash
./scripts/backup.sh
```

O backup é gravado em `backups/doacoes-AAAAMMDD-HHMMSS.dump` (formato custom do `pg_dump`) e só os 30 mais recentes são mantidos. Se algo falhar, nenhum arquivo é gravado. Para um backup diário às 3h, use o `crontab -e`:

```
0 3 * * * cd ~/registro-doacoes && ./scripts/backup.sh >> backups/backup.log 2>&1
```

**Copie a pasta `backups/` para fora do servidor** com regularidade: um backup que só existe no servidor some junto com ele.

Para restaurar, pare a aplicação para ninguém gravar durante a restauração, restaure e suba de novo:

```bash
docker compose stop app
docker compose exec -T db sh -c 'pg_restore --clean --if-exists -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < backups/doacoes-AAAAMMDD-HHMMSS.dump
docker compose start app
```

Para conferir um backup sem mexer no banco principal, restaure em um banco temporário:

```bash
docker compose exec -T db sh -c 'createdb -U "$POSTGRES_USER" doacoes_restauro'
docker compose exec -T db sh -c 'pg_restore -U "$POSTGRES_USER" -d doacoes_restauro' < backups/doacoes-AAAAMMDD-HHMMSS.dump
docker compose exec -T db sh -c 'psql -U "$POSTGRES_USER" -d doacoes_restauro -tAc "select count(*) from campanha"'
docker compose exec -T db sh -c 'dropdb -U "$POSTGRES_USER" doacoes_restauro'
```

## Estrutura

```
backend/            API REST Spring Boot (Java 25), PostgreSQL via Flyway; serve também o React em produção
frontend/           React + TypeScript (Vite, Mantine)
scripts/            backup, implantação, verificação e changelog (testes em scripts/testes/)
postman/            Coleção Postman da API
config/             Regras do Checkstyle
docs/               Especificação, planos de implementação, diagrama e exemplos SQL do banco
.github/workflows/  ci.yml, release.yml, deploy.yml
Dockerfile          node (build do React) → maven (JAR com o React) → JRE 25, sem root
```
