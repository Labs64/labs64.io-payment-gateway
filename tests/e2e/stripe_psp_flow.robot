*** Settings ***
Documentation    Stripe PSP integration through the public Payment Gateway API and an external HTTP stub.
...              Covers the SDK HTTP contract, idempotency, upstream failures, browser callbacks,
...              signed webhooks, persisted state transitions, and terminal-state protection.
Resource         ../../../labs64.io-tests/resources/payment_gateway.resource
Resource         ../../../labs64.io-tests/resources/psp_stub.resource
Test Setup       Prepare Stripe PSP Stub Test
Test Teardown    Delete All Sessions

*** Variables ***
${STRIPE_PSP_SCOPES}              payment-provider:write payment:write payment:read payment:pay payment-transaction:read
${STRIPE_PSP_SESSION}             stripe-psp-e2e
${STRIPE_PUBLIC_SESSION}          stripe-psp-public
${STRIPE_PROVIDER}                stripe
${STRIPE_API_KEY}                 sk_test_psp_stub
${STRIPE_ERROR_API_KEY}           sk_test_psp_stub_error
${STRIPE_INCOMPLETE_API_KEY}      sk_test_psp_stub_incomplete
${STRIPE_WEBHOOK_SECRET}          whsec_test_psp_stub
${STRIPE_CHECKOUT_SESSION_ID}     cs_test_psp_stub
${STRIPE_RETURN_URL}              https://checkout.example.test/payment/return
${STRIPE_CANCEL_URL}              https://checkout.example.test/payment/cancel
${STRIPE_MAPPING_DIRECTORY}       ${CURDIR}/../psp-stub/wiremock/mappings

*** Test Cases ***
Create pending Stripe Checkout with the expected SDK request
    [Documentation]    The real Stripe SDK sends the material amount, currency, metadata, callbacks, auth, and idempotency fields.
    [Tags]    payment-gateway    regression    psp-stub    stripe
    ${payment_id}    ${transaction_id}    ${pay_result}    ${success_url}    ${cancel_url}    ${checkout_session_id}=
    ...    Create Pending Stripe Payment

    Should Be Equal As Strings    ${pay_result}[payment][id]    ${payment_id}
    Should Be Equal As Strings    ${pay_result}[payment][status]    READY
    Should Be Equal As Strings    ${pay_result}[paymentTransaction][status]    PENDING
    Should Be Equal As Strings    ${pay_result}[paymentTransaction][statusDetails][code]    AWAITING_CUSTOMER
    Should Be Equal As Strings    ${pay_result}[paymentTransaction][pspData][checkoutSessionId]    ${STRIPE_CHECKOUT_SESSION_ID}
    Should Be Equal As Strings    ${pay_result}[nextAction][type]    REDIRECT
    Should Be Equal As Strings    ${pay_result}[nextAction][details][url]    https://checkout.stripe.test/c/pay/${STRIPE_CHECKOUT_SESSION_ID}
    Should Contain    ${success_url}    /checkout-sessions/${checkout_session_id}/return
    Should Contain    ${cancel_url}    /checkout-sessions/${checkout_session_id}/cancel

    ${transaction_response}=    Get Payment Transaction    ${transaction_id}    ${STRIPE_PSP_SESSION}
    Response Status Should Be    ${transaction_response}    200
    Should Be Equal As Strings    ${transaction_response.json()}[paymentId]    ${payment_id}
    Should Be Equal As Strings    ${transaction_response.json()}[status]    PENDING

Replay Stripe pay without a second PSP call
    [Documentation]    Replaying one client idempotency key returns the same transaction and creates only one Stripe session.
    [Tags]    payment-gateway    regression    psp-stub    stripe    idempotency
    ${payment_id}=    Create Stripe Payment
    ${checkout}=    Build Stripe Checkout Request
    ${idempotency_key}=    Evaluate    str(uuid.uuid4())    modules=uuid

    ${first_response}=    Pay Payment
    ...    ${payment_id}
    ...    ${STRIPE_PSP_SESSION}
    ...    idempotency_key=${idempotency_key}
    ...    checkout=${checkout}
    Response Status Should Be    ${first_response}    200
    ${first_result}=    Set Variable    ${first_response.json()}
    ${transaction_id}=    Set Variable    ${first_result}[paymentTransaction][id]
    Should Be Equal As Strings    ${first_result}[paymentTransaction][status]    PENDING
    Should Be Equal As Strings    ${first_result}[nextAction][type]    REDIRECT

    ${replay_response}=    Pay Payment
    ...    ${payment_id}
    ...    ${STRIPE_PSP_SESSION}
    ...    idempotency_key=${idempotency_key}
    ...    checkout=${checkout}
    Response Status Should Be    ${replay_response}    200
    Should Be Equal    ${replay_response.json()}    ${first_result}
    Should Be Equal As Strings    ${replay_response.json()}[paymentTransaction][id]    ${transaction_id}

    Stripe Checkout Session Create Request Count Should Be    1
    ${stripe_request}=    Get Only Stripe Checkout Session Create Request
    Stripe Checkout Request Should Match Payment    ${stripe_request}    ${transaction_id}    ${STRIPE_API_KEY}
    ${transactions_response}=    List Payment Transactions    ${payment_id}    ${STRIPE_PSP_SESSION}
    Response Status Should Be    ${transactions_response}    200
    Length Should Be    ${transactions_response.json()}[items]    1

