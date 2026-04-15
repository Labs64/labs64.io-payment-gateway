-- PSP configuration table (tenant-specific PSP settings)
CREATE TABLE IF NOT EXISTS psp_config
(
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           VARCHAR(255)    NOT NULL,
    payment_method_id   VARCHAR(255)    NOT NULL,
    enabled             BOOLEAN         NOT NULL    DEFAULT true,
    config              JSONB           NOT NULL,
    created_at          TIMESTAMPTZ     NOT NULL    DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL    DEFAULT now()
);

-- Auto-update updated_at on every modification
CREATE TRIGGER trg_psp_config_updated_at
    BEFORE UPDATE ON psp_config
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Unique constraint: one config per tenant + payment method
CREATE UNIQUE INDEX IF NOT EXISTS idx_psp_config_tenant_method
    ON psp_config (tenant_id, payment_method_id);
CREATE INDEX IF NOT EXISTS idx_psp_config_tenant_id
    ON psp_config (tenant_id);
