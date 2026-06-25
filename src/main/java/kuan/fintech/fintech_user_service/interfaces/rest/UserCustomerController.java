package kuan.fintech.fintech_user_service.interfaces.rest;

import java.util.UUID;
import kuan.fintech.fintech_user_service.application.command.UpdateMyProfileCommand;
import kuan.fintech.fintech_user_service.application.service.CustomerApplicationService;
import kuan.fintech.fintech_user_service.domain.error.UserErrorCode;
import kuan.fintech.fintech_user_service.domain.error.UserServiceException;
import kuan.fintech.fintech_user_service.interfaces.rest.request.AddressRequest;
import kuan.fintech.fintech_user_service.interfaces.rest.request.UpdateMyProfileRequest;
import kuan.fintech.fintech_user_service.interfaces.rest.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users/me")
public class UserCustomerController {
    private final CustomerApplicationService customerService;

    public UserCustomerController(CustomerApplicationService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    public ApiResponse<?> getMe(
            @RequestHeader("X-Auth-User-Id") String authUserId,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId
    ) {
        return ApiResponse.success(customerService.getMyProfile(parseUuid(authUserId, "authUserId")), correlationId);
    }

    @PutMapping
    public ApiResponse<?> updateMe(
            @RequestHeader("X-Auth-User-Id") String authUserId,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestHeader(value = "X-Causation-Id", required = false) String causationId,
            @RequestBody UpdateMyProfileRequest request
    ) {
        AddressRequest address = request.address();
        return ApiResponse.success(customerService.updateMyProfile(new UpdateMyProfileCommand(
                parseUuid(authUserId, "authUserId"),
                request.fullName(),
                request.phoneNumber(),
                request.dateOfBirth(),
                address == null ? null : address.line1(),
                address == null ? null : address.line2(),
                address == null ? null : address.city(),
                address == null ? null : address.country(),
                request.preferences(),
                parseUuid(authUserId, "authUserId"),
                correlationId,
                causationId
        )), correlationId);
    }

    @GetMapping("/profile-status")
    public ApiResponse<?> getProfileStatus(
            @RequestHeader("X-Auth-User-Id") String authUserId,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId
    ) {
        return ApiResponse.success(customerService.getMyProfileStatus(parseUuid(authUserId, "authUserId")), correlationId);
    }

    private UUID parseUuid(String raw, String fieldName) {
        try {
            return UUID.fromString(raw);
        } catch (RuntimeException ex) {
            throw new UserServiceException(UserErrorCode.USER_INVALID_REQUEST, fieldName + " must be a UUID");
        }
    }
}
