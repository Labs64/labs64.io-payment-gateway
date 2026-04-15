-- Payment table
CREATE TABLE IF NOT EXISTS payment
(
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           VARCHAR(255)    NOT NULL,
    payment_method_id   VARCHAR(255)    NOT NULL,
    status              VARCHAR(50)     NOT NULL,
    type                VARCHAR(50)     NOT NULL,
    amount              BIGINT          NOT NULL    CHECK (amount > 0),
    currency            VARCHAR(3)      NOT NULL    CHECK (currency ~ '^[A-Z]{3}$'),
    description         VARCHAR(500),
    purchase_order_ref  VARCHAR(255),
    correlation_id      VARCHAR(255),
    billing_info        JSONB,
    shipping_info       JSONB,
    extra               JSONB,
    next_action         JSONB,
    created_at          TIMESTAMPTZ     NOT NULL    DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL    DEFAULT now(),

    CONSTRAINT chk_payment_status CHECK (status IN ('ACTIVE', 'INCOMPLETE', 'PAUSED', 'CLOSED')),
    CONSTRAINT chk_payment_type   CHECK (type   IN ('ONE_TIME', 'RECURRING'))
);

-- Auto-update updated_at on every modification
CREATE TRIGGER trg_payment_updated_at
    BEFORE UPDATE ON payment
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Indexes for tenant-scoped queries
CREATE INDEX IF NOT EXISTS idx_payment_tenant_id        ON payment (tenant_id);
CREATE INDEX IF NOT EXISTS idx_payment_tenant_status    ON payment (tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_payment_correlation_id   ON payment (correlation_id);
