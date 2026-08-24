package io.github.anjali.notifyflow.management.service.dispatch;

import org.springframework.stereotype.Component;

import io.github.anjali.notifyflow.management.entity.NotificationProvider;
import io.github.anjali.notifyflow.management.exception.ProviderDispatchException;

@Component
public class SmtpDispatcher implements NotificationDispatcher {

    @Override
    public void dispatch(NotificationProvider provider, String recipient, String subject, String body) {
        throw new ProviderDispatchException("SMTP provider dispatch is not configured in this deployment");
    }
}
