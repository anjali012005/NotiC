package io.github.anjali.notifyflow.management.service.impl;

import java.util.List;
import java.util.Iterator;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.anjali.notifyflow.management.dto.request.CreateNotificationProviderRequest;
import io.github.anjali.notifyflow.management.dto.request.UpdateNotificationProviderRequest;
import io.github.anjali.notifyflow.management.dto.response.MessageResponse;
import io.github.anjali.notifyflow.management.dto.response.NotificationProviderResponse;
import io.github.anjali.notifyflow.management.entity.NotificationProvider;
import io.github.anjali.notifyflow.management.enums.NotificationChannel;
import io.github.anjali.notifyflow.management.enums.ProviderType;
import io.github.anjali.notifyflow.management.exception.DuplicateProviderException;
import io.github.anjali.notifyflow.management.exception.InvalidProviderConfigurationException;
import io.github.anjali.notifyflow.management.exception.ResourceNotFoundException;
import io.github.anjali.notifyflow.management.mapper.NotificationProviderMapper;
import io.github.anjali.notifyflow.management.repository.NotificationProviderRepository;
import io.github.anjali.notifyflow.management.security.TenantAuthorizationService;
import io.github.anjali.notifyflow.management.security.TenantContext;
import io.github.anjali.notifyflow.management.service.NotificationProviderService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationProviderServiceImpl implements NotificationProviderService {

    private static final ObjectMapper CONFIG_MAPPER = new ObjectMapper();

    private final NotificationProviderRepository repository;
    private final NotificationProviderMapper mapper;
    private final TenantAuthorizationService tenantAuthorizationService;

    @Override
    @Transactional
    public NotificationProviderResponse createProvider(CreateNotificationProviderRequest request) {
        validateConfiguration(request.getProviderType(), request.getConfig());
        validateUniqueName(request.getChannel(), request.getName());

        NotificationProvider provider = mapper.toEntity(request);
        provider.setTenantId(currentTenantId());

        if (provider.isDefault()) {
            provider.setEnabled(true);
            clearDefaultForChannel(provider.getChannel());
        }

        NotificationProvider saved = repository.save(provider);

        return mapper.toResponse(saved, "Notification provider created successfully");
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationProviderResponse> getProviders(NotificationChannel channel, Boolean enabled) {
        List<NotificationProvider> providers;
        UUID tenantId = currentTenantId();
        if (channel != null && enabled != null) {
            providers = repository.findByTenantIdAndChannelAndEnabledOrderByNameAsc(tenantId, channel, enabled);
        } else if (channel != null) {
            providers = repository.findByTenantIdAndChannelOrderByNameAsc(tenantId, channel);
        } else if (enabled != null) {
            providers = repository.findByTenantIdAndEnabledOrderByNameAsc(tenantId, enabled);
        } else {
            providers = repository.findByTenantIdOrderByNameAsc(tenantId);
        }

        return providers.stream().map(provider -> mapper.toResponse(provider, "Notification provider retrieved successfully")).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationProviderResponse getProviderById(UUID id) {
        NotificationProvider provider = findByIdOrThrow(id);
        return mapper.toResponse(provider);
    }

    @Override
    @Transactional
    public NotificationProviderResponse updateProvider(UUID id, UpdateNotificationProviderRequest request) {
        validateConfiguration(request.getProviderType(), request.getConfig());
        NotificationProvider existing = findByIdOrThrow(id);
        if (!existing.getName().equals(request.getName()) || existing.getChannel() != request.getChannel()) {
            validateUniqueName(request.getChannel(), request.getName());
        }

        mapper.updateEntity(request, existing);
        if (Boolean.TRUE.equals(request.getIsDefault())) {
            clearDefaultForChannel(existing.getChannel());
            existing.setDefault(true);
        }

        NotificationProvider saved = repository.save(existing);
        return mapper.toResponse(saved, "Notification provider updated successfully");
    }

    @Override
    @Transactional
    public MessageResponse deleteProvider(UUID id) {
        NotificationProvider provider = findByIdOrThrow(id);
        repository.delete(provider);
        return MessageResponse.builder().message("Notification provider deleted successfully").build();
    }

    @Override
    @Transactional
    public NotificationProviderResponse enableProvider(UUID id) {
        NotificationProvider provider = findByIdOrThrow(id);
        provider.setEnabled(true);
        NotificationProvider saved = repository.save(provider);
        return mapper.toResponse(saved, "Notification provider enabled successfully");
    }

    @Override
    @Transactional
    public NotificationProviderResponse disableProvider(UUID id) {
        NotificationProvider provider = findByIdOrThrow(id);
        provider.setEnabled(false);
        NotificationProvider saved = repository.save(provider);
        return mapper.toResponse(saved, "Notification provider disabled successfully");
    }

    @Override
    @Transactional
    public NotificationProviderResponse markAsDefault(UUID id) {
        NotificationProvider provider = findByIdOrThrow(id);
        clearDefaultForChannel(provider.getChannel());
        provider.setDefault(true);
        provider.setEnabled(true);
        NotificationProvider saved = repository.save(provider);
        return mapper.toResponse(saved, "Notification provider marked as default successfully");
    }

    private NotificationProvider findByIdOrThrow(UUID id) {
        NotificationProvider provider = repository.findByTenantIdAndId(currentTenantId(), id)
                .orElseGet(() -> repository.findById(id).orElse(null));
        if (provider == null) {
            throw new ResourceNotFoundException("Provider not found with id: " + id);
        }
        tenantAuthorizationService.requireTenantAccess(provider.getTenantId());
        return provider;
    }

    private UUID currentTenantId() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return tenantId == null ? UUID.fromString("00000000-0000-0000-0000-000000000000") : tenantId;
    }

    private void validateUniqueName(NotificationChannel channel, String name) {
        if (channel == null || name == null || name.isBlank()) {
            return;
        }
        boolean exists = repository.existsByTenantIdAndChannelAndName(currentTenantId(), channel, name.trim());
        if (exists) {
            throw new DuplicateProviderException("Provider with name '" + name + "' already exists for channel '" + channel + "'");
        }
    }

    private void clearDefaultForChannel(NotificationChannel channel) {
        Optional<NotificationProvider> currentDefault = repository.findByTenantIdAndChannelAndIsDefaultTrue(
                currentTenantId(), channel);
        currentDefault.ifPresent(provider -> {
            provider.setDefault(false);
            repository.save(provider);
        });
    }

    private void validateConfiguration(ProviderType providerType, String config) {
        if (providerType != ProviderType.SENDGRID) {
            return;
        }
        final JsonNode node;
        try {
            node = CONFIG_MAPPER.readTree(config);
        } catch (Exception ex) {
            throw new InvalidProviderConfigurationException("SendGrid configuration must be valid JSON with a fromEmail field");
        }
        if (node == null || !node.isObject() || node.path("fromEmail").asText().isBlank()) {
            throw new InvalidProviderConfigurationException("SendGrid configuration must include a fromEmail field");
        }
        Iterator<String> fields = node.fieldNames();
        while (fields.hasNext()) {
            if ("apikey".equalsIgnoreCase(fields.next())) {
                throw new InvalidProviderConfigurationException("SendGrid API keys must be configured with SENDGRID_API_KEY, not stored in provider configuration");
            }
        }
    }
}
