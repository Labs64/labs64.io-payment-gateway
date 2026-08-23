*** Settings ***
Documentation    Synchronous payment lifecycle through the noop provider at the gateway edge.
...              Exercises authentication, controllers, services, provider routing, and persisted
...              payment and transaction state without calling an external PSP sandbox.
Resource         ../../../labs64.io-tests/resources/payment_gateway.resource
Test Setup       Create Payment Gateway Session With Scope    ${PAYMENT_FLOW_SCOPES}    ${PAYMENT_FLOW_SESSION}
Test Teardown    Delete All Sessions

*** Variables ***
${PAYMENT_FLOW_SCOPES}     payment-provider:read payment-provider:write payment:write payment:read payment:pay payment-transaction:read
${PAYMENT_FLOW_SESSION}    payment-flow-e2e

*** Test Cases ***
Complete synchronous payment through noop provider
    [Documentation]    A one-time payment moves from READY to CLOSED and persists one successful transaction.
    [Tags]    payment-gateway    regression
    ${provider_id}=    Ensure Active Noop Payment Provider    ${PAYMENT_FLOW_SESSION}

    ${create_response}=    Create Valid Payment    ${provider_id}    ${PAYMENT_FLOW_SESSION}
    Response Status Should Be    ${create_response}    201
    Response Should Contain Key    ${create_response}    id
    ${created_payment}=    Set Variable    ${create_response.json()}
    ${payment_id}=    Set Variable    ${created_payment}[id]
    Should Be Equal As Strings    ${created_payment}[paymentProviderId]    ${provider_id}
    Should Be Equal As Strings    ${created_payment}[provider]    noop
    Should Be Equal As Strings    ${created_payment}[status]    READY
    Should Be Equal As Strings    ${created_payment}[type]    ONE_TIME
    Should Be Equal As Strings    ${created_payment}[purchaseOrder][currency]    EUR
    Should Be Equal As Integers    ${created_payment}[purchaseOrder][grossAmount]    100

    ${pay_response}=    Pay Payment    ${payment_id}    ${PAYMENT_FLOW_SESSION}
    Response Status Should Be    ${pay_response}    200
    Response Should Contain Key    ${pay_response}    payment
    Response Should Contain Key    ${pay_response}    paymentTransaction
    ${pay_result}=    Set Variable    ${pay_response.json()}
    ${transaction_id}=    Set Variable    ${pay_result}[paymentTransaction][id]
    Should Not Be Empty    ${transaction_id}
    Should Be Equal As Strings    ${pay_result}[payment][id]    ${payment_id}
    Should Be Equal As Strings    ${pay_result}[payment][status]    CLOSED
    Should Be Equal As Strings    ${pay_result}[paymentTransaction][paymentId]    ${payment_id}
    Should Be Equal As Strings    ${pay_result}[paymentTransaction][status]    SUCCESS
    ${has_no_next_action}=    Evaluate    $pay_result.get("nextAction") is None
    Should Be True    ${has_no_next_action}

    ${payment_response}=    Get Payment    ${payment_id}    ${PAYMENT_FLOW_SESSION}
    Response Status Should Be    ${payment_response}    200
    Should Be Equal As Strings    ${payment_response.json()}[id]    ${payment_id}
    Should Be Equal As Strings    ${payment_response.json()}[status]    CLOSED

    ${transaction_response}=    Get Payment Transaction    ${transaction_id}    ${PAYMENT_FLOW_SESSION}
    Response Status Should Be    ${transaction_response}    200
    Should Be Equal As Strings    ${transaction_response.json()}[id]    ${transaction_id}
    Should Be Equal As Strings    ${transaction_response.json()}[paymentId]    ${payment_id}
    Should Be Equal As Strings    ${transaction_response.json()}[status]    SUCCESS

Replay identical pay request with the same idempotency key
    [Documentation]    Repeating the same pay command replays its response and creates no second transaction.
    [Tags]    payment-gateway    regression    idempotency
    ${provider_id}=    Ensure Active Noop Payment Provider    ${PAYMENT_FLOW_SESSION}
    ${create_response}=    Create Valid Payment    ${provider_id}    ${PAYMENT_FLOW_SESSION}
    Response Status Should Be    ${create_response}    201
    ${payment_id}=    Set Variable    ${create_response.json()}[id]
    ${idempotency_key}=    Evaluate    str(uuid.uuid4())    modules=uuid

    ${first_response}=    Pay Payment
    ...    ${payment_id}
    ...    ${PAYMENT_FLOW_SESSION}
    ...    idempotency_key=${idempotency_key}
    Response Status Should Be    ${first_response}    200
    ${first_result}=    Set Variable    ${first_response.json()}
    ${transaction_id}=    Set Variable    ${first_result}[paymentTransaction][id]
    Should Be Equal As Strings    ${first_result}[payment][status]    CLOSED
    Should Be Equal As Strings    ${first_result}[paymentTransaction][status]    SUCCESS

    ${replay_response}=    Pay Payment
    ...    ${payment_id}
    ...    ${PAYMENT_FLOW_SESSION}
    ...    idempotency_key=${idempotency_key}
    Response Status Should Be    ${replay_response}    200
    ${replay_result}=    Set Variable    ${replay_response.json()}
    Should Be Equal    ${replay_result}    ${first_result}
    Should Be Equal As Strings    ${replay_result}[paymentTransaction][id]    ${transaction_id}

    ${transactions_response}=    List Payment Transactions    ${payment_id}    ${PAYMENT_FLOW_SESSION}
    Response Status Should Be    ${transactions_response}    200
    ${transactions}=    Set Variable    ${transactions_response.json()}[items]
    Length Should Be    ${transactions}    1
    Should Be Equal As Strings    ${transactions}[0][id]    ${transaction_id}
