package io.github.anjali.notifyflow.management.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import io.github.anjali.notifyflow.management.entity.NotificationTemplate;
import io.github.anjali.notifyflow.management.enums.NotificationChannel;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, UUID> {
    boolean existsByTemplateKey(String templateKey);

    Optional<NotificationTemplate> findByTemplateKey(String templateKey);

    Page<NotificationTemplate> findByChannel(NotificationChannel channel, Pageable pageable);

    Page<NotificationTemplate> findByTagsContaining(String tag, Pageable pageable);

    Page<NotificationTemplate> findByChannelAndTagsContaining(NotificationChannel channel, String tag, Pageable pageable);
}