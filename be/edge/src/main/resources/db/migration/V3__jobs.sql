-- Persistent job engine (capitolato sez. 40) and scheduler state (sez. 7.10, B.18).
create table job (
    id                uuid primary key,
    type              varchar(40)  not null,
    status            varchar(12)  not null check (status in ('QUEUED','RUNNING','SUCCEEDED','FAILED','CANCELLED')),
    priority          smallint     not null default 5,
    created_at        timestamptz  not null,
    started_at        timestamptz,
    finished_at       timestamptz,
    attempts          int          not null default 0,
    max_attempts      int          not null,
    next_run_at       timestamptz  not null,
    error_code        varchar(32),
    subject_ref       varchar(200),
    idempotency_key   varchar(200) not null unique,
    resource_key      varchar(100),
    payload_json      text         not null default '{}',
    result_json       text,
    lease_until       timestamptz,
    cancel_requested  boolean      not null default false,
    requested_by      varchar(64)  not null
);
create index job_queue_idx on job (priority desc, next_run_at) where status = 'QUEUED';
create index job_running_idx on job (resource_key) where status = 'RUNNING';
create index job_finished_idx on job (finished_at) where finished_at is not null;

-- Last slot handed to the job queue for each schedule: restarts never relaunch the same slot.
create table schedule_state (
    name       varchar(60) primary key,
    last_slot  timestamptz not null
);
