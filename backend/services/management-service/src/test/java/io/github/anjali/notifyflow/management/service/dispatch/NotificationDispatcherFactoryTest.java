package io.github.anjali.notifyflow.management.service.dispatch;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.github.anjali.notifyflow.management.entity.NotificationProvider;
import io.github.anjali.notifyflow.management.enums.NotificationChannel;
import io.github.anjali.notifyflow.management.enums.ProviderType;

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
}
