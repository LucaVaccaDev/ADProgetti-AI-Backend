-- Document index (capitolato 4.8 and 5.3). The Edge is the only home of document text (D-03): the Hub never
-- receives it, except short excerpts of a search result that it erases after a few minutes.
create table indexed_document (
    id            bigserial primary key,
    path          varchar(1024) not null unique,   -- logical path: share:/folder/file.pdf
    size_bytes    bigint not null,
    modified_at   timestamptz not null,
    sha256        char(64),
    mime          varchar(40) not null default 'application/pdf',
    status        varchar(12) not null check (status in ('INDEXED', 'NO_TEXT', 'FAILED', 'TOO_LARGE', 'DUPLICATE', 'REMOVED')),
    error_code    varchar(40),
    pages         integer,
    text_chars    integer,
    duplicate_of  bigint references indexed_document (id) on delete set null,
    indexed_at    timestamptz not null,
    seen_run      bigint
);
create index indexed_document_sha_idx on indexed_document (sha256) where sha256 is not null;
create index indexed_document_status_idx on indexed_document (status);

create table document_chunk (
    id           bigserial primary key,
    document_id  bigint not null references indexed_document (id) on delete cascade,
    ordinal      integer not null,
    page_from    integer not null,
    page_to      integer not null,
    content      text not null,
    tsv          tsvector generated always as (to_tsvector('italian', content)) stored,
    unique (document_id, ordinal)
);
create index document_chunk_tsv_idx on document_chunk using gin (tsv);

-- bge-m3 produces 1024 numbers per passage.
create table document_embedding (
    chunk_id   bigint primary key references document_chunk (id) on delete cascade,
    embedding  vector(1024) not null
);
create index document_embedding_hnsw_idx on document_embedding using hnsw (embedding vector_cosine_ops);

-- One row per indexing run: counts only, never a file name.
create table indexing_run (
    id           bigserial primary key,
    started_at   timestamptz not null,
    finished_at  timestamptz,
    status       varchar(12) not null check (status in ('RUNNING', 'COMPLETED', 'PARTIAL', 'CANCELLED', 'FAILED')),
    scanned      integer not null default 0,
    indexed      integer not null default 0,
    skipped      integer not null default 0,
    no_text      integer not null default 0,
    failed       integer not null default 0,
    duplicates   integer not null default 0,
    too_large    integer not null default 0,
    removed      integer not null default 0,
    unsupported  integer not null default 0,
    chunks       integer not null default 0,
    error_code   varchar(40)
);
