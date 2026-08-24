package io.github.anjali.notifyflow.management.service.dispatch;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.github.anjali.notifyflow.management.entity.NotificationProvider;
import io.github.anjali.notifyflow.management.enums.NotificationChannel;
import io.github.anjali.notifyflow.management.enums.ProviderType;
import io.github.anjali.notifyflow.management.exception.ProviderDispatchException;

class NotificationDispatcherFactoryTest {

    private final NotificationDispatcherFactory factory = new NotificationDispatcherFactory();

    @Test
    void resolvesSmtpDispatcherForSmtpProvider() {
        NotificationProvider provider = NotificationProvider.builder()
                .providerType(ProviderType.SMTP)
                .channel(NotificationChannel.EMAIL)
                .build();

        NotificationDispatcher dispatcher = factory.resolveDispatcher(provider);

        assertThat(dispatcher).isInstanceOf(SmtpDispatcher.class);
    }

    @Test
    void resolvesSendGridDispatcherForSendGridProvider() {
        NotificationProvider provider = NotificationProvider.builder()
                .providerType(ProviderType.SENDGRID)
                .channel(NotificationChannel.EMAIL)
                .build();

        NotificationDispatcher dispatcher = factory.resolveDispatcher(provider);

        assertThat(dispatcher).isInstanceOf(SendGridDispatcher.class);
    }

    @Test
    void resolvesTwilioDispatcherForTwilioProvider() {
        NotificationProvider provider = NotificationProvider.builder()
                .providerType(ProviderType.TWILIO)
                .channel(NotificationChannel.SMS)
                .build();

        NotificationDispatcher dispatcher = factory.resolveDispatcher(provider);

        assertThat(dispatcher).isInstanceOf(TwilioDispatcher.class);
    }

    @Test
    void resolvesFirebaseDispatcherForFirebaseProvider() {
        NotificationProvider provider = NotificationProvider.builder()
                .providerType(ProviderType.FIREBASE)
                .channel(NotificationChannel.PUSH)
                .build();

        NotificationDispatcher dispatcher = factory.resolveDispatcher(provider);

        assertThat(dispatcher).isInstanceOf(FirebaseDispatcher.class);
    }

    @Test
    void rejectsProviderChannelMismatch() {
        NotificationProvider provider = NotificationProvider.builder()
                .providerType(ProviderType.SENDGRID)
                .channel(NotificationChannel.SMS)
                .build();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> factory.resolveDispatcher(provider))
                .isInstanceOf(ProviderDispatchException.class)
                .hasMessageContaining("does not support channel");
    }

    @Test
    void rejectsProviderTypeWithoutAnAdapter() {
        NotificationProvider provider = NotificationProvider.builder()
                .providerType(ProviderType.AWS_SES)
                .channel(NotificationChannel.EMAIL)
                .build();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> factory.resolveDispatcher(provider))
                .isInstanceOf(ProviderDispatchException.class)
                .hasMessageContaining("Unsupported provider type");
    }
}
