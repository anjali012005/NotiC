package io.github.anjali.notifyflow.management.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.anjali.notifyflow.management.dto.request.CreateNotificationProviderRequest;
import io.github.anjali.notifyflow.management.dto.response.NotificationProviderResponse;
import io.github.anjali.notifyflow.management.entity.NotificationProvider;
import io.github.anjali.notifyflow.management.enums.NotificationChannel;
import io.github.anjali.notifyflow.management.enums.ProviderType;
import io.github.anjali.notifyflow.management.exception.DuplicateProviderException;
import io.github.anjali.notifyflow.management.exception.InvalidProviderConfigurationException;
import io.github.anjali.notifyflow.management.mapper.NotificationProviderMapper;
import io.github.anjali.notifyflow.management.repository.NotificationProviderRepository;
import io.github.anjali.notifyflow.management.security.TenantAuthorizationService;
import io.github.anjali.notifyflow.management.security.TenantContext;

class NotificationProviderServiceImplTest {

    private NotificationProviderRepository repository;
    private NotificationProviderServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(NotificationProviderRepository.class);
        service = new NotificationProviderServiceImpl(repository, new NotificationProviderMapper(), new TenantAuthorizationService());
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

        when(repository.existsByTenantIdAndChannelAndName(any(UUID.class), eq(NotificationChannel.EMAIL), eq("SendGrid"))).thenReturn(false);
        when(repository.findByTenantIdAndChannelAndIsDefaultTrue(any(UUID.class), eq(NotificationChannel.EMAIL))).thenReturn(Optional.of(existing));
        when(repository.save(any(NotificationProvider.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateNotificationProviderRequest request = new CreateNotificationProviderRequest();
        request.setName("SendGrid");
        request.setChannel(NotificationChannel.EMAIL);
        request.setProviderType(ProviderType.SENDGRID);
        request.setConfig("{\"fromEmail\":\"notifications@example.com\"}");
        request.setEnabled(false);
        request.setDefault(true);

        NotificationProviderResponse response = service.createProvider(request);

        assertThat(response.isDefault()).isTrue();
        assertThat(response.isEnabled()).isTrue();
        assertThat(existing.isDefault()).isFalse();
        verify(repository).save(existing);
    }

    @Test
    void createProvider_shouldRejectDuplicateNameWithinSameChannel() {
        when(repository.existsByTenantIdAndChannelAndName(any(UUID.class), eq(NotificationChannel.EMAIL), eq("SendGrid"))).thenReturn(true);

        CreateNotificationProviderRequest request = new CreateNotificationProviderRequest();
        request.setName("SendGrid");
        request.setChannel(NotificationChannel.EMAIL);
        request.setProviderType(ProviderType.SENDGRID);
        request.setConfig("{\"fromEmail\":\"notifications@example.com\"}");

        assertThatThrownBy(() -> service.createProvider(request))
                .isInstanceOf(DuplicateProviderException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void createSendGridProvider_shouldRejectApiKeyInDatabaseConfig() {
        CreateNotificationProviderRequest request = new CreateNotificationProviderRequest();
        request.setName("SendGrid");
        request.setChannel(NotificationChannel.EMAIL);
        request.setProviderType(ProviderType.SENDGRID);
        request.setConfig("{\"fromEmail\":\"notifications@example.com\",\"apiKey\":\"secret\"}");

        assertThatThrownBy(() -> service.createProvider(request))
                .isInstanceOf(InvalidProviderConfigurationException.class)
                .hasMessageContaining("SENDGRID_API_KEY");
    }

    @Test
    void getProviderById_shouldRejectCrossTenantProvider() {
        UUID currentTenant = UUID.randomUUID();
        UUID otherTenant = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        NotificationProvider otherTenantProvider = NotificationProvider.builder()
                .id(providerId)
                .tenantId(otherTenant)
                .name("Other tenant SendGrid")
                .channel(NotificationChannel.EMAIL)
                .providerType(ProviderType.SENDGRID)
                .config("{\"fromEmail\":\"notifications@example.com\"}")
                .enabled(true)
                .isDefault(true)
                .build();
        when(repository.findByTenantIdAndId(currentTenant, providerId)).thenReturn(Optional.empty());
        when(repository.findById(providerId)).thenReturn(Optional.of(otherTenantProvider));
        TenantContext.setCurrentTenantId(currentTenant);

        try {
            assertThatThrownBy(() -> service.getProviderById(providerId))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Tenant does not have access");
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void createProvider_shouldPersistIsDefaultTrue() {
        when(repository.existsByTenantIdAndChannelAndName(any(UUID.class), eq(NotificationChannel.EMAIL), eq("SendGrid"))).thenReturn(false);
        when(repository.findByTenantIdAndChannelAndIsDefaultTrue(any(UUID.class), eq(NotificationChannel.EMAIL))).thenReturn(Optional.empty());
        when(repository.save(any(NotificationProvider.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateNotificationProviderRequest request = new CreateNotificationProviderRequest();
        request.setName("SendGrid");
        request.setChannel(NotificationChannel.EMAIL);
        request.setProviderType(ProviderType.SENDGRID);
        request.setConfig("{\"fromEmail\":\"notifications@example.com\"}");
        request.setEnabled(false);
        request.setDefault(true);

        NotificationProviderResponse response = service.createProvider(request);

        assertThat(response.isDefault()).isTrue();
        assertThat(response.isEnabled()).isTrue();
    }

    @Test
    void createProvider_shouldPersistIsDefaultFalse() {
        when(repository.existsByTenantIdAndChannelAndName(any(UUID.class), eq(NotificationChannel.EMAIL), eq("SendGrid"))).thenReturn(false);
        when(repository.findByTenantIdAndChannelAndIsDefaultTrue(any(UUID.class), eq(NotificationChannel.EMAIL))).thenReturn(Optional.empty());
        when(repository.save(any(NotificationProvider.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateNotificationProviderRequest request = new CreateNotificationProviderRequest();
        request.setName("SendGrid");
        request.setChannel(NotificationChannel.EMAIL);
        request.setProviderType(ProviderType.SENDGRID);
        request.setConfig("{\"fromEmail\":\"notifications@example.com\"}");
        request.setEnabled(true);
        request.setDefault(false);

        NotificationProviderResponse response = service.createProvider(request);

        assertThat(response.isDefault()).isFalse();
    }
}
