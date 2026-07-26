package io.github.anjali.notifyflow.management.entity;

import io.github.anjali.notifyflow.management.enums.ChannelType;
import io.github.anjali.notifyflow.management.enums.NotificationStatus;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "notification_templates")
public class NotificationTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String templateKey;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 255)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

}