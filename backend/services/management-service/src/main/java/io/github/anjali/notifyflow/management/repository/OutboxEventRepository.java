package io.github.anjali.notifyflow.management.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import io.github.anjali.notifyflow.management.entity.OutboxEvent;
import io.github.anjali.notifyflow.management.enums.OutboxEventStatus;
import jakarta.persistence.LockModeType;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM OutboxEvent e WHERE e.status = :status AND e.attemptCount < :maxAttempts "
            + "ORDER BY e.createdAt ASC")
    List<OutboxEvent> findPendingForUpdate(@Param("status") OutboxEventStatus status,
                                           @Param("maxAttempts") int maxAttempts,
                                           Pageable pageable);

    long countByStatus(OutboxEventStatus status);

    @Query("SELECT e.createdAt FROM OutboxEvent e WHERE e.status = :status ORDER BY e.createdAt ASC")
    List<LocalDateTime> findCreatedAtByStatus(@Param("status") OutboxEventStatus status);

    default java.util.Optional<LocalDateTime> findOldestCreatedAtByStatus(OutboxEventStatus status) {
        return findCreatedAtByStatus(status).stream().findFirst();
    }
}