Keep Stripe transaction pending when upstream result is unavailable
    [Documentation]    A Stripe 5xx leaves the transaction non-terminal with normalized diagnostic details.
    [Tags]    payment-gateway    regression    psp-stub    stripe    error-path
    ${payment_id}=    Create Stripe Payment    ${STRIPE_ERROR_API_KEY}
    ${checkout}=    Build Stripe Checkout Request
    ${response}=    Pay Payment    ${payment_id}    ${STRIPE_PSP_SESSION}    checkout=${checkout}
    Response Status Should Be    ${response}    200
    Should Be Equal As Strings    ${response.json()}[paymentTransaction][status]    PENDING
    Should Be Equal As Strings    ${response.json()}[paymentTransaction][statusDetails][code]    PROVIDER_UNAVAILABLE
    Should Contain    ${response.json()}[paymentTransaction][statusDetails][message]    definitive payment result
    Should Be Equal    ${response.json()}[nextAction]    ${None}
    Stripe Checkout Session Create Request Count Should Be    1
    Payment Should Have Status    ${payment_id}    READY

Reject incomplete successful Stripe response
    [Documentation]    A syntactically valid Stripe 200 without a hosted checkout URL is treated as an upstream contract failure.
    [Tags]    payment-gateway    regression    psp-stub    stripe    error-path
    ${payment_id}=    Create Stripe Payment    ${STRIPE_INCOMPLETE_API_KEY}
    ${checkout}=    Build Stripe Checkout Request
    ${response}=    Pay Payment    ${payment_id}    ${STRIPE_PSP_SESSION}    checkout=${checkout}
    Response Status Should Be    ${response}    200
    Should Be Equal As Strings    ${response.json()}[paymentTransaction][status]    PENDING
    Should Be Equal As Strings    ${response.json()}[paymentTransaction][statusDetails][code]    PROVIDER_RESPONSE_INVALID
    Should Contain    ${response.json()}[paymentTransaction][statusDetails][message]    invalid response
    Should Be Equal    ${response.json()}[nextAction]    ${None}
    Stripe Checkout Session Create Request Count Should Be    1
    Payment Should Have Status    ${payment_id}    READY

Complete paid Stripe browser return
    [Documentation]    The public return callback retrieves Stripe state, closes the payment, persists SUCCESS, and redirects safely.
    [Tags]    payment-gateway    regression    psp-stub    stripe    checkout-return
    ${payment_id}    ${transaction_id}    ${pay_result}    ${success_url}    ${cancel_url}    ${checkout_session_id}=
    ...    Create Pending Stripe Payment
    Register Stripe Checkout Session Retrieval Stub    ${STRIPE_CHECKOUT_SESSION_ID}    ${transaction_id}
    ${query}=    Create Dictionary    stripeSessionId=${STRIPE_CHECKOUT_SESSION_ID}
    ${response}=    Return Provider Checkout Session
    ...    ${STRIPE_PROVIDER}
    ...    ${checkout_session_id}
    ...    ${query}
    ...    ${STRIPE_PUBLIC_SESSION}
    Response Status Should Be    ${response}    302
    Should Be Equal As Strings    ${response.headers}[Location]    ${STRIPE_RETURN_URL}?sessionId=${checkout_session_id}

    Transaction Should Have Status    ${transaction_id}    SUCCESS
    Payment Should Have Status    ${payment_id}    CLOSED
    Stripe API Request Count Should Be    GET    /v1/checkout/sessions/${STRIPE_CHECKOUT_SESSION_ID}    1
    ${confirmation}=    Get Checkout Session Confirmation    ${checkout_session_id}    ${STRIPE_PUBLIC_SESSION}
    Response Status Should Be    ${confirmation}    200
    Should Be Equal As Strings    ${confirmation.json()}[sessionId]    ${checkout_session_id}
    Should Be Equal As Strings    ${confirmation.json()}[payment][status]    CLOSED
    Should Be Equal As Strings    ${confirmation.json()}[paymentTransaction][status]    SUCCESS

