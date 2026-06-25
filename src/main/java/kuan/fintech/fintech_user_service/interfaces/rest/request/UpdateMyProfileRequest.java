package kuan.fintech.fintech_user_service.interfaces.rest.request;

import java.time.LocalDate;
import java.util.Map;

public record UpdateMyProfileRequest(
        String fullName,
        String phoneNumber,
        LocalDate dateOfBirth,
        AddressRequest address,
        Map<String, Object> preferences
) {
}
