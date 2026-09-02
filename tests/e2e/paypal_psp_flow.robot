*** Settings ***
Documentation    PayPal PSP integration through the public Payment Gateway API and an external HTTP stub.
...              Covers OAuth and Orders SDK contracts, idempotency, browser capture/cancel,
...              verified webhooks, capture fallback, failure mapping, and terminal-state protection.
Resource         ../../../labs64.io-tests/resources/payment_gateway.resource
Resource         ../../../labs64.io-tests/resources/psp_stub.resource
Test Setup       Prepare PayPal PSP Stub Test
Test Teardown    Delete All Sessions

*** Variables ***
${PAYPAL_PSP_SCOPES}                  payment-provider:write payment:write payment:read payment:pay payment-transaction:read
${PAYPAL_PSP_SESSION}                 paypal-psp-e2e
${PAYPAL_PUBLIC_SESSION}              paypal-psp-public
${PAYPAL_PROVIDER}                    paypal
${PAYPAL_CLIENT_ID}                   paypal-client-id
${PAYPAL_CLIENT_SECRET}               paypal-client-secret
${PAYPAL_ERROR_CLIENT_ID}             paypal-error-client-id
${PAYPAL_ERROR_CLIENT_SECRET}         paypal-error-client-secret
${PAYPAL_INCOMPLETE_CLIENT_ID}        paypal-incomplete-client-id
${PAYPAL_INCOMPLETE_CLIENT_SECRET}    paypal-incomplete-client-secret
${PAYPAL_BASIC_AUTHORIZATION}         cGF5cGFsLWNsaWVudC1pZDpwYXlwYWwtY2xpZW50LXNlY3JldA==
${PAYPAL_ACCESS_TOKEN}                paypal-access-token
${PAYPAL_WEBHOOK_ID}                  paypal-webhook-id
${PAYPAL_ORDER_ID}                    PAYPAL-ORDER-PSP-STUB
${PAYPAL_CAPTURE_ID}                  PAYPAL-CAPTURE-PSP-STUB
${PAYPAL_RETURN_URL}                  https://checkout.example.test/payment/return
${PAYPAL_CANCEL_URL}                  https://checkout.example.test/payment/cancel
${PAYPAL_MAPPING_DIRECTORY}           ${CURDIR}/../psp-stub/wiremock/mappings

*** Test Cases ***
Create pending PayPal order with expected OAuth and SDK requests
    [Documentation]    The real PayPal SDK authenticates and sends amount, item, identity, callback, and idempotency fields.
    [Tags]    payment-gateway    regression    psp-stub    paypal
    ${payment_id}    ${transaction_id}    ${pay_result}    ${return_url}    ${cancel_url}    ${checkout_session_id}=
    ...    Create Pending PayPal Payment

    Should Be Equal As Strings    ${pay_result}[payment][id]    ${payment_id}
    Should Be Equal As Strings    ${pay_result}[payment][status]    READY
    Should Be Equal As Strings    ${pay_result}[paymentTransaction][status]    PENDING
    Should Be Equal As Strings    ${pay_result}[paymentTransaction][statusDetails][code]    AWAITING_CUSTOMER
    Should Be Equal As Strings    ${pay_result}[paymentTransaction][pspData][orderId]    ${PAYPAL_ORDER_ID}
    Should Be Equal As Strings    ${pay_result}[paymentTransaction][pspData][status]    CREATED
    Should Be Equal As Strings    ${pay_result}[nextAction][type]    REDIRECT
    Should Be Equal As Strings
    ...    ${pay_result}[nextAction][details][url]
    ...    https://www.sandbox.paypal.test/checkoutnow?token=${PAYPAL_ORDER_ID}

    ${oauth_request}=    Get Only PSP API Request    POST    ${PAYPAL_OAUTH_API_PATH}
    PayPal OAuth Request Should Match Credentials    ${oauth_request}    ${PAYPAL_BASIC_AUTHORIZATION}
    ${transaction_response}=    Get Payment Transaction    ${transaction_id}    ${PAYPAL_PSP_SESSION}
    Response Status Should Be    ${transaction_response}    200
    Should Be Equal As Strings    ${transaction_response.json()}[paymentId]    ${payment_id}
    Should Be Equal As Strings    ${transaction_response.json()}[status]    PENDING

