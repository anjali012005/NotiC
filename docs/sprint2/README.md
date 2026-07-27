# Sprint 2 - Template Retrieval APIs

## 1. Folder structure

backend/services/management-service/src/main/java/io/github/anjali/notifyflow/management/
- controller/
  - NotificationTemplateController.java
- dto/
  - request/
    - CreateNotificationTemplateRequest.java
  - response/
    - NotificationTemplateResponse.java
    - PageResponse.java
- entity/
  - NotificationTemplate.java
- enums/
  - NotificationChannel.java
- exception/
  - DuplicateTemplateKeyException.java
  - GlobalExceptionHandler.java
  - ResourceNotFoundException.java
- mapper/
  - NotificationTemplateMapper.java
- repository/
  - NotificationTemplateRepository.java
- service/
  - NotificationTemplateService.java
  - impl/
    - NotificationTemplateServiceImpl.java

## 2. Every new class

- NotificationTemplateResponse
  - Represents the public response payload returned by the retrieval APIs.
- PageResponse<T>
  - Wraps paginated results with pagination metadata.
- ResourceNotFoundException
  - Signals when a template cannot be found for a given UUID or templateKey.
- NotificationTemplateRepository
  - Adds Spring Data JPA query methods for lookup and filtering.
- NotificationTemplateServiceImpl
  - Implements the retrieval business logic for single-item and paginated endpoints.
- NotificationTemplateController
  - Exposes the Sprint 2 REST endpoints.

## 3. Why each class exists

- NotificationTemplateResponse exists so the API never returns the JPA entity directly.
- PageResponse<T> exists to return the required pagination envelope: content, page, size, totalPages, totalElements, and last.
- ResourceNotFoundException exists to standardize 404 behavior for missing templates.
- NotificationTemplateRepository exists so the service can query templates with Spring Data JPA and Pageable.
- NotificationTemplateServiceImpl exists to keep business rules and data access orchestration in one place.
- NotificationTemplateController exists to expose HTTP endpoints and map query parameters to service calls.

## 4. Request flow

1. The client calls one of the template retrieval endpoints.
2. NotificationTemplateController receives the request and validates the incoming parameters.
3. The controller delegates to NotificationTemplateService.
4. The service calls NotificationTemplateRepository to fetch data.
5. The repository returns entities, which are converted to response DTOs by the mapper.
6. The controller returns the DTO or paginated DTO response to the client.

## 5. Repository methods

- findById(UUID id)
  - Spring Data JPA default method used for retrieving a template by UUID.
- findByTemplateKey(String templateKey)
  - Derived query method for fetching a template by its natural key.
- findByChannel(NotificationChannel channel, Pageable pageable)
  - Derived query method for filtering results by channel.
- findByTagsContaining(String tag, Pageable pageable)
  - Derived query method for filtering results by tag.
- findByChannelAndTagsContaining(NotificationChannel channel, String tag, Pageable pageable)
  - Derived query method for combining channel and tag filtering.

## 6. Pageable

Pageable is used to encapsulate pagination and sorting parameters sent by the client. It allows the service to request a page of data efficiently and return metadata about the current result set.

## 7. Sorting

Sorting is handled by extracting sortBy and sortDir from the request parameters. The service builds a Spring Sort object and passes it to the repository through Pageable.

## 8. Filtering

Filtering is supported through optional query parameters:
- channel: filters templates by notification channel.
- tag: filters templates that contain the specified tag.
If neither filter is supplied, all templates are returned.

## 9. Design decisions

- The API uses DTOs rather than exposing entities to keep the contract stable and avoid leaking persistence internals.
- The service layer is separated from the controller and repository for clean architecture and testability.
- Constructor injection is used to make dependencies explicit and easy to test.
- Spring Data JPA derived methods are preferred over JPQL for readability and maintainability.
- A dedicated not-found exception is used so the application can return a consistent 404 response.

## 10. Where each file should be created

- Controller: backend/services/management-service/src/main/java/io/github/anjali/notifyflow/management/controller/NotificationTemplateController.java
- Service interface: backend/services/management-service/src/main/java/io/github/anjali/notifyflow/management/service/NotificationTemplateService.java
- Service implementation: backend/services/management-service/src/main/java/io/github/anjali/notifyflow/management/service/impl/NotificationTemplateServiceImpl.java
- Repository: backend/services/management-service/src/main/java/io/github/anjali/notifyflow/management/repository/NotificationTemplateRepository.java
- DTOs: backend/services/management-service/src/main/java/io/github/anjali/notifyflow/management/dto/response/NotificationTemplateResponse.java and backend/services/management-service/src/main/java/io/github/anjali/notifyflow/management/dto/response/PageResponse.java
- Mapper: backend/services/management-service/src/main/java/io/github/anjali/notifyflow/management/mapper/NotificationTemplateMapper.java
- Exceptions: backend/services/management-service/src/main/java/io/github/anjali/notifyflow/management/exception/ResourceNotFoundException.java and backend/services/management-service/src/main/java/io/github/anjali/notifyflow/management/exception/GlobalExceptionHandler.java
