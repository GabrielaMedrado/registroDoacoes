# Gestão de Doações

Sistema web para gestão de campanhas sociais, necessidades e doações da Igreja Adventista da Promessa de Joinville-SC.

Projeto Integrador de Tecnologia da Informação III — UFMS Digital (2026.2).

- Especificação: [docs/superpowers/specs/2026-10-08-gestao-doacoes-design.md](docs/superpowers/specs/2026-10-08-gestao-doacoes-design.md)
- Planos de implementação: [docs/superpowers/plans/](docs/superpowers/plans/)

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
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | Conexão com o PostgreSQL |
| `ADMIN_EMAIL`, `ADMIN_SENHA` | Primeiro usuário ADMIN, criado automaticamente quando o banco não tem usuários (senha com ao menos 8 caracteres) |

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

## Testes

Backend (requer Docker, usado pelo Testcontainers) — testes, Checkstyle e cobertura JaCoCo:

```bash
cd backend && ./mvnw verify
```

Frontend — lint, tipos, testes e build:

```bash
cd frontend && npm run lint && npm run typecheck && npm test && npm run build
```

O GitHub Actions (`.github/workflows/ci.yml`) executa os dois conjuntos em todo push e pull request.

## Estrutura

```
backend/    API REST Spring Boot (Java 25), PostgreSQL via Flyway
frontend/   React + TypeScript (Vite, Mantine)
config/     Regras do Checkstyle
docs/       Especificação e planos
```
