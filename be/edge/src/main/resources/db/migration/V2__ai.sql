-- Local AI: model assignments chosen by the administrator and a usage log with metadata only.
create table ai_model_assignment (
    function        varchar(20) primary key,
    model           varchar(100) not null,
    previous_model  varchar(100),
    assigned_by     varchar(64),
    assigned_at     timestamptz not null
);

-- Never contains prompts, answers, file names or e-mail text (CLAUDE.md sections 4 and 9).
create table ai_usage_log (
    id                 bigserial primary key,
    occurred_at        timestamptz not null,
    function           varchar(20) not null,
    model              varchar(100) not null,
    provider           varchar(20) not null,
    requested_by       varchar(64),
    outcome            varchar(16) not null,
    reason             varchar(40),
    latency_ms         bigint not null,
    prompt_tokens      integer,
    completion_tokens  integer
);
create index ai_usage_log_occurred_idx on ai_usage_log (occurred_at);
