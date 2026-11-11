-- A second source (Europaconcorsi) next to TED: where a tender comes from, a link to the publishing portal,
-- and one synchronisation state per source. Still public data only: titles, entities, dates, links.
alter table tender alter column source type varchar(16);
alter table tender add column url varchar(500);

alter table tender_sync add column source varchar(16) not null default 'TED';
alter table tender_sync drop constraint tender_sync_id_check;
alter table tender_sync add constraint tender_sync_id_check check (id in (1, 2));
insert into tender_sync (id, source) values (2, 'EUROPACONCORSI');