Replay PayPal pay without a second order or OAuth call
    [Documentation]    Replaying one client idempotency key returns the same transaction without calling PayPal again.
    [Tags]    payment-gateway    regression    psp-stub    paypal    idempotency
    ${payment_id}=    Create PayPal Payment
    ${checkout}=    Build PayPal Checkout Request
    ${idempotency_key}=    Evaluate    str(uuid.uuid4())    modules=uuid

    ${first_response}=    Pay Payment
    ...    ${payment_id}
    ...    ${PAYPAL_PSP_SESSION}
    ...    idempotency_key=${idempotency_key}
    ...    checkout=${checkout}
    Response Status Should Be    ${first_response}    200
    ${first_result}=    Set Variable    ${first_response.json()}
    ${transaction_id}=    Set Variable    ${first_result}[paymentTransaction][id]
    Should Be Equal As Strings    ${first_result}[paymentTransaction][status]    PENDING
    Should Be Equal As Strings    ${first_result}[nextAction][type]    REDIRECT

    ${replay_response}=    Pay Payment
    ...    ${payment_id}
    ...    ${PAYPAL_PSP_SESSION}
    ...    idempotency_key=${idempotency_key}
    ...    checkout=${checkout}
    Response Status Should Be    ${replay_response}    200
    Should Be Equal    ${replay_response.json()}    ${first_result}
    Should Be Equal As Strings    ${replay_response.json()}[paymentTransaction][id]    ${transaction_id}
    PSP API Request Count Should Be    POST    ${PAYPAL_ORDERS_API_PATH}    1
    PSP API Request Count Should Be    POST    ${PAYPAL_OAUTH_API_PATH}    1
    ${transactions_response}=    List Payment Transactions    ${payment_id}    ${PAYPAL_PSP_SESSION}
    Response Status Should Be    ${transactions_response}    200
    Length Should Be    ${transactions_response.json()}[items]    1

Keep PayPal transaction pending when create-order result is unavailable
    [Documentation]    A PayPal Orders 5xx leaves the transaction non-terminal with normalized diagnostic details.
    [Tags]    payment-gateway    regression    psp-stub    paypal    error-path
    ${payment_id}=    Create PayPal Payment    ${PAYPAL_ERROR_CLIENT_ID}    ${PAYPAL_ERROR_CLIENT_SECRET}
    ${checkout}=    Build PayPal Checkout Request
    ${response}=    Pay Payment    ${payment_id}    ${PAYPAL_PSP_SESSION}    checkout=${checkout}
    Response Status Should Be    ${response}    200
    Should Be Equal As Strings    ${response.json()}[paymentTransaction][status]    PENDING
    Should Be Equal As Strings    ${response.json()}[paymentTransaction][statusDetails][code]    PROVIDER_UNAVAILABLE
    Should Contain    ${response.json()}[paymentTransaction][statusDetails][message]    definitive payment result
    Should Be Equal    ${response.json()}[nextAction]    ${None}
    PSP API Request Count Should Be    POST    ${PAYPAL_ORDERS_API_PATH}    1
    PayPal Payment Should Have Status    ${payment_id}    READY

Reject incomplete successful PayPal order response
    [Documentation]    A PayPal 201 without an approval link leaves the transaction PENDING with an invalid-response detail.
    [Tags]    payment-gateway    regression    psp-stub    paypal    error-path
    ${payment_id}=    Create PayPal Payment    ${PAYPAL_INCOMPLETE_CLIENT_ID}    ${PAYPAL_INCOMPLETE_CLIENT_SECRET}
    ${checkout}=    Build PayPal Checkout Request
    ${response}=    Pay Payment    ${payment_id}    ${PAYPAL_PSP_SESSION}    checkout=${checkout}
    Response Status Should Be    ${response}    200
    Should Be Equal As Strings    ${response.json()}[paymentTransaction][status]    PENDING
    Should Be Equal As Strings    ${response.json()}[paymentTransaction][statusDetails][code]    PROVIDER_RESPONSE_INVALID
    Should Contain    ${response.json()}[paymentTransaction][statusDetails][message]    invalid response
    Should Be Equal    ${response.json()}[nextAction]    ${None}
    PSP API Request Count Should Be    POST    ${PAYPAL_ORDERS_API_PATH}    1
    PayPal Payment Should Have Status    ${payment_id}    READY

