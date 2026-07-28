# Sprint 3: Update and Delete Notification Templates

## 1. Folder structure

```text
backend/services/management-service/
├── src/main/java/io/github/anjali/notifyflow/management/
│   ├── controller/
│   │   └── NotificationTemplateController.java
│   ├── dto/
│   │   ├── request/
│   │   │   ├── CreateNotificationTemplateRequest.java
│   │   │   └── UpdateNotificationTemplateRequest.java
│   │   └── response/
│   │       ├── MessageResponse.java
│   │       ├── NotificationTemplateResponse.java
│   │       └── PageResponse.java
│   ├── entity/
│   │   └── NotificationTemplate.java
│   ├── exception/
│   │   ├── DuplicateTemplateKeyException.java
│   │   ├── GlobalExceptionHandler.java
│   │   └── ResourceNotFoundException.java
│   ├── mapper/
│   │   └── NotificationTemplateMapper.java
│   ├── repository/
│   │   └── NotificationTemplateRepository.java
│   ├── service/
│   │   ├── NotificationTemplateService.java
│   │   └── impl/
│   │       └── NotificationTemplateServiceImpl.java
│   └── enums/
│       └── NotificationChannel.java
└── src/test/java/io/github/anjali/notifyflow/management/service/impl/
    └── NotificationTemplateServiceImplTest.java
```

## 2. Every new class

- NotificationTemplateController
  - Handles the new PUT and DELETE endpoints for templates.
  - Keeps the controller thin and delegates business logic to the service layer.

- UpdateNotificationTemplateRequest
  - DTO used for update payloads.
  - Uses Bean Validation so invalid requests fail with meaningful errors.

- MessageResponse
  - DTO for success responses such as delete confirmation.

- Sprint 3 implementation is also covered in service and mapper classes.

## 3. Every modified class

- NotificationTemplateService
  - Extended with updateTemplate and deleteTemplate methods.

- NotificationTemplateServiceImpl
  - Implements update and delete business rules.
  - Loads the existing entity, updates allowed fields, saves changes, and deletes the entity when requested.

- NotificationTemplateMapper
  - Reused for mapping logic.
  - Added updateEntity so the same mapper is responsible for update transformations.

- NotificationTemplateController
  - Added PUT /api/v1/templates/{id} and DELETE /api/v1/templates/{id}.

- NotificationTemplateServiceImplTest
  - Added unit tests covering successful update, missing-template update, successful delete, and missing-template delete scenarios.

## 4. Why each class exists

- Controller: exposes HTTP endpoints and keeps request handling lightweight.
- Service: contains update and delete business rules and enforces not-found behavior.
- Repository: persists and retrieves the entity using Spring Data JPA.
- Mapper: transforms DTOs to entities and back without exposing entities to clients.
- DTOs: define request and response shapes for clean API contracts.
- Exception handler: centralizes validation and not-found error responses.

## 5. Request flow

1. Client sends a request to the controller.
2. The controller validates the request body and delegates to the service.
3. The service loads the template from the repository or throws ResourceNotFoundException.
4. The mapper updates the entity and the repository persists the change.
5. The mapper converts the persisted entity to a response DTO.
6. The controller returns the response to the client.

## 6. Update flow

- PUT /api/v1/templates/{id}
- The service finds the existing template by ID.
- Only the allowed fields are updated: name, channel, subject, body, and tags.
- The system preserves templateKey, id, createdAt, and updates updatedAt automatically through JPA lifecycle callbacks.
- The response body contains the template ID and a success message.

## 7. Delete flow

- DELETE /api/v1/templates/{id}
- The service finds the existing template by ID.
- If missing, a 404 response is returned.
- If present, the template is deleted and a confirmation message is returned.

## 8. Design decisions

- Layered architecture was preserved: controller -> service -> repository.
- Constructor injection is used through Lombok to keep dependencies explicit.
- Bean Validation is used for request validation.
- The entity is never exposed directly to the API layer.
- Existing repository methods and mapper structure were reused instead of introducing new persistence patterns.

## 9. Where each file should be placed

- Controller classes go in the controller package under the management-service main source tree.
- Request/response DTOs go in the dto/request and dto/response packages.
- Service interfaces and implementations go in the service and service/impl packages.
- Persistence logic belongs in the repository package.
- Tests belong in src/test/java under the matching package structure.
