package io.github.anjali.notifyflow.management.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import io.github.anjali.notifyflow.management.entity.Notification;
import io.github.anjali.notifyflow.management.enums.NotificationChannel;
import io.github.anjali.notifyflow.management.enums.NotificationDeliveryStatus;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Optional<Notification> findById(UUID id);

    @Query("SELECT n FROM Notification n WHERE " +
           "(:status IS NULL OR n.status = :status) AND " +
           "(:channel IS NULL OR n.channel = :channel) AND " +
           "(:recipient IS NULL OR n.recipient LIKE %:recipient%) AND " +
           "(:providerName IS NULL OR n.providerName = :providerName)")
    Page<Notification> findByFilters(
            @Param("status") NotificationDeliveryStatus status,
            @Param("channel") NotificationChannel channel,
            @Param("recipient") String recipient,
            @Param("providerName") String providerName,
            Pageable pageable);

    @Query("SELECT n.id FROM Notification n WHERE n.status = :status ORDER BY n.createdAt ASC")
    List<UUID> findIdsByStatus(@Param("status") NotificationDeliveryStatus status, Pageable pageable);

    @Modifying
    @Query("UPDATE Notification n SET n.status = :processing, n.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE n.id = :id AND n.status = :queued")
    int claimQueuedNotification(@Param("id") UUID id,
                                @Param("queued") NotificationDeliveryStatus queued,
                                @Param("processing") NotificationDeliveryStatus processing);

    @Modifying
    @Query("UPDATE Notification n SET n.status = :queued, n.retryCount = n.retryCount + 1, " +
           "n.failureReason = :reason, n.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE n.status = :processing AND n.updatedAt < :cutoff AND n.retryCount < :maxRetries")
    int requeueStuckNotifications(@Param("processing") NotificationDeliveryStatus processing,
                                  @Param("queued") NotificationDeliveryStatus queued,
                                  @Param("cutoff") LocalDateTime cutoff,
                                  @Param("maxRetries") int maxRetries,
                                  @Param("reason") String reason);

    @Modifying
    @Query("UPDATE Notification n SET n.status = :failed, n.failureReason = :reason, n.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE n.status = :processing AND n.updatedAt < :cutoff AND n.retryCount >= :maxRetries")
    int failExhaustedStuckNotifications(@Param("processing") NotificationDeliveryStatus processing,
                                        @Param("failed") NotificationDeliveryStatus failed,
                                        @Param("cutoff") LocalDateTime cutoff,
                                        @Param("maxRetries") int maxRetries,
                                        @Param("reason") String reason);
}
