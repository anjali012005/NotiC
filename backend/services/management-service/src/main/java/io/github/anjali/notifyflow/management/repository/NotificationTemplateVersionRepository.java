package io.github.anjali.notifyflow.management.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.anjali.notifyflow.management.entity.NotificationTemplateVersion;

public interface NotificationTemplateVersionRepository extends JpaRepository<NotificationTemplateVersion, UUID> {
    List<NotificationTemplateVersion> findByTemplateIdOrderByVersionDesc(UUID templateId);

    Optional<NotificationTemplateVersion> findByTemplateIdAndVersion(UUID templateId, Integer version);
}
