package kuan.fintech.fintech_user_service.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import kuan.fintech.fintech_user_service.application.command.ChangeCustomerStatusCommand;
import kuan.fintech.fintech_user_service.application.command.CreateCustomerCommand;
import kuan.fintech.fintech_user_service.application.command.UpdateMyProfileCommand;
import kuan.fintech.fintech_user_service.application.result.CustomerEligibilityResult;
import kuan.fintech.fintech_user_service.application.result.CustomerProfileResult;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomerApplicationServiceTest {
    @Mock
    CustomerRepository customerRepository;

    @Mock
    CustomerProfileHistoryRepository profileHistoryRepository;

    @Mock
    CustomerStatusHistoryRepository statusHistoryRepository;

    @Mock
    OutboxEventRepository outboxEventRepository;

    CustomerApplicationService service;

    @BeforeEach
    void setUp() {
        service = new CustomerApplicationService(
                customerRepository,
                profileHistoryRepository,
                statusHistoryRepository,
                outboxEventRepository,
                new ProfileCompletionService(),
                new ObjectMapper().findAndRegisterModules()
        );
        lenient().when(customerRepository.save(any(CustomerEntity.class))).thenAnswer(invocation -> {
            CustomerEntity customer = invocation.getArgument(0);
            if (customer.getId() == null) {
                customer.setId(UUID.randomUUID());
            }
            if (customer.getCreatedAt() == null) {
                customer.setCreatedAt(Instant.parse("2026-06-20T00:00:00Z"));
            }
            customer.setUpdatedAt(Instant.parse("2026-06-20T00:00:00Z"));
            return customer;
        });
    }

    @Test
    void createCustomerSetsDefaultsAndCreatesOutboxEvent() {
        UUID authUserId = UUID.randomUUID();
        when(customerRepository.existsByAuthUserId(authUserId)).thenReturn(false);

        CustomerProfileResult result = service.createCustomer(new CreateCustomerCommand(
                authUserId,
                "Customer@Example.COM",
                null,
                "corr-id",
                "cmd-id"
        ));

        assertThat(result.authUserId()).isEqualTo(authUserId);
        assertThat(result.email()).isEqualTo("customer@example.com");
        assertThat(result.status()).isEqualTo(CustomerStatus.ACTIVE);
        assertThat(result.customerType()).isEqualTo(CustomerType.INDIVIDUAL);
        assertThat(result.profileStatus()).isEqualTo(ProfileStatus.INCOMPLETE);

        ArgumentCaptor<OutboxEventEntity> outboxCaptor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getEventType()).isEqualTo("customer.created.v1");
        assertThat(outboxCaptor.getValue().getPayload()).contains("customer.created.v1", "fintech-user-service");
    }

    @Test
    void createCustomerRejectsDuplicateAuthUserId() {
        UUID authUserId = UUID.randomUUID();
        when(customerRepository.existsByAuthUserId(authUserId)).thenReturn(true);

        assertThatThrownBy(() -> service.createCustomer(new CreateCustomerCommand(
                authUserId,
                "customer@example.com",
                CustomerType.INDIVIDUAL,
                null,
                null
        )))
                .isInstanceOf(UserServiceException.class)
                .extracting(ex -> ((UserServiceException) ex).getCode())
                .isEqualTo(UserErrorCode.USER_ALREADY_EXISTS);

        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void updateProfileToCompletedCreatesHistoryAndOutboxEvent() {
        UUID authUserId = UUID.randomUUID();
        CustomerEntity customer = customer(authUserId, CustomerStatus.ACTIVE, ProfileStatus.INCOMPLETE);
        when(customerRepository.findByAuthUserId(authUserId)).thenReturn(Optional.of(customer));

        CustomerProfileResult result = service.updateMyProfile(new UpdateMyProfileCommand(
                authUserId,
                "Nguyen Van A",
                "+84901234567",
                LocalDate.of(1999, 5, 20),
                "123 Nguyen Trai",
                "Thanh Xuan",
                "Hanoi",
                "VN",
                null,
                authUserId,
                "corr-id",
                "cmd-id"
        ));

        assertThat(result.profileStatus()).isEqualTo(ProfileStatus.COMPLETED);
        assertThat(result.fullName()).isEqualTo("Nguyen Van A");

        ArgumentCaptor<CustomerProfileHistoryEntity> historyCaptor = ArgumentCaptor.forClass(CustomerProfileHistoryEntity.class);
        verify(profileHistoryRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getChangedFields())
                .contains("fullName", "phoneNumber", "dateOfBirth", "address", "profileStatus");

        ArgumentCaptor<OutboxEventEntity> outboxCaptor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getEventType()).isEqualTo("customer.profile_updated.v1");
    }

    @Test
    void updateProfileRejectsInvalidPhoneNumber() {
        UUID authUserId = UUID.randomUUID();
        when(customerRepository.findByAuthUserId(authUserId)).thenReturn(Optional.of(customer(authUserId, CustomerStatus.ACTIVE, ProfileStatus.INCOMPLETE)));

        assertThatThrownBy(() -> service.updateMyProfile(new UpdateMyProfileCommand(
                authUserId,
                null,
                "0901234567",
                null,
                null,
                null,
                null,
                null,
                null,
                authUserId,
                null,
                null
        )))
                .isInstanceOf(UserServiceException.class)
                .extracting(ex -> ((UserServiceException) ex).getCode())
                .isEqualTo(UserErrorCode.USER_INVALID_PHONE_NUMBER);
    }

    @Test
    void updateProfileRejectsFutureDateOfBirth() {
        UUID authUserId = UUID.randomUUID();
        when(customerRepository.findByAuthUserId(authUserId)).thenReturn(Optional.of(customer(authUserId, CustomerStatus.ACTIVE, ProfileStatus.INCOMPLETE)));

        assertThatThrownBy(() -> service.updateMyProfile(new UpdateMyProfileCommand(
                authUserId,
                null,
                null,
                LocalDate.now().plusDays(1),
                null,
                null,
                null,
                null,
                null,
                authUserId,
                null,
                null
        )))
                .isInstanceOf(UserServiceException.class)
                .extracting(ex -> ((UserServiceException) ex).getCode())
                .isEqualTo(UserErrorCode.USER_INVALID_DATE_OF_BIRTH);
    }

    @Test
    void suspendActiveCustomerCreatesStatusHistoryAndOutboxEvent() {
        UUID customerId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        CustomerEntity customer = customer(UUID.randomUUID(), CustomerStatus.ACTIVE, ProfileStatus.COMPLETED);
        customer.setId(customerId);
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));

        service.suspendCustomer(new ChangeCustomerStatusCommand(customerId, "Suspicious activity detected", adminId, "corr-id", "cmd-id"));

        assertThat(customer.getStatus()).isEqualTo(CustomerStatus.SUSPENDED);
        ArgumentCaptor<CustomerStatusHistoryEntity> historyCaptor = ArgumentCaptor.forClass(CustomerStatusHistoryEntity.class);
        verify(statusHistoryRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getFromStatus()).isEqualTo(CustomerStatus.ACTIVE);
        assertThat(historyCaptor.getValue().getToStatus()).isEqualTo(CustomerStatus.SUSPENDED);

        ArgumentCaptor<OutboxEventEntity> outboxCaptor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getEventType()).isEqualTo("customer.suspended.v1");
    }

    @Test
    void suspendRequiresReason() {
        UUID customerId = UUID.randomUUID();
        CustomerEntity customer = customer(UUID.randomUUID(), CustomerStatus.ACTIVE, ProfileStatus.COMPLETED);
        customer.setId(customerId);
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> service.suspendCustomer(new ChangeCustomerStatusCommand(customerId, " ", null, null, null)))
                .isInstanceOf(UserServiceException.class)
                .extracting(ex -> ((UserServiceException) ex).getCode())
                .isEqualTo(UserErrorCode.USER_STATUS_REASON_REQUIRED);
    }

    @Test
    void eligibilityIsTrueOnlyForActiveCompletedCustomer() {
        UUID customerId = UUID.randomUUID();
        CustomerEntity customer = customer(UUID.randomUUID(), CustomerStatus.ACTIVE, ProfileStatus.COMPLETED);
        customer.setId(customerId);
        customer.setFullName("Nguyen Van A");
        customer.setPhoneNumber("+84901234567");
        customer.setDateOfBirth(LocalDate.of(1999, 5, 20));
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));

        CustomerEligibilityResult result = service.checkEligibility(customerId);

        assertThat(result.eligible()).isTrue();
        assertThat(result.reasons()).isEmpty();
    }

    @Test
    void eligibilityIncludesSuspendedAndIncompleteReasons() {
        UUID customerId = UUID.randomUUID();
        CustomerEntity customer = customer(UUID.randomUUID(), CustomerStatus.SUSPENDED, ProfileStatus.INCOMPLETE);
        customer.setId(customerId);
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));

        CustomerEligibilityResult result = service.checkEligibility(customerId);

        assertThat(result.eligible()).isFalse();
        assertThat(result.reasons()).containsExactly("CUSTOMER_SUSPENDED", "PROFILE_INCOMPLETE");
    }

    private CustomerEntity customer(UUID authUserId, CustomerStatus status, ProfileStatus profileStatus) {
        CustomerEntity customer = new CustomerEntity();
        customer.setId(UUID.randomUUID());
        customer.setAuthUserId(authUserId);
        customer.setEmail("customer@example.com");
        customer.setCustomerType(CustomerType.INDIVIDUAL);
        customer.setStatus(status);
        customer.setProfileStatus(profileStatus);
        customer.setCreatedAt(Instant.parse("2026-06-20T00:00:00Z"));
        customer.setUpdatedAt(Instant.parse("2026-06-20T00:00:00Z"));
        return customer;
    }
}
