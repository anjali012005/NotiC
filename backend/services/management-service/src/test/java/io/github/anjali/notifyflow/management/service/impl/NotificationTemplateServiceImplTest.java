package io.github.anjali.notifyflow.management.service.impl;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.anjali.notifyflow.management.dto.request.CreateNotificationTemplateRequest;
import io.github.anjali.notifyflow.management.dto.response.NotificationTemplateResponse;
import io.github.anjali.notifyflow.management.entity.NotificationTemplate;
import io.github.anjali.notifyflow.management.enums.NotificationChannel;
import io.github.anjali.notifyflow.management.exception.DuplicateTemplateKeyException;
import io.github.anjali.notifyflow.management.repository.NotificationTemplateRepository;

@ExtendWith(MockitoExtension.class)
class NotificationTemplateServiceImplTest {

    @Mock
    private NotificationTemplateRepository repository;

    @InjectMocks
    private NotificationTemplateServiceImpl service;

    @Test
    void createTemplatePersistsAndReturnsResponse() {
        CreateNotificationTemplateRequest request = new CreateNotificationTemplateRequest();
        request.setTemplateKey("WELCOME_EMAIL");
        request.setName("Welcome Email");
        request.setChannel(NotificationChannel.EMAIL);
        request.setSubject("Welcome to NotifyFlow");
        request.setBody("Hi {{name}}, welcome!");
        request.setTags(Set.of("AUTH", "WELCOME"));

        NotificationTemplate savedTemplate = NotificationTemplate.builder()
                .id(UUID.randomUUID())
                .templateKey("WELCOME_EMAIL")
                .name("Welcome Email")
                .channel(NotificationChannel.EMAIL)
                .subject("Welcome to NotifyFlow")
                .body("Hi {{name}}, welcome!")
                .tags(Set.of("AUTH", "WELCOME"))
                .build();

        when(repository.existsByTemplateKey("WELCOME_EMAIL")).thenReturn(false);
        when(repository.save(any(NotificationTemplate.class))).thenReturn(savedTemplate);

        NotificationTemplateResponse response = service.createTemplate(request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(savedTemplate.getId());
        assertThat(response.getMessage()).contains("created successfully");
    }

    @Test
    void createTemplateThrowsWhenTemplateKeyAlreadyExists() {
        CreateNotificationTemplateRequest request = new CreateNotificationTemplateRequest();
        request.setTemplateKey("WELCOME_EMAIL");
        request.setName("Welcome Email");
        request.setChannel(NotificationChannel.EMAIL);
        request.setSubject("Welcome to NotifyFlow");
        request.setBody("Hi {{name}}, welcome!");

        when(repository.existsByTemplateKey("WELCOME_EMAIL")).thenReturn(true);

        assertThatThrownBy(() -> service.createTemplate(request))
                .isInstanceOf(DuplicateTemplateKeyException.class)
                .hasMessageContaining("already exists");
    }
}
