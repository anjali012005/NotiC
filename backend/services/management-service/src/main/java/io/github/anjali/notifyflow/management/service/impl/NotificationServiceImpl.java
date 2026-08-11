package io.github.anjali.notifyflow.management.service.impl;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.anjali.notifyflow.management.dto.request.SendNotificationRequest;
import io.github.anjali.notifyflow.management.dto.response.NotificationResponse;
import io.github.anjali.notifyflow.management.entity.NotificationProvider;
import io.github.anjali.notifyflow.management.entity.NotificationTemplate;
import io.github.anjali.notifyflow.management.entity.NotificationTemplateVersion;
import io.github.anjali.notifyflow.management.entity.TemplateVariable;
import io.github.anjali.notifyflow.management.exception.MissingRequiredVariableException;
import io.github.anjali.notifyflow.management.exception.ResourceNotFoundException;
import io.github.anjali.notifyflow.management.repository.NotificationProviderRepository;
import io.github.anjali.notifyflow.management.repository.NotificationTemplateRepository;
import io.github.anjali.notifyflow.management.repository.NotificationTemplateVersionRepository;
import io.github.anjali.notifyflow.management.service.NotificationService;
import io.github.anjali.notifyflow.management.service.dispatch.NotificationDispatcher;
import io.github.anjali.notifyflow.management.service.dispatch.NotificationDispatcherFactory;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationTemplateRepository templateRepository;
    private final NotificationTemplateVersionRepository versionRepository;
    private final NotificationProviderRepository providerRepository;
    private final NotificationDispatcherFactory dispatcherFactory;

    @Override
    @Transactional
    public NotificationResponse sendNotification(SendNotificationRequest request) {
        NotificationTemplate template = templateRepository.findByTemplateKey(request.getTemplateKey())
                .orElseThrow(() -> new ResourceNotFoundException("Template not found with key: " + request.getTemplateKey()));

        NotificationTemplateVersion activeVersion = versionRepository.findByTemplate_IdAndActiveTrue(template.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No active version found for template: " + template.getTemplateKey()));

        validateRequiredVariables(activeVersion, request.getVariables());

        String renderedSubject = renderText(activeVersion.getSubject(), request.getVariables());
        String renderedBody = renderText(activeVersion.getBody(), request.getVariables());

        NotificationProvider provider = providerRepository.findByChannelAndIsDefaultTrue(template.getChannel())
                .orElseThrow(() -> new ResourceNotFoundException("Default provider not found for channel: " + template.getChannel()));

        if (!provider.isEnabled()) {
            throw new ResourceNotFoundException("Provider is not enabled: " + provider.getName());
        }

        NotificationDispatcher dispatcher = dispatcherFactory.resolveDispatcher(provider);
        dispatcher.dispatch(provider, request.getRecipient(), renderedSubject, renderedBody);

        return NotificationResponse.builder()
                .id(template.getId())
                .templateKey(template.getTemplateKey())
                .recipient(request.getRecipient())
                .subject(renderedSubject)
                .body(renderedBody)
                .providerName(provider.getName())
                .providerType(provider.getProviderType().name())
                .message("Notification dispatched successfully")
                .build();
    }

    private void validateRequiredVariables(NotificationTemplateVersion version, Map<String, String> variables) {
        Set<String> requiredVariables = version.getVariables() == null ? Set.of() : version.getVariables().stream()
                .filter(Objects::nonNull)
                .map(TemplateVariable::getVariableName)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (requiredVariables.isEmpty()) {
            return;
        }

        if (variables == null) {
            throw new MissingRequiredVariableException("Missing required variable: " + requiredVariables.iterator().next());
        }

        for (String variableName : requiredVariables) {
            if (!variables.containsKey(variableName)) {
                throw new MissingRequiredVariableException("Missing required variable: " + variableName);
            }
        }
    }

    private String renderText(String content, Map<String, String> variables) {
        String rendered = content == null ? "" : content;
        if (variables == null) {
            return rendered;
        }
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            rendered = rendered.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return rendered;
    }
}
