*** Settings ***
Documentation    Payment Gateway authorization scope matrix, asserted at the gateway edge
...              (Traefik authproxy + cerbos). Mirrors the manual scope-matrix validation this
...              suite replaces: listPaymentDefinitions is public; listPaymentProviders needs
...              payment-provider:read; a token missing that scope is denied (403) even though
...              it authenticates successfully.
...
...              The ``local-k8s-only`` tagged cases below additionally corroborate the HTTP
...              assertion against the authproxy's Authz Decision log (kubectl) for the
...              listPaymentProviders operation — a deliberate, narrow exception to this suite's
...              normal "no kubectl" rule. They call `Skip Unless Local Kubernetes` first and
...              skip cleanly outside the local k3d dev cluster, so CI never fails on them. See
...              AGENTS.md "Local-only pod-log corroboration" for the full rationale/scope, and
...              `resources/payment_gateway.resource` for why these mirror auditflow's edge
...              corroboration but not its backend-delivery corroboration.
Resource         ../../../labs64.io-tests/resources/payment_gateway.resource
Test Teardown    Delete All Sessions

*** Test Cases ***
Allow public access to payment definitions (200)
    [Documentation]    GET /payment-definitions succeeds without authentication.
    [Tags]    payment-gateway    regression    auth
    Create Unauthenticated Payment Gateway Session    pg-authz-public
    ${response}=    List Payment Definitions    pg-authz-public
    Response Status Should Be    ${response}    200

Allow public access despite unrelated scope (200)
    [Documentation]    GET /payment-definitions returns 200 even with an unrelated scope token.
    [Tags]    payment-gateway    regression    auth
    Create Payment Gateway Session With Scope    payment-provider:write    pg-authz-public-with-token
    ${response}=    List Payment Definitions    pg-authz-public-with-token
    Response Status Should Be    ${response}    200

Reject missing authorization for payment providers (401)
    [Documentation]    GET /payment-providers without auth header returns 401.
    [Tags]    payment-gateway    regression    auth
    Create Unauthenticated Payment Gateway Session    pg-authz-no-auth
    ${response}=    List Payment Providers    pg-authz-no-auth
    Response Status Should Be    ${response}    401

Reject malformed bearer token (401)
    [Documentation]    Invalid bearer credential returns 401.
    [Tags]    payment-gateway    regression    auth
    # Deliberately nonsensical test fixture credential — not a real credential or signing key.
    ${headers}=    Create Dictionary
    ...    Authorization=Bearer test-fixture-malformed-jwt
    ...    Accept=application/json
    ...    Content-Type=application/json
    Create Session    pg-bad-auth    ${PAYMENT_GATEWAY_BASE_URL}    headers=${headers}    verify=True
    ${response}=    List Payment Providers    pg-bad-auth
    Response Status Should Be    ${response}    401

Reject wrong scope for payment providers (403)
    [Documentation]    Token missing payment-provider:read scope is denied with 403 by cerbos.
    [Tags]    payment-gateway    regression    auth    tenant-isolation
    Create Payment Gateway Session With Scope    payment-provider:write    pg-authz-wrong-scope
    ${response}=    List Payment Providers    pg-authz-wrong-scope
    Response Status Should Be    ${response}    403

Allow read-scoped access to payment providers (200)
    [Documentation]    Token with payment-provider:read scope is allowed.
    [Tags]    payment-gateway    regression    auth
    Create Payment Gateway Session With Scope    payment-provider:read    pg-authz-correct-scope
    ${response}=    List Payment Providers    pg-authz-correct-scope
    Response Status Should Be    ${response}    200

Reject create provider with read-only scope (403)
    [Documentation]    POST /payment-providers requires write scope; read-only token is denied.
    [Tags]    payment-gateway    regression    auth    tenant-isolation
    Create Payment Gateway Session With Scope    payment-provider:read    pg-authz-create-denied
    ${response}=    Create Payment Provider    pg-authz-create-denied
    Response Status Should Be    ${response}    403

