INSERT INTO tenant_psp_configs (id, tenant_id, provider, config, created_at, updated_at, version)
SELECT
    'dddddddd-dddd-dddd-dddd-dddddddddddd', 'tenant-1', 'stripe',
    '{"apiKey":"sk_test_123"}', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (
    SELECT 1 FROM tenant_psp_configs WHERE id = 'dddddddd-dddd-dddd-dddd-dddddddddddd'
);

INSERT INTO tenant_psp_configs (id, tenant_id, provider, config, created_at, updated_at, version)
SELECT
    'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee', 'tenant-1', 'paypal',
    '{"clientId":"client-123","clientSecret":"secret-123"}', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (
    SELECT 1 FROM tenant_psp_configs WHERE id = 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee'
);

INSERT INTO tenant_psp_configs (id, tenant_id, provider, config, created_at, updated_at, version)
SELECT
    'ffffffff-ffff-ffff-ffff-ffffffffffff', 'tenant-1', 'noop',
    '{}', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (
    SELECT 1 FROM tenant_psp_configs WHERE id = 'ffffffff-ffff-ffff-ffff-ffffffffffff'
);

INSERT INTO payments (id, tenant_id, payment_method_id, provider, status, recurring, purchase_order,
                      billing_info, shipping_info, extra, psp_data, psp_reference, next_action_details,
                      next_action_type, created_at, updated_at, version)
SELECT
    '11111111-1111-1111-1111-111111111111', 'tenant-1', 'card', 'stripe', 'ACTIVE', FALSE,
    '{"orderId":"order-1001"}', '{"name":"Jane Doe"}', '{"country":"US"}', '{"note":"test payment"}',
    '{"intent":"pi_1001"}', 'pi_1001', '{"redirectUrl":"https://example.com/redirect"}', 'REDIRECT',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (
    SELECT 1 FROM payments WHERE id = '11111111-1111-1111-1111-111111111111'
);

INSERT INTO payments (id, tenant_id, payment_method_id, provider, status, recurring, purchase_order,
                      billing_info, shipping_info, extra, psp_data, psp_reference, next_action_details,
                      next_action_type, created_at, updated_at, version)
SELECT
    '22222222-2222-2222-2222-222222222222', 'tenant-1', 'paypal', 'paypal', 'CLOSED', TRUE,
    '{"orderId":"order-2001"}', '{"name":"John Doe"}', '{"country":"DE"}', '{"note":"subscription"}',
    '{"order":"pp_2001"}', 'pp_2001', NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (
    SELECT 1 FROM payments WHERE id = '22222222-2222-2222-2222-222222222222'
);

INSERT INTO payment_transactions (id, payment_id, status, psp_data, psp_reference, created_at, updated_at, version)
SELECT
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '11111111-1111-1111-1111-111111111111', 'PENDING',
    '{"charge":"ch_1001"}', 'ch_1001', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (
    SELECT 1 FROM payment_transactions WHERE id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'
);

INSERT INTO payment_transactions (id, payment_id, status, psp_data, psp_reference, created_at, updated_at, version)
SELECT
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', '11111111-1111-1111-1111-111111111111', 'SUCCESS',
    '{"charge":"ch_1002"}', 'ch_1002', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (
    SELECT 1 FROM payment_transactions WHERE id = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'
);

INSERT INTO payment_transactions (id, payment_id, status, psp_data, psp_reference, created_at, updated_at, version)
SELECT
    'cccccccc-cccc-cccc-cccc-cccccccccccc', '22222222-2222-2222-2222-222222222222', 'SUCCESS',
    '{"capture":"pp_tx_2001"}', 'pp_tx_2001', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (
    SELECT 1 FROM payment_transactions WHERE id = 'cccccccc-cccc-cccc-cccc-cccccccccccc'
);

INSERT INTO idempotency_keys (id, payment_id, tenant_id, idempotency_key, transaction_id, created_at)
SELECT
    '99999999-9999-9999-9999-999999999999', '11111111-1111-1111-1111-111111111111', 'tenant-1',
    'idem-1001', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM idempotency_keys WHERE id = '99999999-9999-9999-9999-999999999999'
);
