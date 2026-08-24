package io.github.anjali.notifyflow.management.service.dispatch;

import java.util.Map;

import org.springframework.stereotype.Component;

import io.github.anjali.notifyflow.management.entity.NotificationProvider;
import io.github.anjali.notifyflow.management.enums.ProviderType;
import io.github.anjali.notifyflow.management.enums.NotificationChannel;
import io.github.anjali.notifyflow.management.exception.ProviderDispatchException;
import io.github.anjali.notifyflow.management.exception.ResourceNotFoundException;

@Component
public class NotificationDispatcherFactory {

    private final Map<ProviderType, NotificationDispatcher> dispatchers;

    public NotificationDispatcherFactory() {
        this(new SmtpDispatcher(), new SendGridDispatcher(), new TwilioDispatcher(), new FirebaseDispatcher());
    }

    public NotificationDispatcherFactory(SmtpDispatcher smtpDispatcher,
            SendGridDispatcher sendGridDispatcher,
            TwilioDispatcher twilioDispatcher,
            FirebaseDispatcher firebaseDispatcher) {
        this.dispatchers = Map.of(
                ProviderType.SMTP, smtpDispatcher,
                ProviderType.SENDGRID, sendGridDispatcher,
                ProviderType.TWILIO, twilioDispatcher,
                ProviderType.FIREBASE, firebaseDispatcher);
    }

    public NotificationDispatcher resolveDispatcher(NotificationProvider provider) {
        if (provider == null || provider.getProviderType() == null) {
            throw new ProviderDispatchException("Provider type is required");
        }

        validateChannel(provider);

        NotificationDispatcher dispatcher = dispatchers.get(provider.getProviderType());
        if (dispatcher == null) {
            throw new ProviderDispatchException("Unsupported provider type: " + provider.getProviderType());
        }
        return dispatcher;
    }

    private void validateChannel(NotificationProvider provider) {
        NotificationChannel expected = switch (provider.getProviderType()) {
            case SMTP, SENDGRID, AWS_SES -> NotificationChannel.EMAIL;
            case TWILIO, MSG91 -> NotificationChannel.SMS;
            case FIREBASE, ONESIGNAL -> NotificationChannel.PUSH;
        };
        if (provider.getChannel() != expected) {
            throw new ProviderDispatchException("Provider type " + provider.getProviderType()
                    + " does not support channel " + provider.getChannel());
        }
    }
}
