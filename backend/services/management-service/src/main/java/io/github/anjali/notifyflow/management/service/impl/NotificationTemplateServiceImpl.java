package io.github.anjali.notifyflow.management.service.impl;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.anjali.notifyflow.management.dto.request.CreateNotificationTemplateRequest;
import io.github.anjali.notifyflow.management.dto.request.UpdateNotificationTemplateRequest;
import io.github.anjali.notifyflow.management.dto.response.MessageResponse;
import io.github.anjali.notifyflow.management.dto.response.NotificationTemplateResponse;
import io.github.anjali.notifyflow.management.dto.response.NotificationTemplateVersionResponse;
import io.github.anjali.notifyflow.management.dto.response.PageResponse;
import io.github.anjali.notifyflow.management.entity.NotificationTemplate;
import io.github.anjali.notifyflow.management.entity.NotificationTemplateVersion;
import io.github.anjali.notifyflow.management.enums.NotificationChannel;
import io.github.anjali.notifyflow.management.exception.DuplicateTemplateKeyException;
import io.github.anjali.notifyflow.management.exception.ResourceNotFoundException;
import io.github.anjali.notifyflow.management.mapper.NotificationTemplateMapper;
import io.github.anjali.notifyflow.management.repository.NotificationTemplateRepository;
import io.github.anjali.notifyflow.management.repository.NotificationTemplateVersionRepository;
import io.github.anjali.notifyflow.management.service.NotificationTemplateService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationTemplateServiceImpl implements NotificationTemplateService {

    private final NotificationTemplateRepository repository;
    private final NotificationTemplateVersionRepository versionRepository;
    private final NotificationTemplateMapper mapper;

    @Override
    @Transactional
    public NotificationTemplateResponse createTemplate(CreateNotificationTemplateRequest request) {
        if (repository.existsByTemplateKey(request.getTemplateKey())) {
            throw new DuplicateTemplateKeyException("Template key '" + request.getTemplateKey() + "' already exists");
        }

        NotificationTemplate template = mapper.toEntity(request);
        template.setActiveVersion(1);
        NotificationTemplate savedTemplate = repository.save(template);

        NotificationTemplateVersion initialVersion = mapper.toVersionEntity(savedTemplate, 1, savedTemplate);
        versionRepository.save(initialVersion);

        return mapper.toResponse(savedTemplate, "Notification template created successfully");
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationTemplateResponse getTemplateById(UUID id) {
        NotificationTemplate template = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Template not found with id: " + id));
        return mapper.toResponse(template);
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationTemplateResponse getTemplateByKey(String templateKey) {
        NotificationTemplate template = repository.findByTemplateKey(templateKey)
                .orElseThrow(() -> new ResourceNotFoundException("Template not found with key: " + templateKey));
        return mapper.toResponse(template);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<NotificationTemplateResponse> getTemplates(int page, int size, String sortBy, String sortDir,
            NotificationChannel channel, String tag) {
        String safeSortBy = (sortBy == null || sortBy.isBlank()) ? "createdAt" : sortBy;
        String safeSortDir = (sortDir == null || sortDir.isBlank()) ? "desc" : sortDir;
        Sort.Direction direction = safeSortDir.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, safeSortBy));

        Page<NotificationTemplate> templatePage;
        if (channel != null && tag != null && !tag.isBlank()) {
            templatePage = repository.findByChannelAndTagsContaining(channel, tag, pageable);
        } else if (channel != null) {
            templatePage = repository.findByChannel(channel, pageable);
        } else if (tag != null && !tag.isBlank()) {
            templatePage = repository.findByTagsContaining(tag, pageable);
        } else {
            templatePage = repository.findAll(pageable);
        }

        List<NotificationTemplateResponse> content = templatePage.getContent().stream()
                .map(mapper::toResponse)
                .toList();

        return PageResponse.<NotificationTemplateResponse>builder()
                .content(content)
                .page(templatePage.getNumber())
                .size(templatePage.getSize())
                .totalPages(templatePage.getTotalPages())
                .totalElements(templatePage.getTotalElements())
                .last(templatePage.isLast())
                .build();
    }

    @Override
    @Transactional
    public NotificationTemplateResponse updateTemplate(UUID id, UpdateNotificationTemplateRequest request) {
        NotificationTemplate existingTemplate = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Template not found with id: " + id));

        NotificationTemplate updatedTemplate = mapper.updateEntity(request, existingTemplate);
        int nextVersion = existingTemplate.getActiveVersion() == null ? 1 : existingTemplate.getActiveVersion() + 1;
        updatedTemplate.setActiveVersion(nextVersion);
        NotificationTemplate savedTemplate = repository.save(updatedTemplate);

        NotificationTemplateVersion newVersion = mapper.toVersionEntity(savedTemplate, nextVersion, updatedTemplate);
        versionRepository.save(newVersion);

        return mapper.toResponse(savedTemplate, "Notification template updated successfully");
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationTemplateVersionResponse> getTemplateVersions(UUID id) {
        NotificationTemplate template = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Template not found with id: " + id));

        return versionRepository.findByTemplateIdOrderByVersionDesc(template.getId()).stream()
                .map(mapper::toVersionResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationTemplateVersionResponse getTemplateVersion(UUID id, Integer version) {
        NotificationTemplate template = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Template not found with id: " + id));

        NotificationTemplateVersion templateVersion = versionRepository.findByTemplateIdAndVersion(template.getId(), version)
                .orElseThrow(() -> new ResourceNotFoundException("Version not found for template " + id + " and version " + version));

        return mapper.toVersionResponse(templateVersion);
    }

    @Override
    @Transactional
    public MessageResponse deleteTemplate(UUID id) {
        NotificationTemplate template = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Template not found with id: " + id));

        repository.delete(template);
        return MessageResponse.builder()
                .message("Notification template deleted successfully")
                .build();
    }
}