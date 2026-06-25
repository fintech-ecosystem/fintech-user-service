package kuan.fintech.fintech_user_service.infrastructure.persistence.repository;

import java.util.Optional;
import java.util.UUID;
import kuan.fintech.fintech_user_service.domain.model.CustomerStatus;
import kuan.fintech.fintech_user_service.domain.model.ProfileStatus;
import kuan.fintech.fintech_user_service.infrastructure.persistence.entity.CustomerEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository extends JpaRepository<CustomerEntity, UUID> {
    boolean existsByAuthUserId(UUID authUserId);

    Optional<CustomerEntity> findByAuthUserId(UUID authUserId);

    @Query("""
            select c from CustomerEntity c
            where (:status is null or c.status = :status)
              and (:profileStatus is null or c.profileStatus = :profileStatus)
            """)
    Page<CustomerEntity> search(
            @Param("status") CustomerStatus status,
            @Param("profileStatus") ProfileStatus profileStatus,
            Pageable pageable
    );
}
