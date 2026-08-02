package io.github.anjali.notifyflow.management.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.anjali.notifyflow.management.entity.NotificationTemplateVersion;

public interface NotificationTemplateVersionRepository
        extends JpaRepository<NotificationTemplateVersion, UUID> {

    Optional<NotificationTemplateVersion> findByTemplate_IdAndActiveTrue(UUID templateId);

    List<NotificationTemplateVersion> findByTemplate_IdOrderByVersionNumberDesc(UUID templateId);

    List<NotificationTemplateVersion> findByTemplate_IdAndActiveTrueOrderByVersionNumberDesc(UUID templateId);

    Optional<NotificationTemplateVersion> findByTemplate_IdAndVersionNumber(UUID templateId, Integer versionNumber);

    // Backward compatibility (remove later if unused)
    List<NotificationTemplateVersion> findByTemplateIdOrderByVersionDesc(UUID templateId);

    Optional<NotificationTemplateVersion> findByTemplateIdAndVersion(UUID templateId, Integer version);
}