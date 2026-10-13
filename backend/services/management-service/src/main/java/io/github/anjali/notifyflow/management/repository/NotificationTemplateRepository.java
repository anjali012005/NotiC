package io.github.anjali.notifyflow.management.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import io.github.anjali.notifyflow.management.entity.NotificationTemplate;
import io.github.anjali.notifyflow.management.enums.NotificationChannel;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, UUID> {
    boolean existsByTenantIdAndTemplateKey(UUID tenantId, String templateKey);

    boolean existsByTemplateKey(String templateKey);

    Optional<NotificationTemplate> findByTenantIdAndTemplateKey(UUID tenantId, String templateKey);

    Optional<NotificationTemplate> findByTemplateKey(String templateKey);

    Optional<NotificationTemplate> findByTenantIdAndId(UUID tenantId, UUID id);

    Page<NotificationTemplate> findByTenantId(UUID tenantId, Pageable pageable);

    Page<NotificationTemplate> findByTenantIdAndChannel(UUID tenantId, NotificationChannel channel, Pageable pageable);

    Page<NotificationTemplate> findByTenantIdAndTagsContaining(UUID tenantId, String tag, Pageable pageable);

    Page<NotificationTemplate> findByTenantIdAndChannelAndTagsContaining(UUID tenantId, NotificationChannel channel, String tag, Pageable pageable);

    Page<NotificationTemplate> findByChannel(NotificationChannel channel, Pageable pageable);

    Page<NotificationTemplate> findByTagsContaining(String tag, Pageable pageable);

    Page<NotificationTemplate> findByChannelAndTagsContaining(NotificationChannel channel, String tag, Pageable pageable);
}