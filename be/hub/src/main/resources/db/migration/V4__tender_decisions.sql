-- Studio-wide decisions on monitored tenders: ACCEPTED ("partecipiamo") or REJECTED (discarded, recoverable).
-- A decision lives until the tender's deadline: the Hub copies the deadline from the tender list the Edge sent,
-- never from the client, and a scheduled purge removes decisions past it. No tender text is kept here.
create table tender_decision (
    tender_id     varchar(40)  primary key,
    decision      varchar(10)  not null check (decision in ('ACCEPTED', 'REJECTED')),
    decided_by    uuid         not null,
    decided_name  varchar(150) not null,
    decided_at    timestamptz  not null,
    deadline_at   timestamptz
);
create index tender_decision_deadline_idx on tender_decision (deadline_at);
