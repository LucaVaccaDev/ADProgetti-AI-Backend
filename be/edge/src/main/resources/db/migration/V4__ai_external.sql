-- External AI (FreeLLMAPI): the daily limit per user counts rows of this log, so make that query cheap.
-- Still metadata only: no column can hold a prompt or an answer.
create index ai_usage_log_user_idx on ai_usage_log (requested_by, provider, occurred_at);
