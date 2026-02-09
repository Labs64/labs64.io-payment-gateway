ALTER TABLE idempotency_keys
    ADD COLUMN tenant_id VARCHAR(128) NOT NULL DEFAULT 'unknown';

ALTER TABLE idempotency_keys
    DROP CONSTRAINT IF EXISTS uq_payment_idempotency;

ALTER TABLE idempotency_keys
    ADD CONSTRAINT uq_payment_idempotency UNIQUE (payment_id, tenant_id, idempotency_key);
