package io.github.anjali.notifyflow.management.messaging;

import java.util.UUID;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RabbitNotificationEventPublisher implements NotificationEventPublisher {

    private static final String NOTIFICATION_CREATED = "NOTIFICATION_CREATED";

    private final RabbitTemplate rabbitTemplate;

    @Value("${notification.rabbitmq.exchange}")
    private String exchange;

    @Value("${notification.rabbitmq.routing-key}")
    private String routingKey;

    @Override
    public void publishNotificationCreated(UUID notificationId) {
        rabbitTemplate.convertAndSend(exchange, routingKey,
                new NotificationCreatedMessage(notificationId, NOTIFICATION_CREATED));
    }
}