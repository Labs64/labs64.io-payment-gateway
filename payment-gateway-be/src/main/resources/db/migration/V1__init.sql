CREATE TABLE payments (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL,
    payment_method_id VARCHAR(128) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    recurring BOOLEAN NOT NULL,
    purchase_order TEXT,
    billing_info TEXT,
    shipping_info TEXT,
    extra TEXT,
    psp_data TEXT,
    next_action_details TEXT,
    next_action_type VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT
);

CREATE TABLE payment_transactions (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    psp_data TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT,
    CONSTRAINT fk_payment_transactions_payment
        FOREIGN KEY (payment_id) REFERENCES payments(id)
);

CREATE TABLE tenant_psp_configs (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    config TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT,
    CONSTRAINT uq_tenant_psp UNIQUE (tenant_id, provider)
);

CREATE TABLE idempotency_keys (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    transaction_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_payment_idempotency UNIQUE (payment_id, idempotency_key)
);
