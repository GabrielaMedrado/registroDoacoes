-- Esquema inicial: modelo de dados da spec §3

create table usuario (
    id          bigint generated always as identity primary key,
    nome        varchar(120) not null,
    email       varchar(160) not null unique,
    senha_hash  varchar(100) not null,
    papel       varchar(20)  not null check (papel in ('ADMIN', 'VOLUNTARIO')),
    ativo       boolean      not null default true,
    criado_em   timestamptz  not null default now()
);

create table campanha (
    id             bigint generated always as identity primary key,
    titulo         varchar(150) not null,
    descricao      text,
    data_inicio    date         not null,
    data_fim       date,
    status         varchar(20)  not null check (status in ('RASCUNHO', 'ATIVA', 'ENCERRADA')),
    meta_valor     numeric(12, 2) check (meta_valor is null or meta_valor > 0),
    criado_por     bigint       not null references usuario (id),
    criado_em      timestamptz  not null default now(),
    atualizado_em  timestamptz  not null default now(),
    check (data_fim is null or data_fim >= data_inicio)
);
create index idx_campanha_criado_por on campanha (criado_por);

create table necessidade (
    id               bigint generated always as identity primary key,
    campanha_id      bigint         not null references campanha (id),
    item             varchar(120)   not null,
    unidade          varchar(20)    not null
        check (unidade in ('UNIDADE', 'KG', 'LITRO', 'PACOTE', 'CAIXA', 'PAR')),
    quantidade_meta  numeric(12, 2) not null check (quantidade_meta > 0),
    observacao       text
);
create index idx_necessidade_campanha on necessidade (campanha_id);

create table doador (
    id          bigint generated always as identity primary key,
    nome        varchar(120),
    telefone    varchar(30),
    email       varchar(160),
    observacao  text,
    criado_em   timestamptz not null default now()
);

create table doacao (
    id               bigint generated always as identity primary key,
    campanha_id      bigint      not null references campanha (id),
    doador_id        bigint      references doador (id),
    tipo             varchar(20) not null check (tipo in ('ITEM', 'DINHEIRO')),
    valor            numeric(12, 2),
    forma_pagamento  varchar(20) check (forma_pagamento in ('PIX', 'ESPECIE', 'OUTRO')),
    data_doacao      date        not null,
    observacao       text,
    registrado_por   bigint      not null references usuario (id),
    criado_em        timestamptz not null default now(),
    check (
        (tipo = 'DINHEIRO' and valor > 0 and forma_pagamento is not null)
        or (tipo = 'ITEM' and valor is null and forma_pagamento is null)
    )
);
create index idx_doacao_campanha on doacao (campanha_id);
create index idx_doacao_doador on doacao (doador_id);
create index idx_doacao_registrado_por on doacao (registrado_por);
create index idx_doacao_data on doacao (data_doacao);

create table item_doacao (
    id              bigint generated always as identity primary key,
    doacao_id       bigint         not null references doacao (id) on delete cascade,
    necessidade_id  bigint         not null references necessidade (id),
    quantidade      numeric(12, 2) not null check (quantidade > 0)
);
create index idx_item_doacao_doacao on item_doacao (doacao_id);
create index idx_item_doacao_necessidade on item_doacao (necessidade_id);
