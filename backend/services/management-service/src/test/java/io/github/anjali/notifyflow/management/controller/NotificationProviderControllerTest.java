package io.github.anjali.notifyflow.management.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import io.github.anjali.notifyflow.management.dto.request.CreateNotificationProviderRequest;
import io.github.anjali.notifyflow.management.dto.response.NotificationProviderResponse;
import io.github.anjali.notifyflow.management.enums.NotificationChannel;
import io.github.anjali.notifyflow.management.enums.ProviderType;
import io.github.anjali.notifyflow.management.service.NotificationProviderService;

@ExtendWith(MockitoExtension.class)
class NotificationProviderControllerTest {

    @Mock
    private NotificationProviderService notificationProviderService;

    @InjectMocks
    private NotificationProviderController controller;

    @Test
    void createProvider_shouldCreateProvider() {
        CreateNotificationProviderRequest request = new CreateNotificationProviderRequest();
        request.setName("SendGrid");
        request.setChannel(NotificationChannel.EMAIL);
        request.setProviderType(ProviderType.SENDGRID);
        request.setConfig("{\"apiKey\":\"x\"}");

        NotificationProviderResponse response = NotificationProviderResponse.builder()
                .id(UUID.randomUUID())
                .name("SendGrid")
                .channel(NotificationChannel.EMAIL)
                .providerType(ProviderType.SENDGRID)
                .config("{\"apiKey\":\"x\"}")
                .enabled(true)
                .isDefault(false)
                .build();

        when(notificationProviderService.createProvider(any(CreateNotificationProviderRequest.class))).thenReturn(response);

        ResponseEntity<NotificationProviderResponse> result = controller.createProvider(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getName()).isEqualTo("SendGrid");
    }
}
