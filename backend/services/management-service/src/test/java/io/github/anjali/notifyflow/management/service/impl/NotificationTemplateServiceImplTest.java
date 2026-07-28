package io.github.anjali.notifyflow.management.service.impl;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import io.github.anjali.notifyflow.management.dto.request.CreateNotificationTemplateRequest;
import io.github.anjali.notifyflow.management.dto.request.UpdateNotificationTemplateRequest;
import io.github.anjali.notifyflow.management.dto.response.MessageResponse;
import io.github.anjali.notifyflow.management.dto.response.NotificationTemplateResponse;
import io.github.anjali.notifyflow.management.dto.response.PageResponse;
import io.github.anjali.notifyflow.management.entity.NotificationTemplate;
import io.github.anjali.notifyflow.management.enums.NotificationChannel;
import io.github.anjali.notifyflow.management.exception.DuplicateTemplateKeyException;
import io.github.anjali.notifyflow.management.exception.ResourceNotFoundException;
import io.github.anjali.notifyflow.management.mapper.NotificationTemplateMapper;
import io.github.anjali.notifyflow.management.repository.NotificationTemplateRepository;

@ExtendWith(MockitoExtension.class)
class NotificationTemplateServiceImplTest {

    @Mock
    private NotificationTemplateRepository repository;

    @Mock
    private NotificationTemplateMapper mapper;

    @InjectMocks
    private NotificationTemplateServiceImpl service;

