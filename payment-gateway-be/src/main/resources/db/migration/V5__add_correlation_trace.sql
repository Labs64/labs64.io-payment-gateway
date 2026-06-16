-- Generic correlation binding table.
-- Correlation ID is trace/debug metadata, not a payment execution key, so it
-- lives outside core payment/transaction rows.
CREATE TABLE IF NOT EXISTS correlation_trace
(
    id             UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    correlation_id TEXT        NOT NULL,
    entity_type    TEXT        NOT NULL,
    entity_id      UUID        NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_correlation_trace_correlation_id ON correlation_trace (correlation_id);
CREATE INDEX IF NOT EXISTS ix_correlation_trace_entity ON correlation_trace (entity_type, entity_id, created_at DESC);
