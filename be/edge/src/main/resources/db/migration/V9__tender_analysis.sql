-- Requirements read from a tender's documents (capitolato E.2). The document itself is NEVER kept: it is downloaded or
-- uploaded, read once and deleted; what stays is the list of requirements, each with the page and the quoted words
-- it comes from, plus the SHA-256 of the file (to recognise the same document again).
create table tender_analysis (
    notice_number varchar(20) primary key references tender (notice_number) on delete cascade,
    status        varchar(12) not null check (status in ('DA_CARICARE', 'ANALIZZATA', 'INCOMPLETA', 'FALLITA')),
    source        varchar(10) check (source in ('SCARICATO', 'CARICATO')),
    doc_sha256    char(64),
    pages         integer,
    result_json   text,
    model         varchar(80),
    error_code    varchar(40),
    requested_by  varchar(64),
    analysed_at   timestamptz not null
);
