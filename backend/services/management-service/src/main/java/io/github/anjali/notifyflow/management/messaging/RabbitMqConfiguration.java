package io.github.anjali.notifyflow.management.messaging;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfiguration {

    @Bean
    DirectExchange notificationExchange(@Value("${notification.rabbitmq.exchange}") String name) {
        return new DirectExchange(name);
    }

    @Bean
    Queue notificationQueue(@Value("${notification.rabbitmq.queue}") String name) {
        return new Queue(name, true);
    }

    @Bean
    Binding notificationBinding(Queue notificationQueue, DirectExchange notificationExchange,
                                @Value("${notification.rabbitmq.routing-key}") String routingKey) {
        return BindingBuilder.bind(notificationQueue).to(notificationExchange).with(routingKey);
    }

    @Bean
    DirectExchange retryExchange(@Value("${notification.rabbitmq.retry-exchange:notifyflow.retry}") String name) {
        return new DirectExchange(name);
    }

    @Bean
    Queue retryQueue5s(@Value("${notification.rabbitmq.retry-queue-5s:notification.retry.5s}") String name) {
        return new Queue(name, true);
    }

    @Bean
    Queue retryQueue30s(@Value("${notification.rabbitmq.retry-queue-30s:notification.retry.30s}") String name) {
        return new Queue(name, true);
    }

    @Bean
    Queue retryQueue2m(@Value("${notification.rabbitmq.retry-queue-2m:notification.retry.2m}") String name) {
        return new Queue(name, true);
    }

    @Bean
    Queue dlqQueue(@Value("${notification.rabbitmq.dlq-queue:notification.dlq}") String name) {
        return new Queue(name, true);
    }

    @Bean
    Binding retryBinding5s(Queue retryQueue5s, DirectExchange retryExchange) {
        return BindingBuilder.bind(retryQueue5s).to(retryExchange).with("notification.retry.5s");
    }

    @Bean
    Binding retryBinding30s(Queue retryQueue30s, DirectExchange retryExchange) {
        return BindingBuilder.bind(retryQueue30s).to(retryExchange).with("notification.retry.30s");
    }

    @Bean
    Binding retryBinding2m(Queue retryQueue2m, DirectExchange retryExchange) {
        return BindingBuilder.bind(retryQueue2m).to(retryExchange).with("notification.retry.2m");
    }

    @Bean
    DirectExchange deadLetterExchange(@Value("${notification.rabbitmq.dlx:notifyflow.dlx}") String name) {
        return new DirectExchange(name);
    }

    @Bean
    Binding dlqBinding(Queue dlqQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(dlqQueue).to(deadLetterExchange).with("notification.dlq");
    }

    @Bean
    MessageConverter rabbitMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter rabbitMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(rabbitMessageConverter);
        return template;
    }

    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory, MessageConverter rabbitMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(rabbitMessageConverter);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        return factory;
    }
}
