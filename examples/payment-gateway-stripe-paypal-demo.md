# Payment Gateway Stripe + PayPal demo runbook

The intended demo story is:

1. Obtain one JWT with all Payment Gateway scopes.
2. Show the public provider definitions.
3. Configure a tenant Stripe provider and complete a card payment.
4. Configure a tenant PayPal provider and complete a sandbox payment.
5. Show that PSP-specific responses are normalized to the same
   `PaymentTransaction.SUCCESS` result.
6. Show that browser returns and webhooks can arrive concurrently without
   overwriting a terminal transaction.

> **Do not paste real secrets into this file, commit them, or show them on a
> shared screen.** The commands below read secrets without echoing them.

## 0. Pre-demo checklist

Complete this section first.

- The Kubernetes context points to the demo cluster.
- Payment Gateway, API Gateway, mock OIDC, PostgreSQL, Redis, and RabbitMQ are
   healthy.
- `gateway.localhost` resolves on the machine running the demo.
- `curl`, `jq`, `just`, and Stripe CLI are installed.
- Stripe is in a sandbox/test environment.
- PayPal is in Sandbox mode.
- Stripe CLI is authenticated to the same Stripe sandbox as the `sk_test_...`
   key used below.
- The PayPal webhook belongs to the same REST application as the PayPal
   `clientId` and `clientSecret`.
- A fresh PayPal personal sandbox buyer account is available.

### Verify the external Payment Gateway route

```bash {"terminalRows":"31"}
export PG_API="http://gateway.localhost/payment-gateway/api/v1"

curl --fail-with-body --silent --show-error \
  "$PG_API/payment-definitions" | jq .
```

Expected: HTTP 200 and definitions containing `stripe` and `paypal`.

### Temporary Payment Gateway egress workaround

The Payment Gateway pod must be able to call the Stripe and PayPal HTTPS APIs.
Until provider-specific egress rules are available in the Helm chart, apply the
following temporary NetworkPolicy patch once after deploying the demo cluster:

```bash
kubectl -n labs64io patch networkpolicy labs64io-payment-gateway \
  --type=json \
  --patch='[
    {
      "op": "add",
      "path": "/spec/egress/-",
      "value": {
        "to": [
          {
            "ipBlock": {
              "cidr": "0.0.0.0/0",
              "except": [
                "10.0.0.0/8",
                "172.16.0.0/12",
                "192.168.0.0/16",
                "169.254.0.0/16"
              ]
            }
          }
        ],
        "ports": [
          {
            "protocol": "TCP",
            "port": 443
          }
        ]
      }
    }
  ]'
```

This opens outbound TCP port 443 to public IPv4 addresses while excluding
private and link-local ranges. The patch is temporary and may be overwritten by
a Helm upgrade or a NetworkPolicy reconciliation.

> **TODO:** Remove this workaround after the Helm chart provides restricted,
> provider-aware egress for the Stripe and PayPal API endpoints.

## 1. Obtain the demo JWT

The protected demo operations require exactly these scopes:

- `payment-provider:read`
- `payment-provider:write`
- `payment:read`
- `payment:write`
- `payment:pay`
- `payment-transaction:read`

Generate an M2M token using the Helm repository helper:

```bash
export PG_SCOPES="payment-provider:read payment-provider:write payment:read payment:write payment:pay payment-transaction:read"

export PG_TOKEN="$(
  cd /workspaces/labs64.io-helm-charts &&
  just generate-jwt "$PG_SCOPES" | jq -r '.access_token'
)"

test -n "$PG_TOKEN" && test "$PG_TOKEN" != "null" &&
  echo "JWT is ready"
```

The mock token expires after one hour. Generate it immediately before running
the flow, and rerun this block if an API call starts returning HTTP 401.
The token contains tenant `t_mock`; clients must not send `X-Auth-*` headers
through the gateway.

Verify authorization:

```bash
curl --fail-with-body --silent --show-error \
  -H "Authorization: Bearer $PG_TOKEN" \
  "$PG_API/payment-providers?page=0&size=20" | jq .
```

Expected: HTTP 200. HTTP 403 usually means the JWT was created without one of
the required scopes.

## 2. Stripe setup

### 2.1 Get Stripe test credentials

1. Open the Stripe Dashboard and select the sandbox/test environment.
2. Open **Developers/Workbench -> API keys**.
3. Reveal the test secret key. It starts with `sk_test_`.
4. Authenticate Stripe CLI to the same sandbox:

```bash
stripe login
```

Only the server-side secret key is needed. No Stripe publishable key is used by
this hosted Checkout flow.

Official references:

