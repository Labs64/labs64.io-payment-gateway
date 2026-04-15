package io.labs64.paymentgateway.psp;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents a next action that must be performed by the client
 * (e.g., redirect for 3DS, popup, inline form).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PspNextAction {

    /**
     * Type of next action: "none", "redirect", "3ds-challenge".
     */
    private String type;

    /**
     * PSP-specific metadata for the next action.
     */
    private Map<String, Object> details;
}
