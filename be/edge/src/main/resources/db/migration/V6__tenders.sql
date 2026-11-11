-- Tenders found in public sources (today TED). Public data only: no document text, no analysis of the studio.
-- The Edge is the canonical home; the Hub gets a read-only projection (tenders.list).
create table tender (
    notice_number  varchar(20) primary key,           -- TED publication number, e.g. 723217-2026
    source         varchar(10) not null default 'TED',
    title          varchar(400) not null,
    buyer          varchar(300),
    amount         numeric(16, 2),
    currency       char(3),
    deadline_at    timestamptz,
    published_on   date not null,
    procedure_type varchar(40),
    notice_type    varchar(40) not null,
    cpv            varchar(200) not null default '',   -- space-separated, at most a few
    place          varchar(200),
    lots           integer not null default 1,
    first_seen_at  timestamptz not null,
    last_seen_at   timestamptz not null
);
create index tender_deadline_idx on tender (deadline_at);
create index tender_published_idx on tender (published_on desc);

-- One row: the outcome of the last synchronisation (shown on the Hub, never the query or the response).
create table tender_sync (
    id             smallint primary key check (id = 1),
    last_started_at  timestamptz,
    last_finished_at timestamptz,
    last_status    varchar(12) not null default 'NEVER',
    fetched        integer not null default 0,
    added          integer not null default 0,
    error_code     varchar(40)
);
insert into tender_sync (id) values (1);
