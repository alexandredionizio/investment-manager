-- Apenas equivalencias confirmadas pelo usuario; nenhuma movimentacao financeira.
create table b3_broker_aliases (
    id bigserial primary key,
    normalized_name varchar(100) not null unique,
    broker_id bigint not null references brokers(id) on delete restrict,
    created_at timestamptz not null default now()
);
create index idx_b3_broker_aliases_broker_id on b3_broker_aliases(broker_id);
