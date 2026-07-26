package io.github.anjali.notifyflow.management.service.impl;

import io.github.anjali.notifyflow.management.service.NotificationTemplateService;
import org.springframework.stereotype.Service;
import io.github.anjali.notifyflow.management.repository.NotificationTemplateRepository;

@Service
public class NotificationTemplateServiceImpl implements NotificationTemplateService {

    private final NotificationTemplateRepository repository;

    public NotificationTemplateServiceImpl(NotificationTemplateRepository repository) {

        this.repository = repository;
    }
}