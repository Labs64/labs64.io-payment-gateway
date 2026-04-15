-- Transaction table.
-- Note: "transaction" is a reserved word in SQL; the table is named
-- "payment_transaction" to avoid quoting requirements and ambiguity.
CREATE TABLE IF NOT EXISTS payment_transaction
(
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id          UUID            NOT NULL    REFERENCES payment (id) ON DELETE CASCADE,
    tenant_id           VARCHAR(255)    NOT NULL,
    idempotency_key     VARCHAR(255),
    status              VARCHAR(50)     NOT NULL,
    failure_code        VARCHAR(255),
    failure_message     TEXT,
    psp_data            JSONB,
    created_at          TIMESTAMPTZ     NOT NULL    DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL    DEFAULT now(),

    CONSTRAINT chk_transaction_status CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED'))
);

-- Auto-update updated_at on every modification
CREATE TRIGGER trg_payment_transaction_updated_at
    BEFORE UPDATE ON payment_transaction
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Indexes for tenant-scoped queries and idempotency
CREATE INDEX IF NOT EXISTS idx_transaction_payment_id   ON payment_transaction (payment_id);
CREATE INDEX IF NOT EXISTS idx_transaction_tenant_id    ON payment_transaction (tenant_id);
-- Partial unique index: only enforce uniqueness when idempotency_key is set
CREATE UNIQUE INDEX IF NOT EXISTS idx_transaction_idempotency
    ON payment_transaction (tenant_id, payment_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
