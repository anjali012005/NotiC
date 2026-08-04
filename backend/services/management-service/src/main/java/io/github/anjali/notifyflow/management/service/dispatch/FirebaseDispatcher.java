package io.github.anjali.notifyflow.management.service.dispatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.github.anjali.notifyflow.management.entity.NotificationProvider;

@Component
public class FirebaseDispatcher implements NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(FirebaseDispatcher.class);

    @Override
    public void dispatch(NotificationProvider provider, String recipient, String subject, String body) {
        log.info("FIREBASE dispatch | provider={} | recipient={} | subject={} | body={}", provider, recipient, subject, body);
    }
}
