package kuan.fintech.fintech_user_service.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import kuan.fintech.fintech_user_service.application.command.ChangeCustomerStatusCommand;
import kuan.fintech.fintech_user_service.application.command.CreateCustomerCommand;
import kuan.fintech.fintech_user_service.application.command.UpdateMyProfileCommand;
import kuan.fintech.fintech_user_service.application.result.CustomerEligibilityResult;
import kuan.fintech.fintech_user_service.application.result.CustomerProfileResult;
import kuan.fintech.fintech_user_service.application.result.CustomerStatusChangeResult;
import kuan.fintech.fintech_user_service.application.result.ProfileStatusResult;
import kuan.fintech.fintech_user_service.domain.error.UserErrorCode;
import kuan.fintech.fintech_user_service.domain.error.UserServiceException;
import kuan.fintech.fintech_user_service.domain.model.CustomerStatus;
import kuan.fintech.fintech_user_service.domain.model.CustomerType;
import kuan.fintech.fintech_user_service.domain.model.ProfileStatus;
import kuan.fintech.fintech_user_service.infrastructure.persistence.entity.CustomerEntity;
import kuan.fintech.fintech_user_service.infrastructure.persistence.entity.CustomerProfileHistoryEntity;
import kuan.fintech.fintech_user_service.infrastructure.persistence.entity.CustomerStatusHistoryEntity;
import kuan.fintech.fintech_user_service.infrastructure.persistence.entity.OutboxEventEntity;
import kuan.fintech.fintech_user_service.infrastructure.persistence.repository.CustomerProfileHistoryRepository;
import kuan.fintech.fintech_user_service.infrastructure.persistence.repository.CustomerRepository;
import kuan.fintech.fintech_user_service.infrastructure.persistence.repository.CustomerStatusHistoryRepository;
import kuan.fintech.fintech_user_service.infrastructure.persistence.repository.OutboxEventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CustomerApplicationService {
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+[1-9]\\d{7,14}$");

    private final CustomerRepository customerRepository;
    private final CustomerProfileHistoryRepository profileHistoryRepository;
    private final CustomerStatusHistoryRepository statusHistoryRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ProfileCompletionService profileCompletionService;
    private final ObjectMapper objectMapper;

    public CustomerApplicationService(
            CustomerRepository customerRepository,
            CustomerProfileHistoryRepository profileHistoryRepository,
            CustomerStatusHistoryRepository statusHistoryRepository,
            OutboxEventRepository outboxEventRepository,
            ProfileCompletionService profileCompletionService,
            ObjectMapper objectMapper
    ) {
        this.customerRepository = customerRepository;
        this.profileHistoryRepository = profileHistoryRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.profileCompletionService = profileCompletionService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CustomerProfileResult createCustomer(CreateCustomerCommand command) {
        require(command.authUserId() != null, UserErrorCode.USER_INVALID_REQUEST, "authUserId is required");
        require(StringUtils.hasText(command.email()), UserErrorCode.USER_INVALID_REQUEST, "email is required");
        if (customerRepository.existsByAuthUserId(command.authUserId())) {
            throw new UserServiceException(UserErrorCode.USER_ALREADY_EXISTS, "Customer already exists for authUserId");
        }

        CustomerEntity customer = new CustomerEntity();
        customer.setAuthUserId(command.authUserId());
        customer.setEmail(command.email().trim().toLowerCase());
        customer.setCustomerType(command.customerType() == null ? CustomerType.INDIVIDUAL : command.customerType());
        customer.setStatus(CustomerStatus.ACTIVE);
        customer.setProfileStatus(ProfileStatus.INCOMPLETE);

        CustomerEntity saved = customerRepository.save(customer);
        appendOutbox("customer.created.v1", saved.getId().toString(), mapOf(
                "customerId", saved.getId(),
                "authUserId", saved.getAuthUserId(),
                "email", saved.getEmail(),
                "customerType", saved.getCustomerType(),
                "status", saved.getStatus(),
                "profileStatus", saved.getProfileStatus(),
                "createdAt", nullToNow(saved.getCreatedAt())
        ), command.correlationId(), command.causationId());
        return toResult(saved);
    }

    @Transactional(readOnly = true)
    public CustomerProfileResult getMyProfile(UUID authUserId) {
        return toResult(findByAuthUserId(authUserId));
    }

    @Transactional
    public CustomerProfileResult updateMyProfile(UpdateMyProfileCommand command) {
        CustomerEntity customer = findByAuthUserId(command.authUserId());
        validateProfileUpdate(command);

        Map<String, Object> changedFields = new LinkedHashMap<>();
        setIfChanged(changedFields, "fullName", customer.getFullName(), command.fullName(), customer::setFullName);
        setIfChanged(changedFields, "phoneNumber", customer.getPhoneNumber(), command.phoneNumber(), customer::setPhoneNumber);
        setIfChanged(changedFields, "dateOfBirth", customer.getDateOfBirth(), command.dateOfBirth(), customer::setDateOfBirth);

        Map<String, Object> addressChanges = new LinkedHashMap<>();
        setIfChanged(addressChanges, "line1", customer.getAddressLine1(), command.addressLine1(), customer::setAddressLine1);
        setIfChanged(addressChanges, "line2", customer.getAddressLine2(), command.addressLine2(), customer::setAddressLine2);
        setIfChanged(addressChanges, "city", customer.getCity(), command.city(), customer::setCity);
        setIfChanged(addressChanges, "country", customer.getCountry(), command.country(), customer::setCountry);
        if (!addressChanges.isEmpty()) {
            changedFields.put("address", addressChanges);
        }

        ProfileStatus newStatus = profileCompletionService.calculate(customer);
        if (customer.getProfileStatus() != newStatus) {
            changedFields.put("profileStatus", newStatus);
            customer.setProfileStatus(newStatus);
        }

        CustomerEntity saved = customerRepository.save(customer);
        if (!changedFields.isEmpty()) {
            CustomerProfileHistoryEntity history = new CustomerProfileHistoryEntity();
            history.setCustomerId(saved.getId());
            history.setChangedFields(toJson(changedFields));
            history.setChangedBy(command.changedBy());
            history.setCorrelationId(command.correlationId());
            profileHistoryRepository.save(history);

            appendOutbox("customer.profile_updated.v1", saved.getId().toString(), mapOf(
                    "customerId", saved.getId(),
                    "authUserId", saved.getAuthUserId(),
                    "changedFields", new ArrayList<>(changedFields.keySet()),
                    "profileStatus", saved.getProfileStatus(),
                    "updatedAt", nullToNow(saved.getUpdatedAt())
            ), command.correlationId(), command.causationId());
        }
        return toResult(saved);
    }

    @Transactional(readOnly = true)
    public ProfileStatusResult getMyProfileStatus(UUID authUserId) {
        CustomerEntity customer = findByAuthUserId(authUserId);
        return new ProfileStatusResult(customer.getId(), profileCompletionService.calculate(customer), profileCompletionService.missingFields(customer));
    }

    @Transactional(readOnly = true)
    public Page<CustomerProfileResult> listCustomers(CustomerStatus status, ProfileStatus profileStatus, Pageable pageable) {
        return customerRepository.search(status, profileStatus, pageable).map(this::toResult);
    }

    @Transactional(readOnly = true)
    public CustomerProfileResult getCustomer(UUID customerId) {
        return toResult(findById(customerId));
    }

    @Transactional
    public CustomerStatusChangeResult suspendCustomer(ChangeCustomerStatusCommand command) {
        return changeStatus(command, CustomerStatus.ACTIVE, CustomerStatus.SUSPENDED, "customer.suspended.v1");
    }

    @Transactional
    public CustomerStatusChangeResult reactivateCustomer(ChangeCustomerStatusCommand command) {
        return changeStatus(command, CustomerStatus.SUSPENDED, CustomerStatus.ACTIVE, "customer.reactivated.v1");
    }

    @Transactional
    public CustomerStatusChangeResult closeCustomer(ChangeCustomerStatusCommand command) {
        CustomerEntity customer = findById(command.customerId());
        requireReason(command.reason());
        if (customer.getStatus() == CustomerStatus.CLOSED) {
            throw new UserServiceException(UserErrorCode.USER_INVALID_STATUS_TRANSITION, "Customer is already closed");
        }
        return changeStatus(customer, command, CustomerStatus.CLOSED, "customer.closed.v1");
    }

    @Transactional(readOnly = true)
    public CustomerEligibilityResult checkEligibility(UUID customerId) {
        CustomerEntity customer = findById(customerId);
        List<String> reasons = new ArrayList<>();
        if (customer.getStatus() == CustomerStatus.SUSPENDED) {
            reasons.add("CUSTOMER_SUSPENDED");
        }
        if (customer.getStatus() == CustomerStatus.CLOSED) {
            reasons.add("CUSTOMER_CLOSED");
        }
        ProfileStatus profileStatus = profileCompletionService.calculate(customer);
        if (profileStatus == ProfileStatus.INCOMPLETE) {
            reasons.add("PROFILE_INCOMPLETE");
        }
        return new CustomerEligibilityResult(customer.getId(), reasons.isEmpty(), customer.getStatus(), profileStatus, reasons);
    }

    private CustomerStatusChangeResult changeStatus(
            ChangeCustomerStatusCommand command,
            CustomerStatus expected,
            CustomerStatus target,
            String eventType
    ) {
        CustomerEntity customer = findById(command.customerId());
        requireReason(command.reason());
        if (customer.getStatus() != expected) {
            throw new UserServiceException(UserErrorCode.USER_INVALID_STATUS_TRANSITION, "Invalid customer status transition");
        }
        return changeStatus(customer, command, target, eventType);
    }

    private CustomerStatusChangeResult changeStatus(
            CustomerEntity customer,
            ChangeCustomerStatusCommand command,
            CustomerStatus target,
            String eventType
    ) {
        CustomerStatus fromStatus = customer.getStatus();
        customer.setStatus(target);
        CustomerEntity saved = customerRepository.save(customer);

        CustomerStatusHistoryEntity history = new CustomerStatusHistoryEntity();
        history.setCustomerId(saved.getId());
        history.setFromStatus(fromStatus);
        history.setToStatus(target);
        history.setReason(command.reason().trim());
        history.setChangedBy(command.changedBy());
        history.setCorrelationId(command.correlationId());
        statusHistoryRepository.save(history);

        appendOutbox(eventType, saved.getId().toString(), mapOf(
                "customerId", saved.getId(),
                "fromStatus", fromStatus,
                "toStatus", target,
                "reason", command.reason().trim(),
                "changedBy", command.changedBy(),
                "changedAt", Instant.now()
        ), command.correlationId(), command.causationId());
        return new CustomerStatusChangeResult(saved.getId(), saved.getStatus(), command.reason().trim());
    }

    private CustomerEntity findByAuthUserId(UUID authUserId) {
        require(authUserId != null, UserErrorCode.USER_INVALID_REQUEST, "authUserId is required");
        return customerRepository.findByAuthUserId(authUserId)
                .orElseThrow(() -> new UserServiceException(UserErrorCode.USER_NOT_FOUND, "Customer not found"));
    }

    private CustomerEntity findById(UUID customerId) {
        require(customerId != null, UserErrorCode.USER_INVALID_REQUEST, "customerId is required");
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new UserServiceException(UserErrorCode.USER_NOT_FOUND, "Customer not found"));
    }

    private void validateProfileUpdate(UpdateMyProfileCommand command) {
        if (command.phoneNumber() != null && !PHONE_PATTERN.matcher(command.phoneNumber()).matches()) {
            throw new UserServiceException(UserErrorCode.USER_INVALID_PHONE_NUMBER, "Phone number must be in E.164 format");
        }
        if (command.dateOfBirth() != null && !command.dateOfBirth().isBefore(LocalDate.now())) {
            throw new UserServiceException(UserErrorCode.USER_INVALID_DATE_OF_BIRTH, "Date of birth must be in the past");
        }
    }

    private void requireReason(String reason) {
        require(StringUtils.hasText(reason), UserErrorCode.USER_STATUS_REASON_REQUIRED, "Status change reason is required");
    }

    private void appendOutbox(String eventType, String aggregateId, Map<String, Object> payload, String correlationId, String causationId) {
        OutboxEventEntity outbox = new OutboxEventEntity();
        outbox.setAggregateType("Customer");
        outbox.setAggregateId(aggregateId);
        outbox.setEventType(eventType);
        outbox.setPayload(toJson(mapOf(
                "eventId", UUID.randomUUID(),
                "eventType", eventType,
                "aggregateType", "Customer",
                "aggregateId", aggregateId,
                "occurredAt", Instant.now(),
                "producer", "fintech-user-service",
                "correlationId", correlationId,
                "causationId", causationId,
                "payload", payload
        )));
        outbox.setCorrelationId(correlationId);
        outbox.setCausationId(causationId);
        outboxEventRepository.save(outbox);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new UserServiceException(UserErrorCode.USER_INVALID_REQUEST, "Could not serialize event payload");
        }
    }

    private CustomerProfileResult toResult(CustomerEntity customer) {
        return new CustomerProfileResult(
                customer.getId(),
                customer.getAuthUserId(),
                customer.getEmail(),
                customer.getPhoneNumber(),
                customer.getFullName(),
                customer.getDateOfBirth(),
                customer.getCustomerType(),
                customer.getStatus(),
                profileCompletionService.calculate(customer),
                customer.getAddressLine1(),
                customer.getAddressLine2(),
                customer.getCity(),
                customer.getCountry(),
                customer.getCreatedAt(),
                customer.getUpdatedAt()
        );
    }

    private static Instant nullToNow(Instant value) {
        return value == null ? Instant.now() : value;
    }

    private static Map<String, Object> mapOf(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            map.put((String) keyValues[index], keyValues[index + 1]);
        }
        return map;
    }

    private static void require(boolean expression, UserErrorCode code, String message) {
        if (!expression) {
            throw new UserServiceException(code, message);
        }
    }

    private static <T> void setIfChanged(Map<String, Object> changedFields, String field, T oldValue, T newValue, java.util.function.Consumer<T> setter) {
        if (newValue != null && !Objects.equals(oldValue, newValue)) {
            setter.accept(newValue);
            changedFields.put(field, newValue);
        }
    }
}
