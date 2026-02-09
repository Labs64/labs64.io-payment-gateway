ALTER TABLE payments
    ADD COLUMN psp_reference VARCHAR(128);

ALTER TABLE payment_transactions
    ADD COLUMN psp_reference VARCHAR(128);

CREATE INDEX IF NOT EXISTS idx_payments_provider_psp_ref
    ON payments (provider, psp_reference);
