package io.github.anjali.notifyflow.management.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.anjali.notifyflow.management.dto.request.CreateNotificationProviderRequest;
import io.github.anjali.notifyflow.management.dto.response.NotificationProviderResponse;
import io.github.anjali.notifyflow.management.entity.NotificationProvider;
import io.github.anjali.notifyflow.management.enums.NotificationChannel;
import io.github.anjali.notifyflow.management.enums.ProviderType;
import io.github.anjali.notifyflow.management.exception.DuplicateProviderException;
import io.github.anjali.notifyflow.management.mapper.NotificationProviderMapper;
import io.github.anjali.notifyflow.management.repository.NotificationProviderRepository;

class NotificationProviderServiceImplTest {

    private NotificationProviderRepository repository;
    private NotificationProviderServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(NotificationProviderRepository.class);
        service = new NotificationProviderServiceImpl(repository, new NotificationProviderMapper());
    }

    @Test
    void createProvider_shouldClearExistingDefaultForSameChannel() {
        NotificationProvider existing = NotificationProvider.builder()
                .id(UUID.randomUUID())
                .name("Existing SMTP")
                .channel(NotificationChannel.EMAIL)
                .providerType(ProviderType.SMTP)
                .config("{}")
                .enabled(true)
                .isDefault(true)
                .build();

        when(repository.existsByChannelAndName(NotificationChannel.EMAIL, "SendGrid")).thenReturn(false);
        when(repository.findByChannelAndIsDefaultTrue(NotificationChannel.EMAIL)).thenReturn(Optional.of(existing));
        when(repository.save(any(NotificationProvider.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateNotificationProviderRequest request = new CreateNotificationProviderRequest();
        request.setName("SendGrid");
        request.setChannel(NotificationChannel.EMAIL);
        request.setProviderType(ProviderType.SENDGRID);
        request.setConfig("{\"apiKey\":\"x\"}");
        request.setEnabled(true);
        request.setDefault(true);

        NotificationProviderResponse response = service.createProvider(request);

        assertThat(response.isDefault()).isTrue();
        assertThat(existing.isDefault()).isFalse();
        verify(repository).save(existing);
    }

    @Test
    void createProvider_shouldRejectDuplicateNameWithinSameChannel() {
        when(repository.existsByChannelAndName(NotificationChannel.EMAIL, "SendGrid")).thenReturn(true);

        CreateNotificationProviderRequest request = new CreateNotificationProviderRequest();
        request.setName("SendGrid");
        request.setChannel(NotificationChannel.EMAIL);
        request.setProviderType(ProviderType.SENDGRID);
        request.setConfig("{}");

        assertThatThrownBy(() -> service.createProvider(request))
                .isInstanceOf(DuplicateProviderException.class)
                .hasMessageContaining("already exists");
    }
}
