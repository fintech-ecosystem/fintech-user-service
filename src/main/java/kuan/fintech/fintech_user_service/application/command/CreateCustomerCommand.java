package kuan.fintech.fintech_user_service.application.command;

import java.util.UUID;
import kuan.fintech.fintech_user_service.domain.model.CustomerType;

public record CreateCustomerCommand(
        UUID authUserId,
        String email,
        CustomerType customerType,
        String correlationId,
        String causationId
) {
}
