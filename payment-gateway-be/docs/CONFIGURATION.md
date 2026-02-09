# Configuration

## application.yaml
Payment methods are configured under `payment.methods`:

```yaml
payment:
  methods:
    - id: card
      name: Card
      description: Card payments
      provider: stripe
      recurring: true
      currencies: [USD, EUR]
      countries: [US, DE]
```

## Tenant PSP configuration
Tenant PSP credentials are stored in the database via:

`POST /payment-methods/{paymentMethodId}`

Example request bodies:

### Stripe
```json
{
  "pspConfig": {
    "apiKey": "sk_test_xxx"
  }
}
```

### PayPal
```json
{
  "pspConfig": {
    "clientId": "client-id",
    "clientSecret": "client-secret",
    "mode": "sandbox"
  }
}
```

### No-Op
```json
{
  "pspConfig": {
    "enabled": true
  }
}
```
