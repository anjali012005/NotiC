package io.github.anjali.notifyflow.management.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.github.anjali.notifyflow.management.entity.NotificationProvider;
import io.github.anjali.notifyflow.management.enums.NotificationChannel;

@Repository
public interface NotificationProviderRepository extends JpaRepository<NotificationProvider, UUID> {
    boolean existsByChannelAndName(NotificationChannel channel, String name);

    List<NotificationProvider> findByChannelOrderByNameAsc(NotificationChannel channel);

    List<NotificationProvider> findByEnabledOrderByNameAsc(boolean enabled);

    List<NotificationProvider> findByChannelAndEnabledOrderByNameAsc(NotificationChannel channel, boolean enabled);

    Optional<NotificationProvider> findByChannelAndIsDefaultTrue(NotificationChannel channel);

    List<NotificationProvider> findAllByOrderByNameAsc();
}
