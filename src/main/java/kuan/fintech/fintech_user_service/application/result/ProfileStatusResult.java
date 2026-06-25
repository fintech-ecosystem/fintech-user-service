package kuan.fintech.fintech_user_service.application.result;

import java.util.List;
import java.util.UUID;
import kuan.fintech.fintech_user_service.domain.model.ProfileStatus;

public record ProfileStatusResult(
        UUID customerId,
        ProfileStatus profileStatus,
        List<String> missingFields
) {
}
