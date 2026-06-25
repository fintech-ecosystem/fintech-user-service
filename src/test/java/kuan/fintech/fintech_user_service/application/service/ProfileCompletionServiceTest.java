package kuan.fintech.fintech_user_service.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import kuan.fintech.fintech_user_service.domain.model.ProfileStatus;
import kuan.fintech.fintech_user_service.infrastructure.persistence.entity.CustomerEntity;
import org.junit.jupiter.api.Test;

class ProfileCompletionServiceTest {
    private final ProfileCompletionService service = new ProfileCompletionService();

    @Test
    void returnsMissingFieldsForIncompleteProfile() {
        CustomerEntity customer = new CustomerEntity();

        assertThat(service.calculate(customer)).isEqualTo(ProfileStatus.INCOMPLETE);
        assertThat(service.missingFields(customer)).containsExactly("fullName", "phoneNumber", "dateOfBirth");
    }

    @Test
    void returnsCompletedWhenRequiredFieldsExist() {
        CustomerEntity customer = new CustomerEntity();
        customer.setFullName("Nguyen Van A");
        customer.setPhoneNumber("+84901234567");
        customer.setDateOfBirth(LocalDate.of(1999, 5, 20));

        assertThat(service.calculate(customer)).isEqualTo(ProfileStatus.COMPLETED);
        assertThat(service.missingFields(customer)).isEmpty();
    }
}
