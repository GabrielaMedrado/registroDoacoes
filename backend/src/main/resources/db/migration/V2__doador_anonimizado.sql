-- R9: a anonimização é definitiva; o sinal impede que os dados sejam digitados de volta.
ALTER TABLE doador ADD COLUMN anonimizado BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE doador
SET anonimizado = TRUE
WHERE nome = 'Anônimo' AND telefone IS NULL AND email IS NULL AND observacao IS NULL;
