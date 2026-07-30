# Sprint 4 — Template Versioning

## Overview
This sprint extends the existing notification template management flow to preserve template history instead of overwriting prior content on update.

## New database model
- Existing table: notification_templates remains the metadata container.
- New table: notification_template_versions stores every historical snapshot.
- notification_templates.active_version tracks the currently active version.

## New API surface
- POST /api/v1/templates
- PUT /api/v1/templates/{id}
- GET /api/v1/templates/{id}/versions
- GET /api/v1/templates/{id}/versions/{version}

## Request flow
1. Create template: a metadata row is inserted and version 1 is created.
2. Update template: the template metadata is updated and a new version row is inserted.
3. Read versions: the service loads every historical version ordered by version desc.
4. Read specific version: the service resolves a single version by template id and version number.

## Design decisions
- The original template table remains intact and becomes metadata-only.
- Every update produces a new immutable version row instead of mutating historical content.
- The service layer owns the versioning logic so controllers remain thin.
- Spring Data JPA repositories are used with derived queries rather than native SQL.

## Folder structure
- backend/services/management-service/src/main/java/io/github/anjali/notifyflow/management/controller
- backend/services/management-service/src/main/java/io/github/anjali/notifyflow/management/service
- backend/services/management-service/src/main/java/io/github/anjali/notifyflow/management/service/impl
- backend/services/management-service/src/main/java/io/github/anjali/notifyflow/management/entity
- backend/services/management-service/src/main/java/io/github/anjali/notifyflow/management/repository
- backend/services/management-service/src/main/java/io/github/anjali/notifyflow/management/mapper
- backend/services/management-service/src/main/java/io/github/anjali/notifyflow/management/dto/request
- backend/services/management-service/src/main/java/io/github/anjali/notifyflow/management/dto/response

## New classes
- NotificationTemplateVersion
- NotificationTemplateVersionResponse
- NotificationTemplateVersionRepository

## Modified classes
- NotificationTemplate
- NotificationTemplateService
- NotificationTemplateServiceImpl
- NotificationTemplateController
- NotificationTemplateMapper

## Postman examples

### 1. Create template
POST http://localhost:8081/api/v1/templates
Content-Type: application/json

{
  "templateKey": "WELCOME_EMAIL",
  "name": "Welcome Email",
  "channel": "EMAIL",
  "subject": "Welcome to NotifyFlow",
  "body": "Hi {{name}}, welcome!",
  "tags": ["WELCOME", "AUTH"]
}

Expected database state:
- notification_templates row created with active_version = 1
- notification_template_versions contains version 1

### 2. Update template
PUT http://localhost:8081/api/v1/templates/{id}
Content-Type: application/json

{
  "name": "Welcome Email",
  "channel": "EMAIL",
  "subject": "Hello from NotifyFlow",
  "body": "Hello {{name}}, welcome!",
  "tags": ["WELCOME", "AUTH"]
}

Expected database state:
- notification_templates.active_version becomes 2
- notification_template_versions contains version 1 and version 2

### 3. Get all versions
GET http://localhost:8081/api/v1/templates/{id}/versions

Expected response:
- versions ordered newest first

### 4. Get specific version
GET http://localhost:8081/api/v1/templates/{id}/versions/2

Expected response:
- the exact version snapshot for version 2
