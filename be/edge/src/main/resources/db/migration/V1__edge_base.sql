-- Edge base schema (Release 1). Document index and AI tables arrive with their own migrations.
create extension if not exists vector;

-- Commands received from the Hub (inbox). A repeated id is never executed again: the stored result is resent.
create table inbox_command (
    id            uuid primary key,
    type          varchar(40) not null,
    requested_by  varchar(64) not null,
    status        varchar(12) not null check (status in ('RECEIVED','DONE','REJECTED','FAILED')),
    received_at   timestamptz not null,
    completed_at  timestamptz,
    result_json   text
);

-- Nonces already used, so a replayed command is refused even after a restart.
create table nonce_seen (
    nonce       varchar(100) primary key,
    expires_at  timestamptz not null
);

-- Messages to the Hub (acks, results, projections): saved first, then sent. A message counts as sent
-- only when written to the socket; the recent ones are resent at every reconnection (the Hub is idempotent).
create table outbox_message (
    id          bigserial primary key,
    kind        varchar(12) not null,
    frame       text not null,
    created_at  timestamptz not null,
    sent_at     timestamptz
);
create index outbox_message_unsent_idx on outbox_message (id) where sent_at is null;

create table audit_log (
    id           bigserial primary key,
    occurred_at  timestamptz not null,
    action       varchar(64) not null,
    target       varchar(200),
    outcome      varchar(16) not null,
    detail       varchar(500)
);
create index audit_log_occurred_idx on audit_log (occurred_at);
