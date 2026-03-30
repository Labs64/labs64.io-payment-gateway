package io.labs64.paymentgateway.psp;

import java.util.Map;

import io.labs64.paymentgateway.entity.TransactionEntity.TransactionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Response from PSP after payment execution.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PspPaymentResponse {

    /**
     * Transaction status after PSP processing.
     */
    private TransactionStatus status;

    /**
     * PSP-specific reference data (e.g., chargeId, paymentIntentId).
     */
    private Map<String, Object> pspData;

    /**
     * Failure code if the payment failed.
     */
    private String failureCode;

    /**
     * Failure message if the payment failed.
     */
    private String failureMessage;

    /**
     * Optional next action required (e.g., 3DS redirect).
     */
    private PspNextAction nextAction;

    /**
     * Whether the payment requires async completion (webhook).
     */
    private boolean asyncCompletion;
}
