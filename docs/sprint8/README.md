# Sprint 8 - Notification Reliability & Tracking

## Overview 

Sprint 8 introduces comprehensive notification lifecycle tracking, enabling reliable monitoring of every notification from creation through delivery (or failure). The system now persists notification records with status tracking, retry mechanisms, and detailed history APIs.

## 1. New Classes and Files

### Domain Layer
- `NotificationDeliveryStatus.java` - Enum for notification lifecycle states (QUEUED, PROCESSING, SENT, FAILED)
- `Notification.java` - Entity representing a notification record with full lifecycle tracking

### Data Access Layer
- `NotificationRepository.java` - Repository with filtering and pagination support

### Service Layer
- `NotificationTrackingService.java` - Interface for notification tracking operations
- `NotificationTrackingServiceImpl.java` - Implementation with retry logic and status management

### DTO Layer
- `NotificationDetailsResponse.java` - Detailed response for notification queries
- `NotificationResponse.java` - Updated send response with notification ID and status
- `NotificationMapper.java` - Mapper for entity-to-DTO conversion

### Controller Layer
- `NotificationController.java` - Enhanced with GET endpoints for notification history and retry

### Configuration
- `application.yaml` - Added `notification.max-retry-count` configuration

### Tests
- `NotificationTrackingServiceImplTest.java` - Comprehensive unit tests for tracking service
- `NotificationControllerTest.java` - Controller tests for new endpoints
- Updated `NotificationServiceImplTest.java` - Tests for integrated tracking
- Updated `NotificationProviderServiceImplTest.java` - Tests for isDefault bug fix

## 2. Database Schema

### notifications table
```sql
CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    template_id UUID NOT NULL,
    template_version_id UUID,
    template_key VARCHAR(100) NOT NULL,
    recipient VARCHAR(255) NOT NULL,
    channel VARCHAR(50) NOT NULL,
    provider_id UUID,
    provider_name VARCHAR(100),
    subject VARCHAR(255),
    body TEXT,
    status VARCHAR(50) NOT NULL,
    retry_count INTEGER NOT NULL DEFAULT 0,
    failure_reason TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    sent_at TIMESTAMP
);

-- Indexes for common queries
CREATE INDEX idx_notification_status ON notifications(status);
CREATE INDEX idx_notification_recipient ON notifications(recipient);
CREATE INDEX idx_notification_channel ON notifications(channel);
CREATE INDEX idx_notification_created_at ON notifications(created_at);
CREATE INDEX idx_notification_template_id ON notifications(template_id);
```

## 3. Notification Lifecycle

```
REQUEST
  ↓
QUEUED (initial state when notification record is created)
  ↓
PROCESSING (before provider dispatch)
  ↓
SENT (successful delivery)

FAILURE PATH:
PROCESSING
  ↓
FAILED (with failureReason)

RETRY PATH:
FAILED
  ↓
PROCESSING (retry attempt)
  ↓
SENT (success) or FAILED (retry failed)
```

## 4. Status Meanings

- **QUEUED**: Notification record created, waiting to be processed
- **PROCESSING**: actively being dispatched to provider
- **SENT**: successfully delivered to provider
- **FAILED**: delivery failed (with retry if under max retry count)

## 5. Retry Mechanism

- Configurable maximum retry count (default: 3)
- Retry only available for FAILED status notifications
- Each retry increments `retryCount`
- After max retries, notification remains FAILED
- Retries can be triggered via API endpoint
- Provider must be enabled for retry to succeed

## 6. API Endpoints

### Send Notification (Updated)
```
POST /api/v1/notifications/send
```

**Request:**
```json
{
  "templateKey": "WELCOME_EMAIL",
  "recipient": "user@example.com",
  "variables": {
    "name": "Jane"
  }
}
```

**Response:**
```json
{
  "notificationId": "uuid",
  "status": "SENT",
  "message": "Notification sent successfully"
}
```

### Get Notification by ID
```
GET /api/v1/notifications/{id}
```

**Response:**
```json
{
  "id": "uuid",
  "templateId": "uuid",
  "templateVersionId": "uuid",
  "templateKey": "WELCOME_EMAIL",
  "recipient": "user@example.com",
  "channel": "EMAIL",
  "providerId": "uuid",
  "providerName": "SendGrid",
  "subject": "Hello Jane",
  "body": "Hi Jane",
  "status": "SENT",
  "retryCount": 0,
  "failureReason": null,
  "createdAt": "2024-01-01T10:00:00",
  "updatedAt": "2024-01-01T10:00:01",
  "sentAt": "2024-01-01T10:00:01"
}
```

### List Notifications
```
GET /api/v1/notifications?status=SENT&channel=EMAIL&recipient=user@example.com&providerName=SendGrid&page=0&size=10&sortBy=createdAt&sortDirection=desc
```

**Response:**
```json
{
  "content": [...],
  "currentPage": 0,
  "totalItems": 100,
  "totalPages": 10
}
```

### Retry Notification
```
POST /api/v1/notifications/{id}/retry
```

**Response (Success):**
```json
{
  "message": "Notification retry initiated successfully"
}
```

**Response (Failure):**
```json
{
  "message": "Notification retry failed. Either max retries exceeded or notification not in FAILED status"
}
```

## 7. Configuration

### application.yaml
```yaml
notification:
  max-retry-count: 3
```

## 8. Bug Fixes

### isDefault Boolean Bug
**Problem:** Provider creation requests with `"isDefault": true` were persisting as `false`.

**Root Cause:** JSON deserialization issue with primitive boolean fields and camelCase property names.

