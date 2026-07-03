-- Next actions are tied to a concrete payment transaction attempt.
-- They are intentionally not stored on payment because recurring payments may
-- produce many attempts, each with its own redirect/3DS/action lifecycle.
CREATE TABLE IF NOT EXISTS payment_next_action
(
    id             UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    transaction_id UUID        NOT NULL REFERENCES payment_transaction (id) ON DELETE CASCADE,
    type           TEXT        NOT NULL,
    details        JSONB,
    expires_at     TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TRIGGER trg_payment_next_action_updated_at
    BEFORE UPDATE
    ON payment_next_action
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

-- A transaction has at most one active next action record.
CREATE UNIQUE INDEX IF NOT EXISTS ux_payment_next_action_transaction ON payment_next_action (transaction_id);
CREATE INDEX IF NOT EXISTS ix_payment_next_action_expires_at ON payment_next_action (expires_at);
