package kuan.fintech.fintech_user_service.interfaces.rest.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import kuan.fintech.fintech_user_service.domain.model.CustomerType;

public record CreateCustomerRequest(
        @NotNull UUID authUserId,
        @Email String email,
        CustomerType customerType
) {
}