Complete PayPal browser return through capture API
    [Documentation]    The public return callback captures the PayPal order and closes a successful payment.
    [Tags]    payment-gateway    regression    psp-stub    paypal    checkout-return
    ${payment_id}    ${transaction_id}    ${pay_result}    ${return_url}    ${cancel_url}    ${checkout_session_id}=
    ...    Create Pending PayPal Payment
    ${query}=    Create Dictionary    token=${PAYPAL_ORDER_ID}    PayerID=PAYPAL-PAYER-PSP-STUB
    ${response}=    Return Provider Checkout Session
    ...    ${PAYPAL_PROVIDER}
    ...    ${checkout_session_id}
    ...    ${query}
    ...    ${PAYPAL_PUBLIC_SESSION}
    Response Status Should Be    ${response}    302
    Should Be Equal As Strings    ${response.headers}[Location]    ${PAYPAL_RETURN_URL}?sessionId=${checkout_session_id}
    PayPal Transaction Should Have Status    ${transaction_id}    SUCCESS
    PayPal Payment Should Have Status    ${payment_id}    CLOSED

    ${capture_path}=    Set Variable    ${PAYPAL_ORDERS_API_PATH}/${PAYPAL_ORDER_ID}/capture
    PSP API Request Count Should Be    POST    ${capture_path}    1
    ${capture_request}=    Get Only PSP API Request    POST    ${capture_path}
    PayPal Capture Request Should Match Transaction    ${capture_request}    ${transaction_id}    ${PAYPAL_ACCESS_TOKEN}
    PSP API Request Count Should Be    POST    ${PAYPAL_OAUTH_API_PATH}    2
    ${confirmation}=    Get Checkout Session Confirmation    ${checkout_session_id}    ${PAYPAL_PUBLIC_SESSION}
    Response Status Should Be    ${confirmation}    200
    Should Be Equal As Strings    ${confirmation.json()}[payment][status]    CLOSED
    Should Be Equal As Strings    ${confirmation.json()}[paymentTransaction][status]    SUCCESS

Reject PayPal browser return with mismatched order token
    [Documentation]    A callback for another PayPal order is rejected safely without capture or transaction mutation.
    [Tags]    payment-gateway    regression    psp-stub    paypal    checkout-return    security
    ${payment_id}    ${transaction_id}    ${pay_result}    ${return_url}    ${cancel_url}    ${checkout_session_id}=
    ...    Create Pending PayPal Payment
    ${query}=    Create Dictionary    token=PAYPAL-ORDER-MISMATCH
    ${response}=    Return Provider Checkout Session
    ...    ${PAYPAL_PROVIDER}
    ...    ${checkout_session_id}
    ...    ${query}
    ...    ${PAYPAL_PUBLIC_SESSION}
    Response Status Should Be    ${response}    302
    Should Be Equal As Strings    ${response.headers}[Location]    /
    ${transaction_response}=    Get Payment Transaction    ${transaction_id}    ${PAYPAL_PSP_SESSION}
    Response Status Should Be    ${transaction_response}    200
    Should Be Equal As Strings    ${transaction_response.json()}[status]    PENDING
    Should Be Equal As Strings    ${transaction_response.json()}[statusDetails][code]    PENDING
    Should Be Equal As Strings    ${transaction_response.json()}[pspData][orderId]    ${PAYPAL_ORDER_ID}
    PayPal Payment Should Have Status    ${payment_id}    READY
    PSP API Request Count Should Be    POST    ${PAYPAL_ORDERS_API_PATH}/PAYPAL-ORDER-MISMATCH/capture    0
    PSP API Request Count Should Be    POST    ${PAYPAL_ORDERS_API_PATH}/${PAYPAL_ORDER_ID}/capture    0
    PSP API Request Count Should Be    POST    ${PAYPAL_OAUTH_API_PATH}    1