- <https://docs.stripe.com/keys>
- <https://docs.stripe.com/stripe-cli/use-cli>
- <https://docs.stripe.com/testing>

### 2.2 Start Stripe webhook forwarding

Keep this command running in a dedicated terminal. It is intentionally written
on one line so it can be copied unchanged into Bash or PowerShell:

```sh
stripe listen --events "checkout.session.completed,checkout.session.async_payment_succeeded,checkout.session.async_payment_failed,checkout.session.expired" --forward-to "http://gateway.localhost/payment-gateway/api/v1/providers/stripe/webhooks"
```

Wait for:

```text
Ready! Your webhook signing secret is whsec_...
```

Use this CLI-generated `whsec_...` in the Stripe provider configuration.
It is not the same as a signing secret shown for a Dashboard webhook endpoint.
Restarting `stripe listen` can produce a new signing secret.

If Stripe CLI remains on `Getting ready`, rerun it with
`--log-level debug` and check VPN, proxy, firewall, and WebSocket access.

### 2.3 Create the tenant Stripe provider

Return to the main demo terminal:

```bash
read -rsp "Stripe sk_test_ secret key: " STRIPE_SECRET_KEY; echo
read -rsp "Stripe CLI whsec_ signing secret: " STRIPE_WEBHOOK_SECRET; echo

STRIPE_PROVIDER_RESPONSE="$(
  curl --fail-with-body --silent --show-error \
    -X POST "$PG_API/payment-providers" \
    -H "Authorization: Bearer $PG_TOKEN" \
    -H "Content-Type: application/json" \
    --data-binary @- <<JSON
{
  "provider": "stripe",
  "active": true,
  "name": "Stripe Demo",
  "description": "Stripe Checkout sandbox provider",
  "config": {
    "secretKey": "$STRIPE_SECRET_KEY",
    "webhookSecret": "$STRIPE_WEBHOOK_SECRET"
  }
}
JSON
)"

export STRIPE_PROVIDER_ID="$(
  jq -r '.id' <<<"$STRIPE_PROVIDER_RESPONSE"
)"

jq '{id, provider, active, name}' <<<"$STRIPE_PROVIDER_RESPONSE"
echo "STRIPE_PROVIDER_ID=$STRIPE_PROVIDER_ID"
```

Expected: a new provider UUID, `provider: "stripe"`, and `active: true`.
Creating a fresh provider for every rehearsal is acceptable and avoids
accidentally reusing an old Stripe CLI signing secret.

### 2.4 Create a Stripe payment

```bash
STRIPE_PAYMENT_RESPONSE="$(
  curl --fail-with-body --silent --show-error \
    -X POST "$PG_API/payments" \
    -H "Authorization: Bearer $PG_TOKEN" \
    -H "Content-Type: application/json" \
    --data-binary @- <<JSON
{
  "paymentProviderId": "$STRIPE_PROVIDER_ID",
  "purchaseOrder": {
    "currency": "EUR",
    "items": [
      {
        "name": "Labs64 Stripe Demo",
        "description": "Stripe provider demonstration",
        "sku": "DEMO-STRIPE-001",
        "price": 1999,
        "quantity": 1
      }
    ],
    "netAmount": 1999,
    "grossAmount": 1999,
    "taxAmount": 0
  },
  "billingInfo": {
    "firstName": "Jane",
    "lastName": "Demo",
    "email": "jane.demo@example.com",
    "country": "DE",
    "city": "Munich",
    "address1": "1 Demo Strasse",
    "postalCode": "80331"
  },
  "shippingInfo": {
    "firstName": "Jane",
    "lastName": "Demo",
    "email": "jane.demo@example.com",
    "country": "DE",
    "city": "Munich",
    "address1": "1 Demo Strasse",
    "postalCode": "80331"
  },
  "extra": {
    "demo": "stripe-paypal-runbook",
    "psp": "stripe"
  }
}
JSON
)"

export STRIPE_PAYMENT_ID="$(jq -r '.id' <<<"$STRIPE_PAYMENT_RESPONSE")"

jq '{id, paymentProviderId, provider, status, type, purchaseOrder}' \
  <<<"$STRIPE_PAYMENT_RESPONSE"
echo "STRIPE_PAYMENT_ID=$STRIPE_PAYMENT_ID"
```

Expected payment status: `READY`.

### 2.5 Call `/pay` and open Stripe Checkout