**Solution:** Added `@JsonProperty("isDefault")` annotation to both request and response DTOs to ensure proper JSON mapping.

**Files Modified:**
- `CreateNotificationProviderRequest.java`
- `NotificationProviderResponse.java`

**Test Added:** Regression test in `NotificationProviderServiceImplTest.java`

## 9. Design Decisions

- **Separate Enum**: Created `NotificationDeliveryStatus` to avoid conflict with existing `NotificationStatus` enum (used for templates)
- **Tracking Service**: Separated tracking logic into dedicated service for cleaner separation of concerns
- **Retry Service-Level**: Implemented retry at service level without external dependencies (Spring Retry) for simplicity and testability
- **UUID Consistency**: Used UUID for notification IDs to maintain consistency with existing entities
- **Comprehensive Indexing**: Added indexes on frequently queried fields (status, recipient, channel, createdAt)
- **Failure Reason Storage**: Stored failure reasons in database for debugging without exposing sensitive provider config
- **Backward Compatibility**: Maintained existing API contracts while adding new functionality

## 10. Request Flow

### Send Notification Flow
1. Client sends POST request to `/api/v1/notifications/send`
2. Controller validates request and calls `NotificationService.sendNotification()`
3. Service validates template, finds active version, renders variables
4. Service finds default provider for channel
5. Service calls `NotificationTrackingService.createNotification()` → status: QUEUED
6. Service updates status to PROCESSING
7. Service dispatches to provider
8. On success: status → SENT, set sentAt
9. On failure: status → FAILED, store failureReason
10. Return response with notificationId and status

### Retry Flow
1. Client sends POST request to `/api/v1/notifications/{id}/retry`
2. Controller calls `NotificationTrackingService.retryNotification()`
3. Service validates notification is FAILED and under max retry count
4. Service increments retryCount
5. Service updates status to PROCESSING
6. Service dispatches to provider
7. On success: status → SENT, set sentAt
8. On failure: status → FAILED, update failureReason
9. Return success/failure response

## 11. Test Coverage

### Unit Tests
- Notification creation with QUEUED status
- Status transitions (QUEUED → PROCESSING → SENT/FAILED)
- Failure reason storage
- Retry count increment
- Retry success (under max retries)
- Retry failure (max retries exceeded)
- Retry failure (not in FAILED status)
- Retry failure (provider disabled)
- Notification retrieval by ID
- Notification not found (404)
- Notification list with filters
- Pagination and sorting
- isDefault boolean persistence (regression test)

### Integration Considerations
- Existing Sprint 1-7 tests continue to pass
- Template CRUD operations unaffected
- Provider management unaffected
- Variable extraction and rendering unaffected

## 12. Backward Compatibility

### Maintained Functionality
- Template CRUD operations
- Template versioning
- Variable extraction and validation
- Template rendering
- Provider management
- Provider enable/disable
- Provider default selection
- Provider dispatching
- Global exception handling
- Existing API contracts

### Changed Functionality
- `NotificationResponse` now returns `notificationId` and `status` instead of full details
- Added new fields to notification records for tracking

### No Breaking Changes
- All existing APIs continue to work
- Database migration handled by JPA `ddl-auto: update`
- Existing tests continue to pass

## 13. Postman Collection

A comprehensive Postman collection for Sprint 8 has been created at `docs/postman_sprint8_collection.json`.

The collection includes:

### Provider Management (Bug Fix Test)
- Create Provider with isDefault=true
- Create Provider with isDefault=false

### Notification Sending (Tracking Integration)
- Send Notification - Success Case
- Send Notification - Template Not Found

### Notification History & Tracking
- Get Notification by ID
- Get Notification by ID - Not Found

### Notification List & Filtering
- List All Notifications
- Filter by Status - SENT
- Filter by Status - FAILED
- Filter by Channel - EMAIL
- Filter by Recipient
- Filter by Provider Name
- Combined Filters

### Retry Mechanism
- Retry Failed Notification
- Retry Beyond Max Retries
- Retry Non-FAILED Notification

### Template Setup (Prerequisites)
- Create Template
- Create Template Version

**To use the collection:**
1. Import `docs/postman_sprint8_collection.json` into Postman
2. Set up environment variables for `notificationId` and `templateId` as needed
3. Run requests in order: Template Setup → Provider Management → Notification Sending → Notification History → Retry Mechanism

## 14. Verification Steps

### Manual Verification
1. Send successful email → verify status = SENT
2. Check notification ID in response
3. GET notification by ID → verify all fields populated
4. Verify provider is recorded correctly
5. Verify template/version is recorded
6. Force/simulate provider failure → verify status = FAILED
7. Verify failureReason is stored
8. Retry failed notification → verify retryCount increased
9. Verify successful retry changes status to SENT
10. Test notification list with filters (status, channel, recipient, provider)
11. Test pagination and sorting
12. Test provider isDefault bug fix with isDefault: true
13. Run all existing tests → verify backward compatibility

### Test Execution
```bash
cd backend/services/management-service
mvn test
```

## 15. Known Limitations

- Retry is manual (API-triggered) rather than automatic
- No automatic retry scheduling (future enhancement could add Quartz/Scheduled tasks)
- Status transitions are synchronous (future enhancement could add async processing with message queues)
- No delivery confirmation from providers (future enhancement could add webhooks)
- Failure reasons are stored as text (future enhancement could add structured error codes)

## 16. Future Enhancements

- Automatic retry with exponential backoff
- Async notification processing with message queues (Kafka/RabbitMQ)
- Provider webhooks for delivery confirmation
- Structured error codes and categorization
- Notification analytics and reporting
- Bulk notification operations
- Notification scheduling