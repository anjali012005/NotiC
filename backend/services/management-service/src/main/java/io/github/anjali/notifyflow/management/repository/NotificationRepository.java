package io.github.anjali.notifyflow.management.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
}