Cancel Stripe browser checkout
    [Documentation]    The public cancel callback records FAILED/CANCELLED, keeps the payment reusable, and does not retrieve Stripe state.
    [Tags]    payment-gateway    regression    psp-stub    stripe    checkout-cancel
    ${payment_id}    ${transaction_id}    ${pay_result}    ${success_url}    ${cancel_url}    ${checkout_session_id}=
    ...    Create Pending Stripe Payment
    ${response}=    Cancel Provider Checkout Session
    ...    ${STRIPE_PROVIDER}
    ...    ${checkout_session_id}
    ...    ${STRIPE_PUBLIC_SESSION}
    Response Status Should Be    ${response}    302
    Should Be Equal As Strings    ${response.headers}[Location]    ${STRIPE_CANCEL_URL}?sessionId=${checkout_session_id}

    ${transaction_response}=    Get Payment Transaction    ${transaction_id}    ${STRIPE_PSP_SESSION}
    Response Status Should Be    ${transaction_response}    200
    Should Be Equal As Strings    ${transaction_response.json()}[status]    FAILED
    Should Be Equal As Strings    ${transaction_response.json()}[statusDetails][code]    CANCELLED
    Payment Should Have Status    ${payment_id}    READY
    Stripe API Request Count Should Be    GET    /v1/checkout/sessions/${STRIPE_CHECKOUT_SESSION_ID}    0

Complete unpaid browser return later from a signed Stripe webhook
    [Documentation]    An unpaid return stays PENDING, then a correctly signed completed event finalizes the transaction and payment.
    [Tags]    payment-gateway    regression    psp-stub    stripe    webhook
    ${payment_id}    ${transaction_id}    ${pay_result}    ${success_url}    ${cancel_url}    ${checkout_session_id}=
    ...    Create Pending Stripe Payment
    Register Stripe Checkout Session Retrieval Stub
    ...    ${STRIPE_CHECKOUT_SESSION_ID}
    ...    ${transaction_id}
    ...    payment_status=unpaid
    ...    status=open
    ${query}=    Create Dictionary    stripeSessionId=${STRIPE_CHECKOUT_SESSION_ID}
    ${return_response}=    Return Provider Checkout Session
    ...    ${STRIPE_PROVIDER}
    ...    ${checkout_session_id}
    ...    ${query}
    ...    ${STRIPE_PUBLIC_SESSION}
    Response Status Should Be    ${return_response}    302
    Transaction Should Have Status    ${transaction_id}    PENDING
    Payment Should Have Status    ${payment_id}    READY

    ${response}=    Send Signed Stripe Webhook
    ...    ${transaction_id}
    ...    checkout.session.completed
    ...    paid
    ...    complete
    Response Status Should Be    ${response}    200

    ${transaction_response}=    Get Payment Transaction    ${transaction_id}    ${STRIPE_PSP_SESSION}
    Response Status Should Be    ${transaction_response}    200
    Should Be Equal As Strings    ${transaction_response.json()}[status]    SUCCESS
    Should Be Equal As Strings    ${transaction_response.json()}[statusDetails][code]    COMPLETED
    Should Be Equal As Strings    ${transaction_response.json()}[pspData][eventType]    checkout.session.completed
    Should Be Equal As Strings    ${transaction_response.json()}[pspData][stripeObjectId]    ${STRIPE_CHECKOUT_SESSION_ID}
    Payment Should Have Status    ${payment_id}    CLOSED

