package kuan.fintech.fintech_user_service.infrastructure.persistence.repository;

import java.util.UUID;
import kuan.fintech.fintech_user_service.infrastructure.persistence.entity.CustomerProfileHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerProfileHistoryRepository extends JpaRepository<CustomerProfileHistoryEntity, UUID> {
}
