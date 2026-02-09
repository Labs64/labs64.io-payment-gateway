# Usage

All endpoints require a valid JWT. The tenant is derived from the `tenant_id` (or `tenantId`) claim.
All requests may include `X-Correlation-ID` for tracing.

## Initiate payment
`POST /payments`
```json
{
  "paymentMethodId": "card",
  "purchaseOrder": { "amount": 1000, "currency": "USD" },
  "recurring": false,
  "billingInfo": { "email": "buyer@example.com" }
}
```

## Execute payment
`POST /payments/{paymentId}/pay` with header `Idempotency-Key`.

## Fetch payment / transaction
- `GET /payments/{paymentId}`
- `GET /transactions/{transactionId}`

## Webhooks (async completion)
- Stripe: `POST /webhooks/stripe`
- PayPal: `POST /webhooks/paypal`
