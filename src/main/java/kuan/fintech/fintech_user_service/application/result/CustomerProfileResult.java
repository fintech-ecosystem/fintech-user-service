package kuan.fintech.fintech_user_service.application.result;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import kuan.fintech.fintech_user_service.domain.model.CustomerStatus;
import kuan.fintech.fintech_user_service.domain.model.CustomerType;
import kuan.fintech.fintech_user_service.domain.model.ProfileStatus;

public record CustomerProfileResult(
        UUID customerId,
        UUID authUserId,
        String email,
        String phoneNumber,
        String fullName,
        LocalDate dateOfBirth,
        CustomerType customerType,
        CustomerStatus status,
        ProfileStatus profileStatus,
        String addressLine1,
        String addressLine2,
        String city,
        String country,
        Instant createdAt,
        Instant updatedAt
) {
}
