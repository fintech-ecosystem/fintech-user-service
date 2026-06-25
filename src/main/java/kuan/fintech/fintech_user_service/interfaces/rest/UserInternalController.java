package kuan.fintech.fintech_user_service.interfaces.rest;

import jakarta.validation.Valid;
import java.util.UUID;
import kuan.fintech.fintech_user_service.application.command.CreateCustomerCommand;
import kuan.fintech.fintech_user_service.application.service.CustomerApplicationService;
import kuan.fintech.fintech_user_service.interfaces.rest.request.CreateCustomerRequest;
import kuan.fintech.fintech_user_service.interfaces.rest.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/users")
public class UserInternalController {
    private final CustomerApplicationService customerService;

    public UserInternalController(CustomerApplicationService customerService) {
        this.customerService = customerService;
    }

    @PostMapping
    public ApiResponse<?> createCustomer(
            @Valid @RequestBody CreateCustomerRequest request,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestHeader(value = "X-Causation-Id", required = false) String causationId
    ) {
        return ApiResponse.success(customerService.createCustomer(new CreateCustomerCommand(
                request.authUserId(),
                request.email(),
                request.customerType(),
                correlationId,
                causationId
        )), correlationId);
    }

    @GetMapping("/{customerId}")
    public ApiResponse<?> getCustomer(
            @PathVariable UUID customerId,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId
    ) {
        return ApiResponse.success(customerService.getCustomer(customerId), correlationId);
    }

    @GetMapping("/{customerId}/eligibility")
    public ApiResponse<?> eligibility(
            @PathVariable UUID customerId,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId
    ) {
        return ApiResponse.success(customerService.checkEligibility(customerId), correlationId);
    }
}
