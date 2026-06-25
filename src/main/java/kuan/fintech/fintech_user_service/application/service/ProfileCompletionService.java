package kuan.fintech.fintech_user_service.application.service;

import java.util.ArrayList;
import java.util.List;
import kuan.fintech.fintech_user_service.domain.model.ProfileStatus;
import kuan.fintech.fintech_user_service.infrastructure.persistence.entity.CustomerEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ProfileCompletionService {
    public ProfileStatus calculate(CustomerEntity customer) {
        return missingFields(customer).isEmpty() ? ProfileStatus.COMPLETED : ProfileStatus.INCOMPLETE;
    }

    public List<String> missingFields(CustomerEntity customer) {
        List<String> missing = new ArrayList<>();
        if (!StringUtils.hasText(customer.getFullName())) {
            missing.add("fullName");
        }
        if (!StringUtils.hasText(customer.getPhoneNumber())) {
            missing.add("phoneNumber");
        }
        if (customer.getDateOfBirth() == null) {
            missing.add("dateOfBirth");
        }
        return missing;
    }
}
