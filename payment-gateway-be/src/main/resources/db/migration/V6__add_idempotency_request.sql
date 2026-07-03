-- Generic HTTP idempotency request cache.
-- Idempotency is scoped by tenant, normalized operation, and client-provided key.
-- The request hash includes tenant, HTTP method, raw path, query string, and body.
CREATE TABLE IF NOT EXISTS idempotency_request
(
    id               UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    tenant_id        TEXT        NOT NULL,
    operation        TEXT        NOT NULL,
    key              TEXT        NOT NULL,
    request_hash     TEXT        NOT NULL,
    status           TEXT        NOT NULL,
    response_status  INTEGER,
    response_headers JSONB,
    response_body    JSONB,
    expires_at       TIMESTAMPTZ NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TRIGGER trg_idempotency_request_updated_at
    BEFORE UPDATE
    ON idempotency_request
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE UNIQUE INDEX IF NOT EXISTS ux_idempotency_request_key
    ON idempotency_request (tenant_id, operation, key);

CREATE INDEX IF NOT EXISTS ix_idempotency_request_expires_at
    ON idempotency_request (expires_at);

CREATE INDEX IF NOT EXISTS ix_idempotency_request_status
    ON idempotency_request (status);
