create table permissions (
    code        varchar(64) primary key,
    description varchar(200) not null
);

create table roles (
    code varchar(64) primary key,
    name varchar(100) not null
);

create table role_permissions (
    role_code       varchar(64) not null references roles (code),
    permission_code varchar(64) not null references permissions (code),
    primary key (role_code, permission_code)
);

create table users (
    id              uuid primary key,
    username        varchar(100) not null unique,
    display_name    varchar(150) not null,
    email           varchar(254),
    password_hash   varchar(255) not null,
    enabled         boolean not null default true,
    -- Placeholder for TOTP (deviation D-10: TOTP temporarily not implemented).
    mfa_required    boolean not null default false,
    failed_attempts integer not null default 0,
    locked_until    timestamptz,
    last_login_at   timestamptz,
    created_at      timestamptz not null,
    updated_at      timestamptz not null,
    constraint users_username_lowercase check (username = lower(username))
);

create table user_roles (
    user_id   uuid not null references users (id) on delete cascade,
    role_code varchar(64) not null references roles (code),
    primary key (user_id, role_code)
);

create table refresh_tokens (
    id          uuid primary key,
    user_id     uuid not null references users (id) on delete cascade,
    family_id   uuid not null,
    token_hash  varchar(64) not null unique,
    created_at  timestamptz not null,
    expires_at  timestamptz not null,
    revoked_at  timestamptz
);
create index refresh_tokens_user_idx on refresh_tokens (user_id);
create index refresh_tokens_family_idx on refresh_tokens (family_id);

create table audit_log (
    id             bigserial primary key,
    occurred_at    timestamptz not null,
    actor_user_id  uuid,
    actor_username varchar(100),
    action         varchar(64) not null,
    target         varchar(200),
    outcome        varchar(16) not null,
    detail         varchar(500),
    ip_address     varchar(64)
);
create index audit_log_occurred_idx on audit_log (occurred_at);