Fail pending payment from a signed asynchronous Stripe webhook
    [Documentation]    A correctly signed async_payment_failed event persists FAILED while leaving the payment available for another attempt.
    [Tags]    payment-gateway    regression    psp-stub    stripe    webhook
    ${payment_id}    ${transaction_id}    ${pay_result}    ${success_url}    ${cancel_url}    ${checkout_session_id}=
    ...    Create Pending Stripe Payment
    ${response}=    Send Signed Stripe Webhook
    ...    ${transaction_id}
    ...    checkout.session.async_payment_failed
    ...    unpaid
    ...    complete
    Response Status Should Be    ${response}    200

    ${transaction_response}=    Get Payment Transaction    ${transaction_id}    ${STRIPE_PSP_SESSION}
    Response Status Should Be    ${transaction_response}    200
    Should Be Equal As Strings    ${transaction_response.json()}[status]    FAILED
    Should Be Equal As Strings    ${transaction_response.json()}[statusDetails][code]    PAYMENT_FAILED
    Should Be Equal As Strings    ${transaction_response.json()}[pspData][eventType]    checkout.session.async_payment_failed
    Payment Should Have Status    ${payment_id}    READY

Reject invalid Stripe webhook signature without changing state
    [Documentation]    The public endpoint rejects an unauthentic payload and the pending transaction remains unchanged.
    [Tags]    payment-gateway    regression    psp-stub    stripe    webhook    security
    ${payment_id}    ${transaction_id}    ${pay_result}    ${success_url}    ${cancel_url}    ${checkout_session_id}=
    ...    Create Pending Stripe Payment
    ${payload}=    Build Stripe Webhook Payload
    ...    ${transaction_id}
    ...    checkout.session.completed
    ...    paid
    ...    complete
    ${timestamp}=    Evaluate    int(time.time())    modules=time
    ${headers}=    Create Dictionary
    ...    Content-Type=application/json
    ...    Stripe-Signature=t=${timestamp},v1=invalid-signature
    ${response}=    Send Provider Webhook
    ...    ${STRIPE_PROVIDER}
    ...    ${payload}
    ...    ${headers}
    ...    ${STRIPE_PUBLIC_SESSION}
    Response Status Should Be    ${response}    400
    Should Be Equal As Strings    ${response.json()}[code]    VALIDATION_ERROR
    Transaction Should Have Status    ${transaction_id}    PENDING
    Payment Should Have Status    ${payment_id}    READY

Ignore late Stripe failure after successful terminal webhook
    [Documentation]    A replayed or out-of-order failure cannot overwrite an already successful transaction.
    [Tags]    payment-gateway    regression    psp-stub    stripe    webhook    idempotency
    ${payment_id}    ${transaction_id}    ${pay_result}    ${success_url}    ${cancel_url}    ${checkout_session_id}=
    ...    Create Pending Stripe Payment
    ${success_response}=    Send Signed Stripe Webhook
    ...    ${transaction_id}
    ...    checkout.session.completed
    ...    paid
    ...    complete
    Response Status Should Be    ${success_response}    200
    ${late_failure_response}=    Send Signed Stripe Webhook
    ...    ${transaction_id}
    ...    checkout.session.async_payment_failed
    ...    unpaid
    ...    complete
    Response Status Should Be    ${late_failure_response}    200

    ${transaction_response}=    Get Payment Transaction    ${transaction_id}    ${STRIPE_PSP_SESSION}
    Response Status Should Be    ${transaction_response}    200
    Should Be Equal As Strings    ${transaction_response.json()}[status]    SUCCESS
    Should Be Equal As Strings    ${transaction_response.json()}[pspData][eventType]    checkout.session.completed
    Payment Should Have Status    ${payment_id}    CLOSED

*** Keywords ***
Prepare Stripe PSP Stub Test
    Create Payment Gateway Session With Scope    ${STRIPE_PSP_SCOPES}    ${STRIPE_PSP_SESSION}
    Create Unauthenticated Payment Gateway Session    ${STRIPE_PUBLIC_SESSION}
    PSP Stub Should Be Available
    Reset PSP Stub State
    Register PSP Stub Mapping From File
    ...    ${STRIPE_MAPPING_DIRECTORY}/stripe-create-checkout-session.json
    Register PSP Stub Mapping From File
    ...    ${STRIPE_MAPPING_DIRECTORY}/stripe-create-checkout-session-error.json
    Register PSP Stub Mapping From File
    ...    ${STRIPE_MAPPING_DIRECTORY}/stripe-create-checkout-session-incomplete.json

Build Stripe Checkout Request
    ${checkout}=    Create Dictionary
    ...    returnUrl=${STRIPE_RETURN_URL}
    ...    cancelUrl=${STRIPE_CANCEL_URL}
    RETURN    ${checkout}

