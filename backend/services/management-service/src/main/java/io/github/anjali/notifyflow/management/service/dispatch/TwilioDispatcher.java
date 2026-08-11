package io.github.anjali.notifyflow.management.service.dispatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.github.anjali.notifyflow.management.entity.NotificationProvider;

@Component
public class TwilioDispatcher implements NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(TwilioDispatcher.class);

    @Override
    public void dispatch(NotificationProvider provider, String recipient, String subject, String body) {
        log.info("TWILIO dispatch | provider={} | recipient={} | subject={} | body={}", provider, recipient, subject, body);
    }
}
