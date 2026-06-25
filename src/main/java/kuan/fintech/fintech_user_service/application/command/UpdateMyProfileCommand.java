package kuan.fintech.fintech_user_service.application.command;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public record UpdateMyProfileCommand(
        UUID authUserId,
        String fullName,
        String phoneNumber,
        LocalDate dateOfBirth,
        String addressLine1,
        String addressLine2,
        String city,
        String country,
        Map<String, Object> preferences,
        UUID changedBy,
        String correlationId,
        String causationId
) {
}
