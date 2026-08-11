package io.github.anjali.notifyflow.management.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.anjali.notifyflow.management.dto.request.SendNotificationRequest;
import io.github.anjali.notifyflow.management.dto.response.NotificationResponse;
import io.github.anjali.notifyflow.management.entity.NotificationProvider;
import io.github.anjali.notifyflow.management.entity.NotificationTemplate;
import io.github.anjali.notifyflow.management.entity.NotificationTemplateVersion;
import io.github.anjali.notifyflow.management.entity.TemplateVariable;
import io.github.anjali.notifyflow.management.enums.NotificationChannel;
import io.github.anjali.notifyflow.management.enums.ProviderType;
import io.github.anjali.notifyflow.management.exception.MissingRequiredVariableException;
import io.github.anjali.notifyflow.management.exception.ResourceNotFoundException;
import io.github.anjali.notifyflow.management.repository.NotificationProviderRepository;
import io.github.anjali.notifyflow.management.repository.NotificationTemplateRepository;
import io.github.anjali.notifyflow.management.repository.NotificationTemplateVersionRepository;
import io.github.anjali.notifyflow.management.service.dispatch.NotificationDispatcher;
import io.github.anjali.notifyflow.management.service.dispatch.NotificationDispatcherFactory;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationTemplateRepository templateRepository;

    @Mock
    private NotificationTemplateVersionRepository versionRepository;

    @Mock
    private NotificationProviderRepository providerRepository;

    @Mock
    private NotificationDispatcherFactory dispatcherFactory;

    @InjectMocks
    private NotificationServiceImpl service;

    @Test
    void sendNotificationDispatchesSuccessfully() {
        NotificationTemplate template = NotificationTemplate.builder()
                .id(UUID.randomUUID())
                .templateKey("WELCOME_EMAIL")
                .channel(NotificationChannel.EMAIL)
                .build();

        NotificationTemplateVersion version = NotificationTemplateVersion.builder()
                .id(UUID.randomUUID())
                .template(template)
                .version(1)
                .versionNumber(1)
                .channel(NotificationChannel.EMAIL)
                .subject("Hello {{name}}")
                .body("Hi {{name}}")
                .active(true)
                .variables(List.of(TemplateVariable.builder().variableName("name").build()))
                .build();

        NotificationProvider provider = NotificationProvider.builder()
                .id(UUID.randomUUID())
                .name("Mailgun")
                .channel(NotificationChannel.EMAIL)
                .providerType(ProviderType.SMTP)
                .enabled(true)
                .isDefault(true)
                .build();

        SendNotificationRequest request = new SendNotificationRequest();
        request.setTemplateKey("WELCOME_EMAIL");
        request.setRecipient("user@example.com");
        request.setVariables(Map.of("name", "Jane"));

        NotificationDispatcher dispatcher = new NotificationDispatcher() {
            @Override
            public void dispatch(NotificationProvider provider, String recipient, String subject, String body) {
                // no-op for test
            }
        };

        when(templateRepository.findByTemplateKey("WELCOME_EMAIL")).thenReturn(Optional.of(template));
        when(versionRepository.findByTemplate_IdAndActiveTrue(template.getId())).thenReturn(Optional.of(version));
        when(providerRepository.findByChannelAndIsDefaultTrue(NotificationChannel.EMAIL)).thenReturn(Optional.of(provider));
        when(dispatcherFactory.resolveDispatcher(provider)).thenReturn(dispatcher);

        NotificationResponse response = service.sendNotification(request);

        assertThat(response).isNotNull();
        assertThat(response.getTemplateKey()).isEqualTo("WELCOME_EMAIL");
        assertThat(response.getRecipient()).isEqualTo("user@example.com");
        assertThat(response.getSubject()).isEqualTo("Hello Jane");
        assertThat(response.getBody()).isEqualTo("Hi Jane");
    }

    @Test
    void sendNotificationThrowsWhenTemplateMissing() {
        SendNotificationRequest request = new SendNotificationRequest();
        request.setTemplateKey("MISSING_TEMPLATE");
        request.setRecipient("user@example.com");
        request.setVariables(Map.of());

        when(templateRepository.findByTemplateKey("MISSING_TEMPLATE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sendNotification(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Template not found");
    }

    @Test
    void sendNotificationThrowsWhenProviderMissing() {
        NotificationTemplate template = NotificationTemplate.builder()
                .id(UUID.randomUUID())
                .templateKey("WELCOME_EMAIL")
                .channel(NotificationChannel.EMAIL)
                .build();

        NotificationTemplateVersion version = NotificationTemplateVersion.builder()
                .id(UUID.randomUUID())
                .template(template)
                .version(1)
                .versionNumber(1)
                .channel(NotificationChannel.EMAIL)
                .subject("Hello {{name}}")
                .body("Hi {{name}}")
                .active(true)
                .variables(List.of())
                .build();

        SendNotificationRequest request = new SendNotificationRequest();
        request.setTemplateKey("WELCOME_EMAIL");
        request.setRecipient("user@example.com");
        request.setVariables(Map.of());

        when(templateRepository.findByTemplateKey("WELCOME_EMAIL")).thenReturn(Optional.of(template));
        when(versionRepository.findByTemplate_IdAndActiveTrue(template.getId())).thenReturn(Optional.of(version));
        when(providerRepository.findByChannelAndIsDefaultTrue(NotificationChannel.EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sendNotification(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("provider");
    }

    @Test
    void sendNotificationThrowsWhenVariablesMissing() {
        NotificationTemplate template = NotificationTemplate.builder()
                .id(UUID.randomUUID())
                .templateKey("WELCOME_EMAIL")
                .channel(NotificationChannel.EMAIL)
                .build();

        NotificationTemplateVersion version = NotificationTemplateVersion.builder()
                .id(UUID.randomUUID())
                .template(template)
                .version(1)
                .versionNumber(1)
                .channel(NotificationChannel.EMAIL)
                .subject("Hello {{name}}")
                .body("Hi {{name}}")
                .active(true)
                .variables(List.of(TemplateVariable.builder().variableName("name").build()))
                .build();

        NotificationProvider provider = NotificationProvider.builder()
                .id(UUID.randomUUID())
                .name("Mailgun")
                .channel(NotificationChannel.EMAIL)
                .providerType(ProviderType.SMTP)
                .enabled(true)
                .isDefault(true)
                .build();

        SendNotificationRequest request = new SendNotificationRequest();
        request.setTemplateKey("WELCOME_EMAIL");
        request.setRecipient("user@example.com");
        request.setVariables(Map.of());

        when(templateRepository.findByTemplateKey("WELCOME_EMAIL")).thenReturn(Optional.of(template));
        when(versionRepository.findByTemplate_IdAndActiveTrue(template.getId())).thenReturn(Optional.of(version));

        assertThatThrownBy(() -> service.sendNotification(request))
                .isInstanceOf(MissingRequiredVariableException.class)
                .hasMessageContaining("name");
    }
}
