package io.labs64.paymentgateway.psp;

import java.util.Map;
import java.util.UUID;

import io.labs64.paymentgateway.entity.TransactionEntity.TransactionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Result from verifying and processing a PSP webhook notification.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PspWebhookResult {

    /**
     * Whether the webhook signature was valid.
     */
    private boolean valid;

    /**
     * The payment ID this webhook relates to.
     */
    private UUID paymentId;

    /**
     * The updated transaction status.
     */
    private TransactionStatus transactionStatus;

    /**
     * Failure code if the transaction failed.
     */
    private String failureCode;

    /**
     * Failure message if the transaction failed.
     */
    private String failureMessage;

    /**
     * PSP-specific data from the webhook.
     */
    private Map<String, Object> pspData;
}