```bash
export STRIPE_IDEMPOTENCY_KEY="stripe-demo-$(date +%s)"

STRIPE_PAY_RESPONSE="$(
  curl --fail-with-body --silent --show-error \
    -X POST "$PG_API/payments/$STRIPE_PAYMENT_ID/pay" \
    -H "Authorization: Bearer $PG_TOKEN" \
    -H "Idempotency-Key: $STRIPE_IDEMPOTENCY_KEY" \
    -H "Content-Type: application/json" \
    --data-binary @- <<'JSON'
{
  "checkout": {
    "returnUrl": "https://example.com/payment/success?provider=stripe",
    "cancelUrl": "https://example.com/payment/cancel?provider=stripe"
  }
}
JSON
)"

export STRIPE_TRANSACTION_ID="$(
  jq -r '.paymentTransaction.id' <<<"$STRIPE_PAY_RESPONSE"
)"
export STRIPE_CHECKOUT_URL="$(
  jq -r '.nextAction.details.url' <<<"$STRIPE_PAY_RESPONSE"
)"

jq '{
  paymentStatus: .payment.status,
  transactionId: .paymentTransaction.id,
  transactionStatus: .paymentTransaction.status,
  nextAction: .nextAction
}' <<<"$STRIPE_PAY_RESPONSE"

echo "$STRIPE_CHECKOUT_URL"
```

Open the printed URL in a browser. Use the Stripe test card:

```text
Card number: 4242 4242 4242 4242
Expiry:     any future date
CVC:        any three digits
Name:       Jane Demo
```

After payment:

- the browser follows the Payment Gateway return callback;
- Stripe CLI prints `checkout.session.completed -> 200`;
- the final redirect reaches the supplied `returnUrl`;
- browser return and webhook may race, which is an intentional concurrency
   demonstration.

### 2.6 Show the Stripe result

```bash
curl --fail-with-body --silent --show-error \
  -H "Authorization: Bearer $PG_TOKEN" \
  "$PG_API/payment-transactions/$STRIPE_TRANSACTION_ID" |
  jq '{id, paymentId, status, statusDetails, pspData, updatedAt}'

curl --fail-with-body --silent --show-error \
  -H "Authorization: Bearer $PG_TOKEN" \
  "$PG_API/payments/$STRIPE_PAYMENT_ID" |
  jq '{id, provider, status, type, updatedAt}'
```

Expected:

- transaction status: `SUCCESS`;
- one-time payment status: `CLOSED`;
- Stripe event name can remain in `pspData`, while normalized
   `statusDetails.code` is `SUCCESS`.

## 3. PayPal setup

### 3.1 Get PayPal sandbox credentials and buyer

1. Open <https://developer.paypal.com/dashboard/>.
2. Select **Apps & Credentials -> Sandbox**.
3. Create or open a REST application.
4. Copy its `clientId` and `clientSecret`.
5. Open **Testing Tools -> Sandbox Accounts**.
6. Open a **Personal** sandbox account and copy its email and generated
   password. This is the buyer account used in the checkout browser.
7. Keep the Business account associated with the REST application as the
   seller.

Official references:

- <https://developer.paypal.com/api/rest/>
- <https://developer.paypal.com/tools/sandbox/accounts/>
- <https://developer.paypal.com/api/rest/webhooks/>

### 3.2 Make the PayPal webhook publicly reachable

PayPal cannot call `gateway.localhost` from its servers. Use one of:

1. The preferred public HTTPS hostname of a shared development cluster.
2. A temporary HTTPS tunnel that preserves or rewrites the upstream Host
   header to `gateway.localhost`.

Example with Cloudflare Tunnel:

```bash
cloudflared tunnel \
  --url http://gateway.localhost \
  --http-host-header gateway.localhost
```

Copy the generated `https://...trycloudflare.com` origin and append:

```text
/payment-gateway/api/v1/providers/paypal/webhooks
```

The complete public URL must return through the Payment Gateway route. A 404
from a GET request is not a useful webhook test because the endpoint accepts
POST only; verify delivery in the PayPal Webhooks Events dashboard.

### 3.3 Register the PayPal webhook

In the same PayPal Sandbox REST application:

1. Add the public webhook URL.
2. Select these events:
   - **Checkout order approved** — `CHECKOUT.ORDER.APPROVED` (mandatory);
   - **Checkout order completed** — `CHECKOUT.ORDER.COMPLETED`;
   - **Payment capture completed** — `PAYMENT.CAPTURE.COMPLETED`;
   - **Payment capture pending** — `PAYMENT.CAPTURE.PENDING`;
   - **Payment capture denied** — `PAYMENT.CAPTURE.DENIED`;
   - **Payment capture reversed** — `PAYMENT.CAPTURE.REVERSED`.

3. Save it and copy the webhook ID. This is the endpoint ID, not an event ID.