Keep PayPal browser return pending when capture result is unavailable
    [Documentation]    A PayPal capture 5xx redirects safely and leaves the transaction non-terminal.
    [Tags]    payment-gateway    regression    psp-stub    paypal    checkout-return    error-path
    ${payment_id}    ${transaction_id}    ${pay_result}    ${return_url}    ${cancel_url}    ${checkout_session_id}=
    ...    Create Pending PayPal Payment
    Register PayPal Capture Error Stub    ${PAYPAL_ORDER_ID}
    ${query}=    Create Dictionary    token=${PAYPAL_ORDER_ID}
    ${response}=    Return Provider Checkout Session
    ...    ${PAYPAL_PROVIDER}
    ...    ${checkout_session_id}
    ...    ${query}
    ...    ${PAYPAL_PUBLIC_SESSION}
    Response Status Should Be    ${response}    302
    Should Be Equal As Strings    ${response.headers}[Location]    /
    ${transaction_response}=    Get Payment Transaction    ${transaction_id}    ${PAYPAL_PSP_SESSION}
    Response Status Should Be    ${transaction_response}    200
    Should Be Equal As Strings    ${transaction_response.json()}[status]    PENDING
    Should Be Equal As Strings    ${transaction_response.json()}[statusDetails][code]    PROVIDER_UNAVAILABLE
    Should Contain    ${transaction_response.json()}[statusDetails][message]    definitive payment result
    PayPal Payment Should Have Status    ${payment_id}    READY

Cancel PayPal browser checkout
    [Documentation]    The public cancel callback records FAILED/CANCELLED without calling PayPal capture.
    [Tags]    payment-gateway    regression    psp-stub    paypal    checkout-cancel
    ${payment_id}    ${transaction_id}    ${pay_result}    ${return_url}    ${cancel_url}    ${checkout_session_id}=
    ...    Create Pending PayPal Payment
    ${query}=    Create Dictionary    token=${PAYPAL_ORDER_ID}
    ${response}=    Cancel Provider Checkout Session
    ...    ${PAYPAL_PROVIDER}
    ...    ${checkout_session_id}
    ...    ${PAYPAL_PUBLIC_SESSION}
    ...    ${query}
    Response Status Should Be    ${response}    302
    Should Be Equal As Strings    ${response.headers}[Location]    ${PAYPAL_CANCEL_URL}?sessionId=${checkout_session_id}
    ${transaction_response}=    Get Payment Transaction    ${transaction_id}    ${PAYPAL_PSP_SESSION}
    Response Status Should Be    ${transaction_response}    200
    Should Be Equal As Strings    ${transaction_response.json()}[status]    FAILED
    Should Be Equal As Strings    ${transaction_response.json()}[statusDetails][code]    CANCELLED
    Should Be Equal As Strings    ${transaction_response.json()}[pspData][orderId]    ${PAYPAL_ORDER_ID}
    PayPal Payment Should Have Status    ${payment_id}    READY
    PSP API Request Count Should Be    POST    ${PAYPAL_ORDERS_API_PATH}/${PAYPAL_ORDER_ID}/capture    0
    PSP API Request Count Should Be    POST    ${PAYPAL_OAUTH_API_PATH}    1

Capture approved PayPal order from verified webhook
    [Documentation]    CHECKOUT.ORDER.APPROVED is verified and captures independently of the browser return.
    [Tags]    payment-gateway    regression    psp-stub    paypal    webhook
    ${payment_id}    ${transaction_id}    ${pay_result}    ${return_url}    ${cancel_url}    ${checkout_session_id}=
    ...    Create Pending PayPal Payment
    ${response}    ${event_id}=    Send Verified PayPal Webhook
    ...    ${transaction_id}
    ...    CHECKOUT.ORDER.APPROVED
    ...    APPROVED
    ...    order_resource=${TRUE}
    Response Status Should Be    ${response}    200
    PayPal Transaction Should Have Status    ${transaction_id}    SUCCESS
    PayPal Payment Should Have Status    ${payment_id}    CLOSED
    ${transaction_response}=    Get Payment Transaction    ${transaction_id}    ${PAYPAL_PSP_SESSION}
    Should Be Equal As Strings    ${transaction_response.json()}[pspData][eventType]    CHECKOUT.ORDER.APPROVED
    Should Be Equal As Strings    ${transaction_response.json()}[pspData][orderId]    ${PAYPAL_ORDER_ID}
    Should Be Equal As Strings    ${transaction_response.json()}[pspData][captureStatus]    COMPLETED

    ${verify_request}=    Get Only PSP API Request    POST    ${PAYPAL_WEBHOOK_VERIFY_API_PATH}
    PayPal Webhook Verification Request Should Match
    ...    ${verify_request}
    ...    ${event_id}
    ...    CHECKOUT.ORDER.APPROVED
    ...    ${PAYPAL_WEBHOOK_ID}
    ...    valid-signature
    ${capture_path}=    Set Variable    ${PAYPAL_ORDERS_API_PATH}/${PAYPAL_ORDER_ID}/capture
    ${capture_request}=    Get Only PSP API Request    POST    ${capture_path}
    PayPal Capture Request Should Match Transaction    ${capture_request}    ${transaction_id}    ${PAYPAL_ACCESS_TOKEN}
    PSP API Request Count Should Be    POST    ${PAYPAL_OAUTH_API_PATH}    3

