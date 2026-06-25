package kuan.fintech.fintech_user_service.application.command;

import java.util.UUID;

public record ChangeCustomerStatusCommand(
        UUID customerId,
        String reason,
        UUID changedBy,
        String correlationId,
        String causationId
) {
}
