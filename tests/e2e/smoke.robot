*** Settings ***
Documentation    Payment Gateway smoke checks — fast critical-path validation via the API edge.
...              These tests run on every PR. Only the ``noop`` PSP is exercised — real PSP
...              (Stripe/PayPal) sandboxes are never called from this suite.
Resource         ../../../labs64.io-tests/resources/payment_gateway.resource
Suite Teardown   Delete All Sessions

*** Test Cases ***
Allow public access to payment definitions (200)
    [Documentation]    GET /payment-definitions without credentials returns 200.
    [Tags]    payment-gateway    smoke
    Create Unauthenticated Payment Gateway Session    pg-public
    ${response}=    List Payment Definitions    pg-public
    Response Status Should Be    ${response}    200
    Response Should Contain Key    ${response}    items

Allow read-scoped access to payment providers (200)
    [Documentation]    GET /payment-providers with payment-provider:read token returns 200.
    [Tags]    payment-gateway    smoke
    Create Payment Gateway Session With Scope    payment-provider:read    pg-read
    ${response}=    List Payment Providers    pg-read
    Response Status Should Be    ${response}    200
    Response Should Contain Key    ${response}    items

Reject missing authorization for payment providers (401)
    [Documentation]    GET /payment-providers without auth header returns 401.
    [Tags]    payment-gateway    smoke    auth
    Create Unauthenticated Payment Gateway Session    pg-no-auth
    ${response}=    List Payment Providers    pg-no-auth
    Response Status Should Be    ${response}    401