`CHECKOUT.ORDER.APPROVED` is the key event: the provider captures the approved
order even if the buyer closes the browser before returning. Selecting only
`Checkout order completed` is insufficient because that event occurs after
capture.

### 3.4 Create the tenant PayPal provider

```bash
read -rsp "PayPal sandbox client ID: " PAYPAL_CLIENT_ID; echo
read -rsp "PayPal sandbox client secret: " PAYPAL_CLIENT_SECRET; echo
read -rp "PayPal sandbox webhook ID: " PAYPAL_WEBHOOK_ID

PAYPAL_PROVIDER_RESPONSE="$(
  curl --fail-with-body --silent --show-error \
    -X POST "$PG_API/payment-providers" \
    -H "Authorization: Bearer $PG_TOKEN" \
    -H "Content-Type: application/json" \
    --data-binary @- <<JSON
{
  "provider": "paypal",
  "active": true,
  "name": "PayPal Demo",
  "description": "PayPal Checkout sandbox provider",
  "config": {
    "clientId": "$PAYPAL_CLIENT_ID",
    "clientSecret": "$PAYPAL_CLIENT_SECRET",
    "environment": "sandbox",
    "webhookId": "$PAYPAL_WEBHOOK_ID"
  }
}
JSON
)"

export PAYPAL_PROVIDER_ID="$(jq -r '.id' <<<"$PAYPAL_PROVIDER_RESPONSE")"

jq '{id, provider, active, name}' <<<"$PAYPAL_PROVIDER_RESPONSE"
echo "PAYPAL_PROVIDER_ID=$PAYPAL_PROVIDER_ID"
```

### 3.5 Create a PayPal payment

```bash
PAYPAL_PAYMENT_RESPONSE="$(
  curl --fail-with-body --silent --show-error \
    -X POST "$PG_API/payments" \
    -H "Authorization: Bearer $PG_TOKEN" \
    -H "Content-Type: application/json" \
    --data-binary @- <<JSON
{
  "paymentProviderId": "$PAYPAL_PROVIDER_ID",
  "purchaseOrder": {
    "currency": "EUR",
    "items": [
      {
        "name": "Labs64 PayPal Demo",
        "description": "PayPal provider demonstration",
        "sku": "DEMO-PAYPAL-001",
        "price": 2499,
        "quantity": 1
      }
    ],
    "netAmount": 2499,
    "grossAmount": 2499,
    "taxAmount": 0
  },
  "billingInfo": {
    "firstName": "John",
    "lastName": "Demo",
    "email": "john.demo@example.com",
    "country": "DE",
    "city": "Berlin",
    "address1": "1 Demo Platz",
    "postalCode": "10115"
  },
  "shippingInfo": {
    "firstName": "John",
    "lastName": "Demo",
    "email": "john.demo@example.com",
    "country": "DE",
    "city": "Berlin",
    "address1": "1 Demo Platz",
    "postalCode": "10115"
  },
  "extra": {
    "demo": "stripe-paypal-runbook",
    "psp": "paypal"
  }
}
JSON
)"

export PAYPAL_PAYMENT_ID="$(jq -r '.id' <<<"$PAYPAL_PAYMENT_RESPONSE")"

jq '{id, paymentProviderId, provider, status, type, purchaseOrder}' \
  <<<"$PAYPAL_PAYMENT_RESPONSE"
echo "PAYPAL_PAYMENT_ID=$PAYPAL_PAYMENT_ID"
```

Expected payment status: `READY`.

### 3.6 Call `/pay` and approve PayPal Checkout

```bash
export PAYPAL_IDEMPOTENCY_KEY="paypal-demo-$(date +%s)"

PAYPAL_PAY_RESPONSE="$(
  curl --fail-with-body --silent --show-error \
    -X POST "$PG_API/payments/$PAYPAL_PAYMENT_ID/pay" \
    -H "Authorization: Bearer $PG_TOKEN" \
    -H "Idempotency-Key: $PAYPAL_IDEMPOTENCY_KEY" \
    -H "Content-Type: application/json" \
    --data-binary @- <<'JSON'
{
  "checkout": {
    "returnUrl": "https://example.com/payment/success?provider=paypal",
    "cancelUrl": "https://example.com/payment/cancel?provider=paypal"
  }
}
JSON
)"

export PAYPAL_TRANSACTION_ID="$(
  jq -r '.paymentTransaction.id' <<<"$PAYPAL_PAY_RESPONSE"
)"
export PAYPAL_CHECKOUT_URL="$(
  jq -r '.nextAction.details.url' <<<"$PAYPAL_PAY_RESPONSE"
)"

jq '{
  paymentStatus: .payment.status,
  transactionId: .paymentTransaction.id,
  transactionStatus: .paymentTransaction.status,
  nextAction: .nextAction
}' <<<"$PAYPAL_PAY_RESPONSE"

echo "$PAYPAL_CHECKOUT_URL"
```

