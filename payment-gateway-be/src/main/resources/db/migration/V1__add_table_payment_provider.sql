-- Tenant-owned payment provider overlay.
-- YAML defines globally supported payment providers; this table stores the
-- tenant-owned active state and PSP configuration for each supported provider.
CREATE TABLE IF NOT EXISTS payment_provider
(
    id          UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    tenant_id   TEXT        NOT NULL,
    provider    TEXT        NOT NULL,
    active      BOOLEAN     NOT NULL DEFAULT true,
    name        TEXT        NOT NULL,
    description TEXT,
    config      JSONB       NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Auto-update updated_at on every modification
CREATE TRIGGER trg_payment_provider_updated_at
    BEFORE UPDATE
    ON payment_provider
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

-- One tenant-owned payment provider overlay per globally supported payment provider.
CREATE UNIQUE INDEX IF NOT EXISTS ux_payment_provider_tenant_provider ON payment_provider (tenant_id, provider);
CREATE UNIQUE INDEX IF NOT EXISTS ux_payment_provider_tenant_id ON payment_provider (tenant_id, id);
CREATE INDEX IF NOT EXISTS ix_payment_provider_tenant ON payment_provider (tenant_id);
CREATE INDEX IF NOT EXISTS ix_payment_provider_tenant_active ON payment_provider (tenant_id, active);
