-- Payment table
CREATE TABLE IF NOT EXISTS payment
(
    id                UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    tenant_id         TEXT        NOT NULL,
    payment_provider_id UUID        NOT NULL,
    status            TEXT        NOT NULL,
    description       TEXT,
    purchase_order    JSONB       NOT NULL,
    billing_info      JSONB,
    shipping_info     JSONB,
    recurrence        JSONB,
    extra             JSONB,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (tenant_id, payment_provider_id) REFERENCES payment_provider (tenant_id, id) ON DELETE RESTRICT
);

-- Auto-update updated_at on every modification
CREATE TRIGGER trg_payment_updated_at
    BEFORE UPDATE
    ON payment
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

-- Indexes for tenant-scoped queries
CREATE UNIQUE INDEX IF NOT EXISTS ux_payment_tenant_id ON payment (tenant_id, id);
CREATE INDEX IF NOT EXISTS ix_payment_tenant ON payment (tenant_id);
CREATE INDEX IF NOT EXISTS ix_payment_tenant_status ON payment (tenant_id, status);
CREATE INDEX IF NOT EXISTS ix_payment_payment_provider ON payment (tenant_id, payment_provider_id);
CREATE INDEX IF NOT EXISTS ix_payment_po_currency ON payment ((purchase_order ->> 'currency'));
CREATE INDEX IF NOT EXISTS ix_payment_po_gross_amount ON payment (((purchase_order ->> 'grossAmount')));