    @Test
    void createTemplatePersistsAndReturnsResponse() {
        CreateNotificationTemplateRequest request = new CreateNotificationTemplateRequest();
        request.setTemplateKey("WELCOME_EMAIL");
        request.setName("Welcome Email");
        request.setChannel(NotificationChannel.EMAIL);
        request.setSubject("Welcome to NotifyFlow");
        request.setBody("Hi {{name}}, welcome!");
        request.setTags(Set.of("AUTH", "WELCOME"));

        NotificationTemplate savedTemplate = NotificationTemplate.builder()
                .id(UUID.randomUUID())
                .templateKey("WELCOME_EMAIL")
                .name("Welcome Email")
                .channel(NotificationChannel.EMAIL)
                .subject("Welcome to NotifyFlow")
                .body("Hi {{name}}, welcome!")
                .tags(Set.of("AUTH", "WELCOME"))
                .build();

        NotificationTemplateResponse expectedResponse = NotificationTemplateResponse.builder()
                .id(savedTemplate.getId())
                .message("Notification template created successfully")
                .build();

        when(repository.existsByTemplateKey("WELCOME_EMAIL")).thenReturn(false);
        when(repository.save(any(NotificationTemplate.class))).thenReturn(savedTemplate);
        when(mapper.toEntity(any(CreateNotificationTemplateRequest.class))).thenReturn(savedTemplate);
        when(mapper.toResponse(savedTemplate, "Notification template created successfully")).thenReturn(expectedResponse);

        NotificationTemplateResponse response = service.createTemplate(request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(savedTemplate.getId());
        assertThat(response.getMessage()).contains("created successfully");
    }

    @Test
    void createTemplateThrowsWhenTemplateKeyAlreadyExists() {
        CreateNotificationTemplateRequest request = new CreateNotificationTemplateRequest();
        request.setTemplateKey("WELCOME_EMAIL");
        request.setName("Welcome Email");
        request.setChannel(NotificationChannel.EMAIL);
        request.setSubject("Welcome to NotifyFlow");
        request.setBody("Hi {{name}}, welcome!");

        when(repository.existsByTemplateKey("WELCOME_EMAIL")).thenReturn(true);

        assertThatThrownBy(() -> service.createTemplate(request))
                .isInstanceOf(DuplicateTemplateKeyException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void getTemplateByIdReturnsTemplateWhenFound() {
        UUID id = UUID.randomUUID();
        NotificationTemplate template = NotificationTemplate.builder()
                .id(id)
                .templateKey("WELCOME_EMAIL")
                .name("Welcome Email")
                .channel(NotificationChannel.EMAIL)
                .subject("Welcome to NotifyFlow")
                .body("Hi {{name}}, welcome!")
                .tags(Set.of("AUTH", "WELCOME"))
                .build();

        NotificationTemplateResponse expectedResponse = NotificationTemplateResponse.builder()
                .id(id)
                .templateKey("WELCOME_EMAIL")
                .message("Notification template created successfully")
                .build();

        when(repository.findById(id)).thenReturn(Optional.of(template));
        when(mapper.toResponse(template)).thenReturn(expectedResponse);

        NotificationTemplateResponse response = service.getTemplateById(id);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(id);
        assertThat(response.getTemplateKey()).isEqualTo("WELCOME_EMAIL");
    }

    @Test
    void getTemplateByIdThrowsWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTemplateById(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Template not found");
    }

    @Test
    void getTemplatesReturnsPaginatedAndFilteredResponse() {
        NotificationTemplate template = NotificationTemplate.builder()
                .id(UUID.randomUUID())
                .templateKey("WELCOME_EMAIL")
                .name("Welcome Email")
                .channel(NotificationChannel.EMAIL)
                .subject("Welcome to NotifyFlow")
                .body("Hi {{name}}, welcome!")
                .tags(Set.of("AUTH", "WELCOME"))
                .build();

        NotificationTemplateResponse expectedResponse = NotificationTemplateResponse.builder()
                .id(template.getId())
                .templateKey("WELCOME_EMAIL")
                .message("Notification template created successfully")
                .build();

        Page<NotificationTemplate> page = new PageImpl<>(List.of(template), PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt")), 1);
        when(repository.findByChannelAndTagsContaining(NotificationChannel.EMAIL, "AUTH", PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt")))).thenReturn(page);
        when(mapper.toResponse(template)).thenReturn(expectedResponse);

        PageResponse<NotificationTemplateResponse> response = service.getTemplates(0, 10, "createdAt", "desc", NotificationChannel.EMAIL, "AUTH");

        assertThat(response).isNotNull();
        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getPage()).isEqualTo(0);
        assertThat(response.getSize()).isEqualTo(10);
        assertThat(response.getTotalElements()).isEqualTo(1);
    }

    @Test
    void updateTemplateReturnsUpdatedResponseWhenTemplateExists() {
        UUID id = UUID.randomUUID();
        UpdateNotificationTemplateRequest request = new UpdateNotificationTemplateRequest();
        request.setName("Updated Welcome Email");
        request.setChannel(NotificationChannel.EMAIL);
        request.setSubject("Welcome!");
        request.setBody("Hi {{name}}, welcome!");
        request.setTags(Set.of("AUTH", "WELCOME"));

        NotificationTemplate existingTemplate = NotificationTemplate.builder()
                .id(id)
                .templateKey("WELCOME_EMAIL")
                .name("Welcome Email")
                .channel(NotificationChannel.EMAIL)
                .subject("Welcome to NotifyFlow")
                .body("Hi {{name}}, welcome!")
                .tags(Set.of("AUTH"))
                .build();

        NotificationTemplate updatedTemplate = NotificationTemplate.builder()
                .id(id)
                .templateKey("WELCOME_EMAIL")
                .name("Updated Welcome Email")
                .channel(NotificationChannel.EMAIL)
                .subject("Welcome!")
                .body("Hi {{name}}, welcome!")
                .tags(Set.of("AUTH", "WELCOME"))
                .build();

        NotificationTemplateResponse expectedResponse = NotificationTemplateResponse.builder()
                .id(id)
                .message("Notification template updated successfully")
                .build();

        when(repository.findById(id)).thenReturn(Optional.of(existingTemplate));
        when(mapper.updateEntity(request, existingTemplate)).thenReturn(updatedTemplate);
        when(repository.save(updatedTemplate)).thenReturn(updatedTemplate);
        when(mapper.toResponse(updatedTemplate, "Notification template updated successfully")).thenReturn(expectedResponse);

        NotificationTemplateResponse response = service.updateTemplate(id, request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(id);
        assertThat(response.getMessage()).contains("updated successfully");
    }

    @Test
    void updateTemplateThrowsWhenTemplateDoesNotExist() {
        UUID id = UUID.randomUUID();
        UpdateNotificationTemplateRequest request = new UpdateNotificationTemplateRequest();
        request.setName("Updated Welcome Email");
        request.setChannel(NotificationChannel.EMAIL);
        request.setSubject("Welcome!");
        request.setBody("Hi {{name}}, welcome!");

        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateTemplate(id, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Template not found");
    }

    @Test
    void deleteTemplateReturnsMessageWhenTemplateExists() {
        UUID id = UUID.randomUUID();
        NotificationTemplate template = NotificationTemplate.builder()
                .id(id)
                .templateKey("WELCOME_EMAIL")
                .name("Welcome Email")
                .channel(NotificationChannel.EMAIL)
                .subject("Welcome to NotifyFlow")
                .body("Hi {{name}}, welcome!")
                .build();

        when(repository.findById(id)).thenReturn(Optional.of(template));
        doNothing().when(repository).delete(template);

        MessageResponse response = service.deleteTemplate(id);

        assertThat(response).isNotNull();
        assertThat(response.getMessage()).contains("deleted successfully");
        verify(repository).delete(template);
    }

    @Test
    void deleteTemplateThrowsWhenTemplateDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteTemplate(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Template not found");
    }
}