Complete payment from verified PayPal capture webhook
    [Documentation]    A verified PAYMENT.CAPTURE.COMPLETED event succeeds without another capture API call.
    [Tags]    payment-gateway    regression    psp-stub    paypal    webhook
    ${payment_id}    ${transaction_id}    ${pay_result}    ${return_url}    ${cancel_url}    ${checkout_session_id}=
    ...    Create Pending PayPal Payment
    ${response}    ${event_id}=    Send Verified PayPal Webhook
    ...    ${transaction_id}
    ...    PAYMENT.CAPTURE.COMPLETED
    ...    COMPLETED
    Response Status Should Be    ${response}    200
    PayPal Transaction Should Have Status    ${transaction_id}    SUCCESS
    PayPal Payment Should Have Status    ${payment_id}    CLOSED
    ${transaction_response}=    Get Payment Transaction    ${transaction_id}    ${PAYPAL_PSP_SESSION}
    Should Be Equal As Strings    ${transaction_response.json()}[pspData][eventId]    ${event_id}
    Should Be Equal As Strings    ${transaction_response.json()}[pspData][eventType]    PAYMENT.CAPTURE.COMPLETED
    Should Be Equal As Strings    ${transaction_response.json()}[pspData][resourceId]    ${PAYPAL_CAPTURE_ID}
    Should Be Equal As Strings    ${transaction_response.json()}[pspData][paypalStatus]    COMPLETED
    PSP API Request Count Should Be    POST    ${PAYPAL_WEBHOOK_VERIFY_API_PATH}    1
    PSP API Request Count Should Be    POST    ${PAYPAL_ORDERS_API_PATH}/${PAYPAL_ORDER_ID}/capture    0

Fail payment from verified PayPal denied webhook
    [Documentation]    A verified PAYMENT.CAPTURE.DENIED event persists FAILED and leaves the payment reusable.
    [Tags]    payment-gateway    regression    psp-stub    paypal    webhook
    ${payment_id}    ${transaction_id}    ${pay_result}    ${return_url}    ${cancel_url}    ${checkout_session_id}=
    ...    Create Pending PayPal Payment
    ${response}    ${event_id}=    Send Verified PayPal Webhook
    ...    ${transaction_id}
    ...    PAYMENT.CAPTURE.DENIED
    ...    DENIED
    Response Status Should Be    ${response}    200
    ${transaction_response}=    Get Payment Transaction    ${transaction_id}    ${PAYPAL_PSP_SESSION}
    Response Status Should Be    ${transaction_response}    200
    Should Be Equal As Strings    ${transaction_response.json()}[status]    FAILED
    Should Be Equal As Strings    ${transaction_response.json()}[statusDetails][code]    DECLINED
    Should Be Equal As Strings    ${transaction_response.json()}[pspData][eventType]    PAYMENT.CAPTURE.DENIED
    PayPal Payment Should Have Status    ${payment_id}    READY
    PSP API Request Count Should Be    POST    ${PAYPAL_WEBHOOK_VERIFY_API_PATH}    1

