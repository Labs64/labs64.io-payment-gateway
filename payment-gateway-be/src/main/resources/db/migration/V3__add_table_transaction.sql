-- Transaction table.
-- Note: "transaction" is a reserved word in SQL; the table is named
-- "payment_transaction" to avoid quoting requirements and ambiguity.
CREATE TABLE IF NOT EXISTS payment_transaction
(
    id              UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    payment_id      UUID        NOT NULL,
    tenant_id       TEXT        NOT NULL,
    status          TEXT        NOT NULL,
    status_details  JSONB,
    psp_data        JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (tenant_id, payment_id) REFERENCES payment (tenant_id, id) ON DELETE RESTRICT
);

-- Auto-update updated_at on every modification
CREATE TRIGGER trg_payment_transaction_updated_at
    BEFORE UPDATE
    ON payment_transaction
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

-- Indexes for tenant-scoped queries
CREATE UNIQUE INDEX IF NOT EXISTS ux_transaction_tenant_id ON payment_transaction (tenant_id, id);
CREATE INDEX IF NOT EXISTS ix_transaction_payment_id ON payment_transaction (payment_id);
CREATE INDEX IF NOT EXISTS ix_transaction_tenant_id ON payment_transaction (tenant_id);
