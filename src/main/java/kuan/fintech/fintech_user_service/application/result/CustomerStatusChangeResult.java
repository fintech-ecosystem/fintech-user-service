package kuan.fintech.fintech_user_service.application.result;

import java.util.UUID;
import kuan.fintech.fintech_user_service.domain.model.CustomerStatus;

public record CustomerStatusChangeResult(
        UUID customerId,
        CustomerStatus status,
        String reason
) {
}
