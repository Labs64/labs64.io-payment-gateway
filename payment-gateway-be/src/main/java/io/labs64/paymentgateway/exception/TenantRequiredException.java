package io.labs64.paymentgateway.exception;

import org.springframework.http.HttpStatus;

import io.labs64.paymentgateway.v1.model.ErrorCode;

/**
 * Exception thrown when tenantId is missing from JWT / request context.
 */
public class TenantRequiredException extends ApiException {

    public TenantRequiredException() {
        super("Missing tenantId in request context.", HttpStatus.BAD_REQUEST, ErrorCode.MISSING_TENANT_ID);
    }
}
