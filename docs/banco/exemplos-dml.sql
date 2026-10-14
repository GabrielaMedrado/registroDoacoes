-- Exemplos de manipulação de dados (INSERT, SELECT, UPDATE e DELETE) sobre o esquema
-- das migrations em backend/src/main/resources/db/migration/.
--
-- ATENÇÃO: rode só em um banco temporário, nunca no banco do sistema. Alguns comandos
-- falham de propósito, para mostrar as restrições (CHECK e chaves estrangeiras).
-- Os ids 1, 2... supõem um banco vazio, recém-criado.
--
-- Na raiz do projeto, com o banco no ar (docker compose up -d db):
--
--   docker compose exec -T db sh -c 'createdb -U "$POSTGRES_USER" doacoes_exemplo'
--   for f in backend/src/main/resources/db/migration/V*.sql; do
--     docker compose exec -T db sh -c 'psql -q -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d doacoes_exemplo' < "$f"
--   done
--   docker compose exec -T db sh -c 'psql -U "$POSTGRES_USER" -d doacoes_exemplo --echo-all' < docs/banco/exemplos-dml.sql
--   docker compose exec -T db sh -c 'dropdb -U "$POSTGRES_USER" doacoes_exemplo'

\set ON_ERROR_STOP off
\pset footer off

-- 1. INSERÇÃO

INSERT INTO usuario (nome, email, senha_hash, papel)
VALUES ('Ana Souza', 'ana@exemplo.org', '$2a$10$hashIlustrativo', 'VOLUNTARIO')
RETURNING id, nome, papel;

INSERT INTO campanha (titulo, data_inicio, data_fim, status, meta_valor, criado_por)
VALUES ('Cestas básicas de Natal', '2026-11-01', '2026-12-20', 'ATIVA', 1500.00, 1)
RETURNING id, titulo, status;

INSERT INTO necessidade (campanha_id, item, unidade, quantidade_meta)
VALUES (1, 'Arroz', 'KG', 50), (1, 'Feijão', 'KG', 30), (1, 'Óleo de soja', 'LITRO', 20);

INSERT INTO doador (nome, telefone) VALUES ('João Pereira', '(47) 99999-0000');

-- Doação de itens: a doação e os seus itens
INSERT INTO doacao (campanha_id, doador_id, tipo, data_doacao, registrado_por)
VALUES (1, 1, 'ITEM', '2026-11-03', 1);
INSERT INTO item_doacao (doacao_id, necessidade_id, quantidade)
VALUES (1, 1, 10), (1, 2, 5);

-- Doação em dinheiro, anônima (sem doador)
INSERT INTO doacao (campanha_id, doador_id, tipo, valor, forma_pagamento, data_doacao, registrado_por)
VALUES (1, NULL, 'DINHEIRO', 200.00, 'PIX', '2026-11-04', 1);

-- 2. RESTRIÇÕES (os dois comandos falham)

-- Valor negativo: recusado pelo CHECK da tabela doacao
INSERT INTO doacao (campanha_id, tipo, valor, forma_pagamento, data_doacao, registrado_por)
VALUES (1, 'DINHEIRO', -50.00, 'PIX', '2026-11-04', 1);

-- Necessidade inexistente: recusada pela chave estrangeira
INSERT INTO item_doacao (doacao_id, necessidade_id, quantidade) VALUES (1, 99, 3);

-- 3. CONSULTAS

-- Progresso da campanha: meta, arrecadado e pendente de cada necessidade
-- (mesma lógica do ProgressoRepository; o total nunca é gravado, sempre calculado)
SELECT n.item, n.unidade, n.quantidade_meta AS meta,
       COALESCE(SUM(i.quantidade), 0) AS arrecadado,
       GREATEST(n.quantidade_meta - COALESCE(SUM(i.quantidade), 0), 0) AS pendente
FROM necessidade n
LEFT JOIN item_doacao i ON i.necessidade_id = n.id
WHERE n.campanha_id = 1
GROUP BY n.id
ORDER BY pendente DESC;

-- Doações com o doador ("Anônimo" quando não há) e quem registrou
SELECT d.id, d.data_doacao, d.tipo, COALESCE(dr.nome, 'Anônimo') AS doador,
       d.valor, u.nome AS registrado_por
FROM doacao d
LEFT JOIN doador dr ON dr.id = d.doador_id
JOIN usuario u ON u.id = d.registrado_por
ORDER BY d.data_doacao;

-- Total em dinheiro por campanha, comparado com a meta
SELECT c.titulo, SUM(d.valor) AS total_dinheiro, c.meta_valor
FROM campanha c
JOIN doacao d ON d.campanha_id = c.id AND d.tipo = 'DINHEIRO'
GROUP BY c.id;

-- 4. ATUALIZAÇÃO

UPDATE item_doacao SET quantidade = 12 WHERE doacao_id = 1 AND necessidade_id = 1
RETURNING doacao_id, necessidade_id, quantidade;

-- Travamento otimista: a edição informa a versão que leu (0) e é aplicada...
UPDATE doacao SET observacao = 'Entregue na secretaria', versao = versao + 1, atualizado_em = now()
WHERE id = 1 AND versao = 0
RETURNING id, versao, observacao;

-- ...e a de outra pessoa, feita a partir da mesma versão 0, não altera nada (UPDATE 0).
-- No sistema, esse caso vira a resposta 409 da API.
UPDATE doacao SET observacao = 'Edição de outra pessoa', versao = versao + 1
WHERE id = 1 AND versao = 0;

-- 5. REMOÇÃO

-- Falha: a necessidade já tem itens doados (chave estrangeira, regra R7)
DELETE FROM necessidade WHERE id = 1;

-- Apagar a doação apaga também os seus itens (on delete cascade)
DELETE FROM doacao WHERE id = 1;

SELECT count(*) AS itens_restantes FROM item_doacao WHERE doacao_id = 1;
