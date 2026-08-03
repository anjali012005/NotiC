package io.github.anjali.notifyflow.management.service.impl;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.anjali.notifyflow.management.dto.request.CreateNotificationProviderRequest;
import io.github.anjali.notifyflow.management.dto.request.UpdateNotificationProviderRequest;
import io.github.anjali.notifyflow.management.dto.response.MessageResponse;
import io.github.anjali.notifyflow.management.dto.response.NotificationProviderResponse;
import io.github.anjali.notifyflow.management.entity.NotificationProvider;
import io.github.anjali.notifyflow.management.enums.NotificationChannel;
import io.github.anjali.notifyflow.management.exception.DuplicateProviderException;
import io.github.anjali.notifyflow.management.exception.ResourceNotFoundException;
import io.github.anjali.notifyflow.management.mapper.NotificationProviderMapper;
import io.github.anjali.notifyflow.management.repository.NotificationProviderRepository;
import io.github.anjali.notifyflow.management.service.NotificationProviderService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationProviderServiceImpl implements NotificationProviderService {

    private final NotificationProviderRepository repository;
    private final NotificationProviderMapper mapper;

    @Override
    @Transactional
    public NotificationProviderResponse createProvider(CreateNotificationProviderRequest request) {
        validateUniqueName(request.getChannel(), request.getName());

        NotificationProvider provider = mapper.toEntity(request);
        if (provider.isDefault()) {
            clearDefaultForChannel(provider.getChannel());
        }

        NotificationProvider saved = repository.save(provider);
        return mapper.toResponse(saved, "Notification provider created successfully");
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationProviderResponse> getProviders(NotificationChannel channel, Boolean enabled) {
        List<NotificationProvider> providers;
        if (channel != null && enabled != null) {
            providers = repository.findByChannelAndEnabledOrderByNameAsc(channel, enabled);
        } else if (channel != null) {
            providers = repository.findByChannelOrderByNameAsc(channel);
        } else if (enabled != null) {
            providers = repository.findByEnabledOrderByNameAsc(enabled);
        } else {
            providers = repository.findAllByOrderByNameAsc();
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
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found with id: " + id));
    }

    private void validateUniqueName(NotificationChannel channel, String name) {
        if (channel == null || name == null || name.isBlank()) {
            return;
        }
        boolean exists = repository.existsByChannelAndName(channel, name.trim());
        if (exists) {
            throw new DuplicateProviderException("Provider with name '" + name + "' already exists for channel '" + channel + "'");
        }
    }

    private void clearDefaultForChannel(NotificationChannel channel) {
        Optional<NotificationProvider> currentDefault = repository.findByChannelAndIsDefaultTrue(channel);
        currentDefault.ifPresent(provider -> {
            provider.setDefault(false);
            repository.save(provider);
        });
    }
}
