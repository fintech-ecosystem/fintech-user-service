package kuan.fintech.fintech_user_service.interfaces.rest;

import java.util.UUID;
import kuan.fintech.fintech_user_service.application.command.ChangeCustomerStatusCommand;
import kuan.fintech.fintech_user_service.application.service.CustomerApplicationService;
import kuan.fintech.fintech_user_service.domain.model.CustomerStatus;
import kuan.fintech.fintech_user_service.domain.model.ProfileStatus;
import kuan.fintech.fintech_user_service.interfaces.rest.request.StatusChangeRequest;
import kuan.fintech.fintech_user_service.interfaces.rest.response.ApiResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/users")
public class UserAdminController {
    private final CustomerApplicationService customerService;

    public UserAdminController(CustomerApplicationService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    public ApiResponse<?> listCustomers(
            @RequestParam(required = false) CustomerStatus status,
            @RequestParam(required = false) ProfileStatus profileStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId
    ) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ApiResponse.success(customerService.listCustomers(status, profileStatus, pageable), correlationId);
    }

    @GetMapping("/{customerId}")
    public ApiResponse<?> getCustomer(
            @PathVariable UUID customerId,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId
    ) {
        return ApiResponse.success(customerService.getCustomer(customerId), correlationId);
    }

    @PostMapping("/{customerId}/suspend")
    public ApiResponse<?> suspend(
            @PathVariable UUID customerId,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestHeader(value = "X-Causation-Id", required = false) String causationId,
            @RequestBody StatusChangeRequest request
    ) {
        return ApiResponse.success(customerService.suspendCustomer(command(customerId, request, correlationId, causationId)), correlationId);
    }

    @PostMapping("/{customerId}/reactivate")
    public ApiResponse<?> reactivate(
            @PathVariable UUID customerId,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestHeader(value = "X-Causation-Id", required = false) String causationId,
            @RequestBody StatusChangeRequest request
    ) {
        return ApiResponse.success(customerService.reactivateCustomer(command(customerId, request, correlationId, causationId)), correlationId);
    }

    @PostMapping("/{customerId}/close")
    public ApiResponse<?> close(
            @PathVariable UUID customerId,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestHeader(value = "X-Causation-Id", required = false) String causationId,
            @RequestBody StatusChangeRequest request
    ) {
        return ApiResponse.success(customerService.closeCustomer(command(customerId, request, correlationId, causationId)), correlationId);
    }

    private ChangeCustomerStatusCommand command(UUID customerId, StatusChangeRequest request, String correlationId, String causationId) {
        return new ChangeCustomerStatusCommand(customerId, request.reason(), request.changedBy(), correlationId, causationId);
    }
}
