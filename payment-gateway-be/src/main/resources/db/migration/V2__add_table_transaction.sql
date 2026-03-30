-- Transaction table
CREATE TABLE IF NOT EXISTS transaction
(
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id          UUID            NOT NULL REFERENCES payment (id),
    tenant_id           VARCHAR(255)    NOT NULL,
    idempotency_key     VARCHAR(255),
    status              VARCHAR(50)     NOT NULL,
    failure_code        VARCHAR(255),
    failure_message     TEXT,
    psp_data            JSONB,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- Indexes for tenant-scoped queries and idempotency
CREATE INDEX IF NOT EXISTS idx_transaction_payment_id ON transaction (payment_id);
CREATE INDEX IF NOT EXISTS idx_transaction_tenant_id ON transaction (tenant_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_transaction_idempotency ON transaction (tenant_id, payment_id, idempotency_key);
