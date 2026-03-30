-- Payment table
CREATE TABLE IF NOT EXISTS payment
(
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           VARCHAR(255)    NOT NULL,
    payment_method_id   VARCHAR(255)    NOT NULL,
    status              VARCHAR(50)     NOT NULL,
    type                VARCHAR(50)     NOT NULL,
    amount              BIGINT          NOT NULL,
    currency            VARCHAR(3)      NOT NULL,
    description         VARCHAR(500),
    purchase_order_ref  VARCHAR(255),
    correlation_id      VARCHAR(255),
    billing_info        JSONB,
    shipping_info       JSONB,
    extra               JSONB,
    next_action         JSONB,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- Indexes for tenant-scoped queries
CREATE INDEX IF NOT EXISTS idx_payment_tenant_id ON payment (tenant_id);
CREATE INDEX IF NOT EXISTS idx_payment_tenant_status ON payment (tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_payment_correlation_id ON payment (correlation_id);
