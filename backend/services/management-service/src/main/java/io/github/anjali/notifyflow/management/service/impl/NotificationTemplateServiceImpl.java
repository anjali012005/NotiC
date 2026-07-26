package io.github.anjali.notifyflow.management.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.anjali.notifyflow.management.dto.request.CreateNotificationTemplateRequest;
import io.github.anjali.notifyflow.management.dto.response.NotificationTemplateResponse;
import io.github.anjali.notifyflow.management.entity.NotificationTemplate;
import io.github.anjali.notifyflow.management.exception.DuplicateTemplateKeyException;
import io.github.anjali.notifyflow.management.mapper.NotificationTemplateMapper;
import io.github.anjali.notifyflow.management.repository.NotificationTemplateRepository;
import io.github.anjali.notifyflow.management.service.NotificationTemplateService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationTemplateServiceImpl implements NotificationTemplateService {

    private final NotificationTemplateRepository repository;
    private final NotificationTemplateMapper mapper;

    @Override
    @Transactional
    public NotificationTemplateResponse createTemplate(CreateNotificationTemplateRequest request) {
        if (repository.existsByTemplateKey(request.getTemplateKey())) {
            throw new DuplicateTemplateKeyException("Template key '" + request.getTemplateKey() + "' already exists");
        }

        NotificationTemplate template = mapper.toEntity(request);
        NotificationTemplate savedTemplate = repository.save(template);
        return mapper.toResponse(savedTemplate);
    }
}