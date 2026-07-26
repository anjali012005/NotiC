package io.github.anjali.notifyflow.management.repository;

import io.github.anjali.notifyflow.management.entity.NotificationTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, UUID> {
    boolean existsByTemplateKey(String templateKey);
}