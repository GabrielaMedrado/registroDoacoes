# Coleção Postman

`registroDoacoes.postman_collection.json` cobre os fluxos principais da API, nesta ordem: login, campanhas, necessidades, doações, progresso, painel, relatórios e logout. Cada requisição tem testes (`pm.test`) e guarda os ids que as seguintes usam. Ao final, a campanha de teste é encerrada para não aparecer no painel.

## No Postman

1. Importe os dois arquivos desta pasta (coleção e ambiente `Local`).
2. No ambiente `Local`, preencha `adminEmail` e `adminSenha` com o ADMIN do seu `.env` (`ADMIN_EMAIL` e `ADMIN_SENHA`). **Não versione a senha.**
3. Com o backend rodando (`docker compose up -d db` e `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` em `backend/`), abra a coleção no **Runner** e execute.

O token CSRF é tratado pela própria coleção: o cookie `XSRF-TOKEN` é guardado em `{{xsrf}}` e enviado no cabeçalho `X-XSRF-TOKEN` em toda requisição que altera dados.

## Pela linha de comando (newman)

```bash
npx --yes newman@6 run postman/registroDoacoes.postman_collection.json -e postman/local.postman_environment.json --env-var "adminEmail=<ADMIN_EMAIL>" --env-var "adminSenha=<ADMIN_SENHA>"
```

Resultado esperado: `assertions 22 executadas, 0 falhas`.

> Cada execução cria uma campanha "Campanha Postman <timestamp>" (encerrada no fim) no banco local. Uma senha errada conta como tentativa de login: depois de 5 seguidas, o login fica bloqueado por 15 minutos.