Reject failed PayPal webhook verification without changing state
    [Documentation]    A PayPal verification FAILURE returns 400 and leaves the pending transaction unchanged.
    [Tags]    payment-gateway    regression    psp-stub    paypal    webhook    security
    ${payment_id}    ${transaction_id}    ${pay_result}    ${return_url}    ${cancel_url}    ${checkout_session_id}=
    ...    Create Pending PayPal Payment
    ${response}    ${event_id}=    Send Verified PayPal Webhook
    ...    ${transaction_id}
    ...    PAYMENT.CAPTURE.COMPLETED
    ...    COMPLETED
    ...    transmission_signature=invalid-signature
    Response Status Should Be    ${response}    400
    Should Be Equal As Strings    ${response.json()}[code]    VALIDATION_ERROR
    Should Contain    ${response.json()}[message]    PayPal webhook verification failed
    PayPal Transaction Should Have Status    ${transaction_id}    PENDING
    PayPal Payment Should Have Status    ${payment_id}    READY
    ${verify_request}=    Get Only PSP API Request    POST    ${PAYPAL_WEBHOOK_VERIFY_API_PATH}
    PayPal Webhook Verification Request Should Match
    ...    ${verify_request}
    ...    ${event_id}
    ...    PAYMENT.CAPTURE.COMPLETED
    ...    ${PAYPAL_WEBHOOK_ID}
    ...    invalid-signature

Ignore late PayPal failure after successful terminal webhook
    [Documentation]    A verified late failure cannot overwrite an already successful PayPal transaction.
    [Tags]    payment-gateway    regression    psp-stub    paypal    webhook    idempotency
    ${payment_id}    ${transaction_id}    ${pay_result}    ${return_url}    ${cancel_url}    ${checkout_session_id}=
    ...    Create Pending PayPal Payment
    ${success_response}    ${success_event_id}=    Send Verified PayPal Webhook
    ...    ${transaction_id}
    ...    PAYMENT.CAPTURE.COMPLETED
    ...    COMPLETED
    Response Status Should Be    ${success_response}    200
    ${failure_response}    ${failure_event_id}=    Send Verified PayPal Webhook
    ...    ${transaction_id}
    ...    PAYMENT.CAPTURE.DENIED
    ...    DENIED
    Response Status Should Be    ${failure_response}    200
    ${transaction_response}=    Get Payment Transaction    ${transaction_id}    ${PAYPAL_PSP_SESSION}
    Response Status Should Be    ${transaction_response}    200
    Should Be Equal As Strings    ${transaction_response.json()}[status]    SUCCESS
    Should Be Equal As Strings    ${transaction_response.json()}[pspData][eventId]    ${success_event_id}
    Should Be Equal As Strings    ${transaction_response.json()}[pspData][eventType]    PAYMENT.CAPTURE.COMPLETED
    PayPal Payment Should Have Status    ${payment_id}    CLOSED
    PSP API Request Count Should Be    POST    ${PAYPAL_WEBHOOK_VERIFY_API_PATH}    1

*** Keywords ***
Prepare PayPal PSP Stub Test
    Create Payment Gateway Session With Scope    ${PAYPAL_PSP_SCOPES}    ${PAYPAL_PSP_SESSION}
    Create Unauthenticated Payment Gateway Session    ${PAYPAL_PUBLIC_SESSION}
    PSP Stub Should Be Available
    Reset PSP Stub State
    Register PSP Stub Mappings From Directory    ${PAYPAL_MAPPING_DIRECTORY}    paypal-*.json

Build PayPal Checkout Request
    ${checkout}=    Create Dictionary
    ...    returnUrl=${PAYPAL_RETURN_URL}
    ...    cancelUrl=${PAYPAL_CANCEL_URL}
    RETURN    ${checkout}

Create PayPal Payment
    [Arguments]    ${client_id}=${PAYPAL_CLIENT_ID}    ${client_secret}=${PAYPAL_CLIENT_SECRET}
    ${provider_config}=    Create Dictionary
    ...    clientId=${client_id}
    ...    clientSecret=${client_secret}
    ...    environment=sandbox
    ...    webhookId=${PAYPAL_WEBHOOK_ID}
    ${provider_response}=    Create Payment Provider
    ...    ${PAYPAL_PSP_SESSION}
    ...    ${PAYPAL_PROVIDER}
    ...    ${TRUE}
    ...    ${provider_config}
    Response Status Should Be    ${provider_response}    200
    ${provider_id}=    Set Variable    ${provider_response.json()}[id]
    ${create_response}=    Create Valid Payment    ${provider_id}    ${PAYPAL_PSP_SESSION}
    Response Status Should Be    ${create_response}    201
    RETURN    ${create_response.json()}[id]

