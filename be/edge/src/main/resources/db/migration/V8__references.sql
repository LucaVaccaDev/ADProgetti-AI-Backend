-- Archive of the studio's past services (capitolato 19): the "references" a tender asks for. Canonical on the Edge (D-03).
-- Imported from the studio's Excel; every row keeps where it came from. An imported reference starts as DA_APPROVARE and
-- only an approved one may be used in an official declaration (cap. 19.3).
create table reference_service (
    id             bigserial primary key,
    ref_key        varchar(64) not null unique,       -- CIG when present, otherwise a hash of place+period+object
    cig            varchar(20),
    place          varchar(200),
    period_text    varchar(100),
    period_from    date,
    period_to      date,
    object         text,
    client         varchar(200),
    role_text      varchar(100),
    share_percent  numeric(5,2),
    approval_ref   text,
    status         varchar(14) not null default 'DA_APPROVARE'
                   check (status in ('DA_APPROVARE', 'DA_VERIFICARE', 'APPROVATA', 'SCARTATA')),
    issues         varchar(300),                      -- codes of what could not be read, separated by commas
    content_hash   char(64) not null,                 -- of everything read from the sheet: tells a re-import what changed
    source_file    varchar(1024) not null,
    source_sheet   varchar(100) not null,
    source_column  varchar(8) not null,
    imported_at    timestamptz not null,
    updated_at     timestamptz not null
);
create index reference_service_status_idx on reference_service (status);

-- One row per (service, work id): the amount of the works of that class and category in the service.
create table reference_work_amount (
    service_id   bigint not null references reference_service (id) on delete cascade,
    work_id      varchar(12) not null,                -- ID opera of DM 17/06/2016: E.08, S.03, IA.01...
    category     varchar(12),                         -- category of L. 143/49 as written in the sheet (I/d, III/a...)
    complexity   numeric(4,2),
    amount       numeric(16,2) not null check (amount > 0),
    primary key (service_id, work_id)
);
