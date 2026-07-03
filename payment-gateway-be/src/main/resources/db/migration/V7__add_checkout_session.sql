-- Checkout session stores user-facing continuation state for a concrete
-- payment transaction attempt, such as hosted checkout/approval redirects.
CREATE TABLE IF NOT EXISTS checkout_session
(
    id                     UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    tenant_id              TEXT        NOT NULL,
    payment_id             UUID        NOT NULL,
    payment_transaction_id UUID        NOT NULL REFERENCES payment_transaction (id) ON DELETE CASCADE,
    payload                JSONB,
    next_action            JSONB,
    expires_at             TIMESTAMPTZ,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (tenant_id, payment_id) REFERENCES payment (tenant_id, id) ON DELETE RESTRICT
);

CREATE TRIGGER trg_checkout_session_updated_at
    BEFORE UPDATE
    ON checkout_session
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

-- A payment transaction has at most one user-facing session.
CREATE UNIQUE INDEX IF NOT EXISTS ux_checkout_session_transaction
    ON checkout_session (payment_transaction_id);

CREATE UNIQUE INDEX IF NOT EXISTS ux_checkout_session_tenant_id
    ON checkout_session (tenant_id, id);

CREATE INDEX IF NOT EXISTS ix_checkout_session_payment_id
    ON checkout_session (payment_id);

CREATE INDEX IF NOT EXISTS ix_checkout_session_expires_at
    ON checkout_session (expires_at);
