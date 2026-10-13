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
    boolean existsByTenantIdAndChannelAndName(UUID tenantId, NotificationChannel channel, String name);

    boolean existsByChannelAndName(NotificationChannel channel, String name);

    Optional<NotificationProvider> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<NotificationProvider> findByTenantIdAndChannelAndIsDefaultTrue(UUID tenantId, NotificationChannel channel);

    List<NotificationProvider> findByTenantIdOrderByNameAsc(UUID tenantId);

    List<NotificationProvider> findByTenantIdAndChannelOrderByNameAsc(UUID tenantId, NotificationChannel channel);

    List<NotificationProvider> findByTenantIdAndEnabledOrderByNameAsc(UUID tenantId, boolean enabled);

    List<NotificationProvider> findByTenantIdAndChannelAndEnabledOrderByNameAsc(UUID tenantId, NotificationChannel channel, boolean enabled);

    List<NotificationProvider> findByChannelOrderByNameAsc(NotificationChannel channel);

    List<NotificationProvider> findByEnabledOrderByNameAsc(boolean enabled);

    List<NotificationProvider> findByChannelAndEnabledOrderByNameAsc(NotificationChannel channel, boolean enabled);

    Optional<NotificationProvider> findByChannelAndIsDefaultTrue(NotificationChannel channel);

    List<NotificationProvider> findAllByOrderByNameAsc();
}
