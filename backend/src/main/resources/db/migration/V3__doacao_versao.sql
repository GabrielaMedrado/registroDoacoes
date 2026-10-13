-- Travamento otimista: a edição envia a versão que leu; se outra pessoa salvou antes, a API responde 409.
alter table doacao add column versao bigint not null default 0;
-- Gravado a cada edição: também garante que mudar só os itens aumenta a versão da doação.
alter table doacao add column atualizado_em timestamptz not null default now();
