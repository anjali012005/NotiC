package io.github.anjali.notifyflow.management.service.dispatch;

import io.github.anjali.notifyflow.management.entity.NotificationProvider;

public interface NotificationDispatcher {

    void dispatch(NotificationProvider provider, String recipient, String subject, String body);
}