Create Stripe Payment
    [Arguments]    ${api_key}=${STRIPE_API_KEY}
    ${provider_config}=    Create Dictionary
    ...    secretKey=${api_key}
    ...    webhookSecret=${STRIPE_WEBHOOK_SECRET}
    ${provider_response}=    Create Payment Provider
    ...    ${STRIPE_PSP_SESSION}
    ...    ${STRIPE_PROVIDER}
    ...    ${TRUE}
    ...    ${provider_config}
    Response Status Should Be    ${provider_response}    200
    ${provider_id}=    Set Variable    ${provider_response.json()}[id]
    ${create_response}=    Create Valid Payment    ${provider_id}    ${STRIPE_PSP_SESSION}
    Response Status Should Be    ${create_response}    201
    RETURN    ${create_response.json()}[id]

Create Pending Stripe Payment
    ${payment_id}=    Create Stripe Payment
    ${checkout}=    Build Stripe Checkout Request
    ${pay_response}=    Pay Payment
    ...    ${payment_id}
    ...    ${STRIPE_PSP_SESSION}
    ...    checkout=${checkout}
    Response Status Should Be    ${pay_response}    200
    ${pay_result}=    Set Variable    ${pay_response.json()}
    ${transaction_id}=    Set Variable    ${pay_result}[paymentTransaction][id]
    Should Be Equal As Strings    ${pay_result}[paymentTransaction][status]    PENDING
    ${stripe_request}=    Get Only Stripe Checkout Session Create Request
    ${success_url}    ${cancel_url}=    Stripe Checkout Request Should Match Payment
    ...    ${stripe_request}
    ...    ${transaction_id}
    ...    ${STRIPE_API_KEY}
    ${checkout_session_id}=    Get Checkout Session Id From Provider Callback URL    ${success_url}
    RETURN    ${payment_id}    ${transaction_id}    ${pay_result}    ${success_url}    ${cancel_url}    ${checkout_session_id}

Build Stripe Webhook Payload
    [Arguments]    ${transaction_id}    ${event_type}    ${payment_status}    ${status}
    ${metadata}=    Create Dictionary    paymentTransactionId=${transaction_id}
    ${stripe_object}=    Create Dictionary
    ...    id=${STRIPE_CHECKOUT_SESSION_ID}
    ...    object=checkout.session
    ...    client_reference_id=${transaction_id}
    ...    metadata=${metadata}
    ...    payment_intent=pi_test_psp_stub
    ...    payment_status=${payment_status}
    ...    status=${status}
    ${data}=    Create Dictionary    object=${stripe_object}
    ${event_id}=    Evaluate    "evt_" + uuid.uuid4().hex    modules=uuid
    ${event}=    Create Dictionary
    ...    id=${event_id}
    ...    object=event
    ...    type=${event_type}
    ...    data=${data}
    ${payload}=    Evaluate    json.dumps($event, separators=(",", ":"))    modules=json
    RETURN    ${payload}

Create Stripe Webhook Signature
    [Arguments]    ${payload}    ${secret}=${STRIPE_WEBHOOK_SECRET}
    ${timestamp}=    Evaluate    int(time.time())    modules=time
    ${digest}=    Evaluate    hmac.new($secret.encode(), (str($timestamp) + "." + $payload).encode(), hashlib.sha256).hexdigest()    modules=hmac,hashlib
    RETURN    t=${timestamp},v1=${digest}

Send Signed Stripe Webhook
    [Arguments]    ${transaction_id}    ${event_type}    ${payment_status}    ${status}
    ${payload}=    Build Stripe Webhook Payload    ${transaction_id}    ${event_type}    ${payment_status}    ${status}
    ${signature}=    Create Stripe Webhook Signature    ${payload}
    ${headers}=    Create Dictionary
    ...    Content-Type=application/json
    ...    Stripe-Signature=${signature}
    ${response}=    Send Provider Webhook
    ...    ${STRIPE_PROVIDER}
    ...    ${payload}
    ...    ${headers}
    ...    ${STRIPE_PUBLIC_SESSION}
    RETURN    ${response}

Transaction Should Have Status
    [Arguments]    ${transaction_id}    ${expected_status}
    ${response}=    Get Payment Transaction    ${transaction_id}    ${STRIPE_PSP_SESSION}
    Response Status Should Be    ${response}    200
    Should Be Equal As Strings    ${response.json()}[status]    ${expected_status}

Payment Should Have Status
    [Arguments]    ${payment_id}    ${expected_status}
    ${response}=    Get Payment    ${payment_id}    ${STRIPE_PSP_SESSION}
    Response Status Should Be    ${response}    200
    Should Be Equal As Strings    ${response.json()}[status]    ${expected_status}
