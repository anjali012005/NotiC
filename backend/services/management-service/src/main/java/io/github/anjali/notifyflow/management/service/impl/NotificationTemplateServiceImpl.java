package io.github.anjali.notifyflow.management.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.anjali.notifyflow.management.dto.request.CreateNotificationTemplateRequest;
import io.github.anjali.notifyflow.management.dto.request.CreateTemplateVersionRequest;
import io.github.anjali.notifyflow.management.dto.request.RenderTemplateRequest;
import io.github.anjali.notifyflow.management.dto.request.UpdateNotificationTemplateRequest;
import io.github.anjali.notifyflow.management.dto.response.MessageResponse;
import io.github.anjali.notifyflow.management.dto.response.NotificationTemplateResponse;
import io.github.anjali.notifyflow.management.dto.response.PageResponse;
import io.github.anjali.notifyflow.management.dto.response.RenderTemplateResponse;
import io.github.anjali.notifyflow.management.entity.NotificationTemplate;
import io.github.anjali.notifyflow.management.entity.NotificationTemplateVersion;
import io.github.anjali.notifyflow.management.entity.TemplateVariable;
import io.github.anjali.notifyflow.management.enums.NotificationChannel;
import io.github.anjali.notifyflow.management.exception.DuplicateTemplateKeyException;
import io.github.anjali.notifyflow.management.exception.InvalidTemplateVariableException;
import io.github.anjali.notifyflow.management.exception.MissingRequiredVariableException;
import io.github.anjali.notifyflow.management.exception.ResourceNotFoundException;
import io.github.anjali.notifyflow.management.mapper.NotificationTemplateMapper;
import io.github.anjali.notifyflow.management.repository.NotificationTemplateRepository;
import io.github.anjali.notifyflow.management.repository.NotificationTemplateVersionRepository;
import io.github.anjali.notifyflow.management.repository.TemplateVariableRepository;
import io.github.anjali.notifyflow.management.service.NotificationTemplateService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationTemplateServiceImpl implements NotificationTemplateService {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{\\s*([a-zA-Z][a-zA-Z0-9_]*)\\s*\\}\\}");
    private static final String VARIABLE_NAME_PATTERN = "[a-zA-Z][a-zA-Z0-9_]*";

    private final NotificationTemplateRepository repository;
    private final NotificationTemplateVersionRepository versionRepository;
    private final TemplateVariableRepository variableRepository;
    private final NotificationTemplateMapper mapper;

    @Override
    @Transactional
    public NotificationTemplateResponse createTemplate(CreateNotificationTemplateRequest request) {
        if (repository.existsByTemplateKey(request.getTemplateKey())) {
            throw new DuplicateTemplateKeyException("Template key '" + request.getTemplateKey() + "' already exists");
        }

        NotificationTemplate template = mapper.toEntity(request);
        NotificationTemplate savedTemplate = repository.save(template);

        createVersion(savedTemplate, request.getSubject(), request.getBody(), true);
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
        NotificationTemplate savedTemplate = repository.save(updatedTemplate);
        createVersion(savedTemplate, request.getSubject(), request.getBody(), true);
        return mapper.toResponse(savedTemplate, "Notification template updated successfully");
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

    @Override
    @Transactional
    public NotificationTemplateResponse createVersion(UUID templateId, CreateTemplateVersionRequest request) {
        NotificationTemplate template = repository.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException("Template not found with id: " + templateId));

        createVersion(template, request.getSubject(), request.getBody(), true);
        return mapper.toResponse(template, "Template version created successfully");
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getVariables(UUID templateId) {
        NotificationTemplate template = repository.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException("Template not found with id: " + templateId));

        NotificationTemplateVersion version = versionRepository.findByTemplateIdAndActiveTrue(template.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No active version found for template: " + templateId));

        return version.getVariables().stream()
                .map(TemplateVariable::getVariableName)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public RenderTemplateResponse renderTemplate(UUID templateId, RenderTemplateRequest request) {
        NotificationTemplate template = repository.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException("Template not found with id: " + templateId));

        NotificationTemplateVersion version = versionRepository.findByTemplateIdAndActiveTrue(template.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No active version found for template: " + templateId));

        Set<String> requiredVariables = version.getVariables().stream()
                .map(TemplateVariable::getVariableName)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (request.getVariables() == null) {
            throw new MissingRequiredVariableException("Missing required variable: " + requiredVariables.iterator().next());
        }

        for (String variableName : requiredVariables) {
            if (!request.getVariables().containsKey(variableName)) {
                throw new MissingRequiredVariableException("Missing required variable: " + variableName);
            }
        }

        String renderedSubject = renderText(version.getSubject(), request.getVariables());
        String renderedBody = renderText(version.getBody(), request.getVariables());

        return RenderTemplateResponse.builder()
                .subject(renderedSubject)
                .body(renderedBody)
                .build();
    }

    private NotificationTemplateVersion createVersion(NotificationTemplate template, String subject, String body, boolean active) {
        validateTemplateContent(subject, body);

        deactivateExistingActiveVersions(template);

        NotificationTemplateVersion version = NotificationTemplateVersion.builder()
                .template(template)
                .versionNumber(1)
                .subject(subject)
                .body(body)
                .active(active)
                .build();

        NotificationTemplateVersion savedVersion = versionRepository.save(version);

        List<TemplateVariable> variables = extractVariables(subject, body).stream()
                .map(variableName -> TemplateVariable.builder()
                        .version(savedVersion)
                        .variableName(variableName)
                        .build())
                .toList();

        if (!variables.isEmpty()) {
            variableRepository.saveAll(variables);
        }

        savedVersion.setVariables(new ArrayList<>(variables));
        return savedVersion;
    }

    private void validateTemplateContent(String subject, String body) {
        Set<String> seen = new LinkedHashSet<>();
        for (String content : List.of(subject, body)) {
            if (content == null) {
                continue;
            }

            Matcher matcher = VARIABLE_PATTERN.matcher(content);
            while (matcher.find()) {
                String placeholder = matcher.group(1);
                if (!placeholder.matches(VARIABLE_NAME_PATTERN)) {
                    throw new InvalidTemplateVariableException("Invalid variable placeholder: " + placeholder);
                }
                if (!seen.add(placeholder)) {
                    throw new InvalidTemplateVariableException("Duplicate variable placeholder: " + placeholder);
                }
            }

            if (content.contains("{{") && !content.contains("}}")) {
                throw new InvalidTemplateVariableException("Invalid variable placeholder");
            }
        }
    }

    private Set<String> extractVariables(String subject, String body) {
        Set<String> variables = new LinkedHashSet<>();
        for (String content : List.of(subject, body)) {
            if (content == null) {
                continue;
            }

            Matcher matcher = VARIABLE_PATTERN.matcher(content);
            while (matcher.find()) {
                String placeholder = matcher.group(1);
                if (!placeholder.matches(VARIABLE_NAME_PATTERN)) {
                    throw new InvalidTemplateVariableException("Invalid variable placeholder: " + placeholder);
                }
                if (!variables.add(placeholder)) {
                    throw new InvalidTemplateVariableException("Duplicate variable placeholder: " + placeholder);
                }
            }
        }
        return variables;
    }

    private void deactivateExistingActiveVersions(NotificationTemplate template) {
        List<NotificationTemplateVersion> activeVersions = versionRepository.findByTemplateIdAndActiveTrueOrderByVersionNumberDesc(template.getId());
        for (NotificationTemplateVersion version : activeVersions) {
            version.setActive(false);
        }
        versionRepository.saveAll(activeVersions);
    }

    private String renderText(String content, Map<String, String> variables) {
        String rendered = content == null ? "" : content;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            rendered = rendered.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return rendered;
    }
}