Create Pending PayPal Payment
    ${payment_id}=    Create PayPal Payment
    ${checkout}=    Build PayPal Checkout Request
    ${pay_response}=    Pay Payment
    ...    ${payment_id}
    ...    ${PAYPAL_PSP_SESSION}
    ...    checkout=${checkout}
    Response Status Should Be    ${pay_response}    200
    ${pay_result}=    Set Variable    ${pay_response.json()}
    ${transaction_id}=    Set Variable    ${pay_result}[paymentTransaction][id]
    Should Be Equal As Strings    ${pay_result}[paymentTransaction][status]    PENDING
    ${order_request}=    Get Only PSP API Request    POST    ${PAYPAL_ORDERS_API_PATH}
    ${return_url}    ${cancel_url}=    PayPal Create Order Request Should Match Payment
    ...    ${order_request}
    ...    ${payment_id}
    ...    ${transaction_id}
    ...    ${PAYPAL_ACCESS_TOKEN}
    ${checkout_session_id}=    Get Checkout Session Id From Provider Callback URL    ${return_url}
    RETURN    ${payment_id}    ${transaction_id}    ${pay_result}    ${return_url}    ${cancel_url}    ${checkout_session_id}

Build PayPal Webhook Payload
    [Arguments]    ${transaction_id}    ${event_type}    ${status}    ${order_resource}=${FALSE}
    ${event_id}=    Evaluate    "WH-" + uuid.uuid4().hex    modules=uuid
    IF    ${order_resource}
        ${purchase_unit}=    Create Dictionary    invoice_id=${transaction_id}
        ${purchase_units}=    Create List    ${purchase_unit}
        ${resource}=    Create Dictionary
        ...    id=${PAYPAL_ORDER_ID}
        ...    status=${status}
        ...    purchase_units=${purchase_units}
    ELSE
        ${related_ids}=    Create Dictionary    order_id=${PAYPAL_ORDER_ID}
        ${supplementary_data}=    Create Dictionary    related_ids=${related_ids}
        ${resource}=    Create Dictionary
        ...    id=${PAYPAL_CAPTURE_ID}
        ...    status=${status}
        ...    invoice_id=${transaction_id}
        ...    supplementary_data=${supplementary_data}
    END
    ${event}=    Create Dictionary
    ...    id=${event_id}
    ...    event_type=${event_type}
    ...    resource=${resource}
    ${payload}=    Evaluate    json.dumps($event, separators=(",", ":"))    modules=json
    RETURN    ${payload}    ${event_id}

Send Verified PayPal Webhook
    [Arguments]    ${transaction_id}    ${event_type}    ${status}    ${order_resource}=${FALSE}    ${transmission_signature}=valid-signature
    ${payload}    ${event_id}=    Build PayPal Webhook Payload
    ...    ${transaction_id}
    ...    ${event_type}
    ...    ${status}
    ...    ${order_resource}
    ${headers}=    Create Dictionary
    ...    Content-Type=application/json
    ...    PAYPAL-AUTH-ALGO=SHA256withRSA
    ...    PAYPAL-CERT-URL=https://api.paypal.test/certs/psp-stub.pem
    ...    PAYPAL-TRANSMISSION-ID=transmission-psp-stub
    ...    PAYPAL-TRANSMISSION-SIG=${transmission_signature}
    ...    PAYPAL-TRANSMISSION-TIME=2026-09-01T00:00:00Z
    ${response}=    Send Provider Webhook
    ...    ${PAYPAL_PROVIDER}
    ...    ${payload}
    ...    ${headers}
    ...    ${PAYPAL_PUBLIC_SESSION}
    RETURN    ${response}    ${event_id}

PayPal Transaction Should Have Status
    [Arguments]    ${transaction_id}    ${expected_status}
    ${response}=    Get Payment Transaction    ${transaction_id}    ${PAYPAL_PSP_SESSION}
    Response Status Should Be    ${response}    200
    Should Be Equal As Strings    ${response.json()}[status]    ${expected_status}

PayPal Payment Should Have Status
    [Arguments]    ${payment_id}    ${expected_status}
    ${response}=    Get Payment    ${payment_id}    ${PAYPAL_PSP_SESSION}
    Response Status Should Be    ${response}    200
    Should Be Equal As Strings    ${response.json()}[status]    ${expected_status}
