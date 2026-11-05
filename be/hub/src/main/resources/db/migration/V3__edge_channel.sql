-- Hub <-> Edge channel (CLAUDE.md section 5). Only commands, results and read-only projections live here.

-- Single credential of the Edge: only the SHA-256 hash of the 256-bit key is stored.
create table edge_credential (
    id          smallint primary key check (id = 1),
    key_hash    char(64) not null,
    created_at  timestamptz not null,
    rotated_by  uuid
);

-- Outbox of signed Hub->Edge commands (at-least-once delivery).
create table command_outbox (
    id            uuid primary key,
    type          varchar(40) not null,
    requested_by  uuid not null,
    status        varchar(12) not null check (status in
                  ('PENDING','SENT','ACKED','SUCCEEDED','FAILED','REJECTED','EXPIRED')),
    signed_json   text not null,
    created_at    timestamptz not null,
    expires_at    timestamptz not null,
    attempts      integer not null default 0,
    last_sent_at  timestamptz,
    acked_at      timestamptz
);
create index command_outbox_open_idx on command_outbox (status, created_at);

create table command_result (
    command_id   uuid primary key references command_outbox (id),
    status       varchar(12) not null,
    result       text,
    error_code   varchar(64),
    received_at  timestamptz not null
);

-- Inbox: idempotency keys of messages already applied (a repeated message is ignored).
create table channel_inbox (
    idempotency_key  varchar(100) primary key,
    received_at      timestamptz not null
);

-- Read-only projections produced by the Edge (allowed names are checked in code).
create table projection (
    name        varchar(64) not null,
    entity_id   varchar(100) not null,
    version     bigint not null,
    data        text not null,
    updated_at  timestamptz not null,
    primary key (name, entity_id)
);

-- Last known Edge state (single row).
create table edge_state (
    id            smallint primary key check (id = 1),
    last_seen_at  timestamptz,
    edge_version  varchar(40),
    services      text
);