Open the printed URL and sign in with the **Personal PayPal sandbox buyer**
email and password. Approve the order.

This flow demonstrates that:

- PG created the PayPal order with `intent=CAPTURE`;
- PG stored its transaction UUID in PayPal `invoice_id`;
- the browser return and `CHECKOUT.ORDER.APPROVED` webhook can both attempt
   capture;
- both use the transaction UUID as the PayPal idempotency key;
- PG verifies the webhook against PayPal using the configured `webhookId`;
- PG applies the provider result under a transaction lock.

### 3.7 Show the PayPal result

```bash
curl --fail-with-body --silent --show-error \
  -H "Authorization: Bearer $PG_TOKEN" \
  "$PG_API/payment-transactions/$PAYPAL_TRANSACTION_ID" |
  jq '{id, paymentId, status, statusDetails, pspData, updatedAt}'

curl --fail-with-body --silent --show-error \
  -H "Authorization: Bearer $PG_TOKEN" \
  "$PG_API/payments/$PAYPAL_PAYMENT_ID" |
  jq '{id, provider, status, type, updatedAt}'
```

Expected:

- transaction status: `SUCCESS`;
- one-time payment status: `CLOSED`;
- PayPal order/capture data remains in `pspData`;
- the external behavior is the same normalized gateway result as Stripe.

Optionally open the PayPal Sandbox Webhooks Events dashboard, select the REST
application, and show the successful delivery. PayPal also allows resending a
recent event from that dashboard.

## 4. Architectural outcome

> Payment Gateway owns payment lifecycle, tenant isolation, idempotency,
> callback URLs, transaction locking, status normalization, and messaging.
> Stripe and PayPal own only PSP-specific API calls, payload mapping, webhook
> transaction extraction, and signature verification. Both providers therefore
> produce the same gateway-level payment and transaction contract.

The most important visible outcome is:

```text
Stripe -> PaymentTransaction.SUCCESS -> Payment.CLOSED
PayPal -> PaymentTransaction.SUCCESS -> Payment.CLOSED
```

## 5. Fast recovery guide

| Symptom | Fast check |
| --- | --- |
| HTTP 401 | Regenerate `PG_TOKEN`; mock tokens expire after one hour. |
| HTTP 403 | Generate the token with all six scopes from section 1. Do not send `X-Auth-*` headers. |
| Provider creation returns 400 | Check required config names and ensure Stripe values start with `sk_/rk_` and `whsec_`; PayPal environment must be `sandbox` or `live`. |
| `/pay` returns validation error | Both absolute `checkout.returnUrl` and `checkout.cancelUrl` are required. |
| PSP redirects to `localhost:8080` | Fix `PAYMENT_GATEWAY_PUBLIC_BASE_URL` and redeploy PG. |
| Stripe listener is silent | Confirm CLI and `secretKey` use the same sandbox, and complete the actual Checkout URL produced by PG. |
| Stripe webhook returns 400 | The provider has the wrong `whsec_`; copy the secret printed by the currently running `stripe listen`. |
| PayPal has no webhook delivery | The URL is not publicly reachable, belongs to another app/environment, or `CHECKOUT.ORDER.APPROVED` was not selected. |
| PayPal webhook returns 400 | Check `webhookId`, app credentials, Sandbox/Live environment, and tunnel header forwarding. |
| Transaction is still `PENDING` | Requery it after a few seconds and inspect Stripe CLI or the PayPal Webhooks Events dashboard. |
| Rehearsal payment is already closed | Create a new payment and use a new `Idempotency-Key`; do not call `/pay` again on a completed one-time payment. |

## 6. After the demo

```bash
unset PG_TOKEN
unset STRIPE_SECRET_KEY STRIPE_WEBHOOK_SECRET
unset PAYPAL_CLIENT_ID PAYPAL_CLIENT_SECRET PAYPAL_WEBHOOK_ID
unset STRIPE_PROVIDER_RESPONSE STRIPE_PAYMENT_RESPONSE STRIPE_PAY_RESPONSE
unset PAYPAL_PROVIDER_RESPONSE PAYPAL_PAYMENT_RESPONSE PAYPAL_PAY_RESPONSE
```

Stop Stripe CLI and the temporary PayPal HTTPS tunnel. Delete temporary tenant
providers later only if they have no associated payments; the API intentionally
prevents deleting providers already referenced by payment history.