Reject get provider details with read-only scope (403)
    [Documentation]    GET /payment-providers/{id} requires write scope to prevent config leak.
    [Tags]    payment-gateway    regression    auth    tenant-isolation
    Create Payment Gateway Session With Scope    payment-provider:read    pg-authz-detail-denied
    ${response}=    Get Payment Provider    00000000-0000-0000-0000-000000000000    pg-authz-detail-denied
    Response Status Should Be    ${response}    403

Reject token with no scopes (403)
    [Documentation]    Token with empty scope set is denied with 403 by cerbos.
    [Tags]    payment-gateway    regression    auth
    Create Payment Gateway Session With Scope    no-access    pg-authz-no-scopes
    ${response}=    List Payment Providers    pg-authz-no-scopes
    Response Status Should Be    ${response}    403

Verify authproxy logs show 401 (local-k8s)
    [Documentation]    Authproxy logs must record no-token rejection.
    [Tags]    payment-gateway    regression    auth    local-k8s-only
    Skip Unless Local Kubernetes
    Create Unauthenticated Payment Gateway Session    pg-authz-no-auth-k8s
    ${response}=    List Payment Providers    pg-authz-no-auth-k8s
    Response Status Should Be    ${response}    401
    Authproxy Logs Should Show No-Token Rejection For Payment Providers

Verify Cerbos deny in authproxy logs for wrong scope (local-k8s)
    [Documentation]    Authproxy logs show Cerbos deny for missing read scope.
    [Tags]    payment-gateway    regression    auth    tenant-isolation    local-k8s-only
    Skip Unless Local Kubernetes
    Create Payment Gateway Session With Scope    payment-provider:write    pg-authz-wrong-scope-k8s
    ${response}=    List Payment Providers    pg-authz-wrong-scope-k8s
    Response Status Should Be    ${response}    403
    Authproxy Logs Should Show Authz Decision For Payment Providers    enforced-deny

Verify Cerbos allow in authproxy logs for read scope (local-k8s)
    [Documentation]    Authproxy logs show Cerbos allow for valid read scope.
    [Tags]    payment-gateway    regression    auth    local-k8s-only
    Skip Unless Local Kubernetes
    Create Payment Gateway Session With Scope    payment-provider:read    pg-authz-correct-scope-k8s
    ${response}=    List Payment Providers    pg-authz-correct-scope-k8s
    Response Status Should Be    ${response}    200
    Authproxy Logs Should Show Authz Decision For Payment Providers    enforced-allow

Verify Cerbos deny in authproxy logs for no scopes (local-k8s)
    [Documentation]    Authproxy logs show Cerbos deny for empty scope set.
    [Tags]    payment-gateway    regression    auth    local-k8s-only
    Skip Unless Local Kubernetes
    Create Payment Gateway Session With Scope    no-access    pg-authz-no-scopes-k8s
    ${response}=    List Payment Providers    pg-authz-no-scopes-k8s
    Response Status Should Be    ${response}    403
    Authproxy Logs Should Show Authz Decision For Payment Providers    enforced-deny

Reject missing read scope for payments (403)
    [Documentation]    GET /payments requires payment:read; token missing it is denied.
    [Tags]    payment-gateway    regression    auth    tenant-isolation
    Create Payment Gateway Session With Scope    payment:write    pg-authz-payments-wrong-scope
    ${response}=    List Payments    pg-authz-payments-wrong-scope
    Response Status Should Be    ${response}    403

Allow read-scoped access to payments (200)
    [Documentation]    GET /payments with payment:read scope is allowed.
    [Tags]    payment-gateway    regression    auth
    Create Payment Gateway Session With Scope    payment:read    pg-authz-payments-correct-scope
    ${response}=    List Payments    pg-authz-payments-correct-scope
    Response Status Should Be    ${response}    200

Reject read-only scope for creating payment (403)
    [Documentation]    POST /payments requires payment:write; read-only token is denied.
    [Tags]    payment-gateway    regression    auth    tenant-isolation
    Create Payment Gateway Session With Scope    payment:read    pg-authz-payments-create-denied
    ${response}=    Create Payment    pg-authz-payments-create-denied
    Response Status Should Be    ${response}    403
