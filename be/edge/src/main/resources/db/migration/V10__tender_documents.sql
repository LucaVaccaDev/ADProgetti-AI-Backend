-- Tender documents kept until the tender expires (D-30, replaces the "read once and delete" rule of D-29 (1)).
-- The files live on the Edge's disk (the "BANDI PROVVISORI" folder); this table only describes them. They are public
-- tender documents. Deleting a tender (deadline passed) deletes its rows here and the library removes the files.
create table tender_document (
    id            uuid primary key default gen_random_uuid(),
    notice_number varchar(20) not null references tender (notice_number) on delete cascade,
    file_name     varchar(160) not null,
    size_bytes    bigint not null,
    sha256        char(64) not null,
    origin        varchar(10) not null check (origin in ('SCARICATO', 'CARICATO')),
    source_url    varchar(600),
    stored_at     timestamptz not null,
    unique (notice_number, sha256)
);

-- What happened the last time the Edge tried to fetch the documents of a tender on its own.
-- NULL = never tried; NON_SCARICABILE = nothing found or the page needs a person; TROPPO_GRANDE = a file above the cap.
alter table tender add column doc_status varchar(16) check (doc_status in ('NON_SCARICABILE', 'TROPPO_GRANDE'));
alter table tender add column doc_note varchar(60);
alter table tender add column doc_checked_at timestamptz;
