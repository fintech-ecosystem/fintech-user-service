# User Service Document

## Document Information

---

## Service Overview

**fintech\-user\-service** is responsible for managing customer profile data after a user account is created by `fintech-auth-service`\.

The service stores business\-facing customer information such as:

```Plain Text
customerId
authUserId
email
phone number
full name
date of birth
customer status
customer type
profile completion status
```

This service does not handle password, login, token, refresh token, or authentication credentials\.

Those responsibilities belong to `fintech-auth-service`\.

---

## Why This Service Exists

In a FinTech system, authentication data and customer profile data should be separated\.

`auth-service` answers:

```Plain Text
Who can log in?
What roles does this account have?
Is the password/token valid?
```

`user-service` answers:

```Plain Text
Who is this customer in business context?
What is the customer's profile?
Is the customer active, blocked, or closed?
Has the customer completed basic profile information?
What customer data should other services use?
```

This separation keeps security logic and customer domain logic independent\.

---

## Service Responsibility

## 4\.1 Owns

`fintech-user-service` owns:

```Plain Text
customer profile
customer status
customer type
customer contact information
profile completion state
customer lifecycle state
customer preference
customer metadata
customer-created domain event
customer-updated domain event
```

## 4\.2 Does Not Own

`fintech-user-service` does not own:

```Plain Text
password
password hash
login session
access token
refresh token
KYC verification result
wallet balance
ledger account
payment transaction
loan application
risk score
settlement batch
reconciliation result
```

---

## Relationship With Other Services

---

## Boundary With auth\-service

## 6\.1 auth\-service

`auth-service` owns:

```Plain Text
auth_user_id
email for login
password_hash
roles
refresh_tokens
login history
account credential status
```

## 6\.2 user\-service

`user-service` owns:

```Plain Text
customer_id
auth_user_id reference
business profile
full name
phone number
date of birth
customer status
customer type
```

## 6\.3 Important Rule

`user-service` must not store password or token data\.

`auth-service` must not store detailed customer business profile\.

---

## Main Domain Concepts

## 7\.1 Customer

A customer is the business representation of a user account\.

Important fields:

```Plain Text
customerId
authUserId
email
phoneNumber
fullName
dateOfBirth
status
customerType
createdAt
updatedAt
```

---

## 7\.2 Customer Status

Customer status represents whether the customer can use the platform\.

Supported statuses:

```Plain Text
ACTIVE
SUSPENDED
CLOSED
```

Meaning:

---

## 7\.3 Customer Type

Customer type represents the kind of customer\.

Supported values:

```Plain Text
INDIVIDUAL
BUSINESS
```

For v0\.1, only `INDIVIDUAL` is required\.

`BUSINESS` can be added later for merchant/business onboarding\.

---

## Customer Lifecycle

## 8\.1 Normal Flow

```Plain Text
auth-service creates account
→ auth.user_registered.v1 emitted
→ user-service creates customer profile
→ customer updates profile
→ profile status becomes COMPLETED
→ customer submits KYC through kyc-service
```

## 8\.2 Suspension Flow

```Plain Text
ACTIVE
→ SUSPENDED
```

Reason examples:

```Plain Text
fraud suspicion
manual admin action
compliance review
too many suspicious activities
```

## 8\.3 Reactivation Flow

```Plain Text
SUSPENDED
→ ACTIVE
```

## 8\.4 Closure Flow

```Plain Text
ACTIVE or SUSPENDED
→ CLOSED
```

A closed customer should not perform new financial actions\.

---

## Business Rules

## 9\.1 General Rules

---

## 9\.2 Customer Creation Rules

---

## 9\.3 Profile Update Rules

---

## 9\.4 Customer Status Rules

---

## Functional Requirements

## 10\.1 Customer APIs

### FR\-USER\-001 Get My Profile

The system shall allow authenticated customer to get their own profile\.

Endpoint:

```Plain Text
GET /users/me
```

Expected behavior:

```Plain Text
read customerId from JWT subject or authUserId mapping
return customer profile
do not return internal sensitive data
```

---

### FR\-USER\-002 Update My Profile

The system shall allow authenticated customer to update their own profile\.

Endpoint:

```Plain Text
PUT /users/me
```

Allowed fields:

```Plain Text
fullName
phoneNumber
dateOfBirth
address
preferences
```

Expected behavior:

```Plain Text
validate input
update profile
recalculate profile status
create update history
insert outbox event customer.profile_updated.v1
return updated profile
```

---

### FR\-USER\-003 Get My Profile Completion Status

The system shall allow customer to check whether basic profile is completed\.

Endpoint:

```Plain Text
GET /users/me/profile-status
```

Expected response:

```Plain Text
customerId
profileStatus
missingFields
```

---

## 10\.2 Admin APIs

### FR\-USER\-004 List Customers

The system shall allow admin to list customers\.

Endpoint:

```Plain Text
GET /admin/users?status=ACTIVE&profileStatus=COMPLETED&page=0&size=20
```

Expected behavior:

```Plain Text
filter by status
filter by profile status
support pagination
support sort by createdAt
```

---

### FR\-USER\-005 Get Customer Detail

The system shall allow admin to get customer detail by customerId\.

Endpoint:

```Plain Text
GET /admin/users/{customerId}
```

Expected behavior:

```Plain Text
return profile detail
return status
return profile status
return createdAt and updatedAt
```

---

### FR\-USER\-006 Suspend Customer

The system shall allow admin to suspend a customer\.

Endpoint:

```Plain Text
POST /admin/users/{customerId}/suspend
```

Expected behavior:

```Plain Text
validate customer exists
validate current status = ACTIVE
require reason
change status to SUSPENDED
create status history
insert outbox event customer.suspended.v1
```

---

### FR\-USER\-007 Reactivate Customer

The system shall allow admin to reactivate a suspended customer\.

Endpoint:

```Plain Text
POST /admin/users/{customerId}/reactivate
```

Expected behavior:

```Plain Text
validate customer exists
validate current status = SUSPENDED
require reason
change status to ACTIVE
create status history
insert outbox event customer.reactivated.v1
```

---

### FR\-USER\-008 Close Customer

The system shall allow admin to close customer profile\.

Endpoint:

```Plain Text
POST /admin/users/{customerId}/close
```

Expected behavior:

```Plain Text
validate customer exists
validate customer is not already CLOSED
require reason
change status to CLOSED
create status history
insert outbox event customer.closed.v1
```

For v0\.1, this can be documented but not implemented in first iteration\.

---

## 10\.3 Internal APIs

### FR\-USER\-009 Get Customer By ID

Internal services may need basic customer profile\.

Endpoint:

```Plain Text
GET /internal/users/{customerId}
```

Expected response:

```Plain Text
customerId
authUserId
email
fullName
phoneNumber
status
profileStatus
customerType
```

Allowed consumers:

```Plain Text
kyc-service
wallet-service
payment-service
loan-service
risk-service
notification-service
```

---

### FR\-USER\-010 Check Customer Eligibility

Internal services may need to know whether customer can perform financial actions\.

Endpoint:

```Plain Text
GET /internal/users/{customerId}/eligibility
```

Expected response:

```Plain Text
customerId
eligible
status
profileStatus
reasons
```

Example:

```JSON
{
  "customerId": "uuid",
  "eligible": false,
  "status": "SUSPENDED",
  "profileStatus": "COMPLETED",
  "reasons": ["CUSTOMER_SUSPENDED"]
}
```

---

## Non\-Functional Requirements

## 11\.1 Security

---

## 11\.2 Reliability

---

## 11\.3 Auditability

---

## 11\.4 Maintainability



---

## API Design

## 12\.1 Customer Endpoints

```Plain Text
GET  /users/me
PUT  /users/me
GET  /users/me/profile-status
```

## 12\.2 Admin Endpoints

```Plain Text
GET  /admin/users
GET  /admin/users/{customerId}
POST /admin/users/{customerId}/suspend
POST /admin/users/{customerId}/reactivate
POST /admin/users/{customerId}/close
```

## 12\.3 Internal Endpoints

```Plain Text
GET /internal/users/{customerId}
GET /internal/users/{customerId}/eligibility
```

---

## Request / Response Examples

## 13\.1 Get My Profile

Endpoint:

```Plain Text
GET /users/me
```

Response:

```JSON
{
  "success": true,
  "data": {
    "customerId": "uuid",
    "authUserId": "uuid",
    "email": "customer@example.com",
    "phoneNumber": null,
    "fullName": null,
    "dateOfBirth": null,
    "customerType": "INDIVIDUAL",
    "status": "ACTIVE",
    "profileStatus": "INCOMPLETE",
    "createdAt": "2026-06-20T00:00:00Z",
    "updatedAt": "2026-06-20T00:00:00Z"
  },
  "error": null,
  "correlationId": "corr-id",
  "timestamp": "2026-06-20T00:00:00Z"
}
```

---

## 13\.2 Update My Profile

Endpoint:

```Plain Text
PUT /users/me
```

Request:

```JSON
{
  "fullName": "Nguyen Van A",
  "phoneNumber": "+84901234567",
  "dateOfBirth": "1999-05-20",
  "address": {
    "line1": "123 Nguyen Trai",
    "line2": "Thanh Xuan",
    "city": "Hanoi",
    "country": "VN"
  },
  "preferences": {
    "language": "vi",
    "notificationEnabled": true
  }
}
```

Response:

```JSON
{
  "success": true,
  "data": {
    "customerId": "uuid",
    "email": "customer@example.com",
    "phoneNumber": "+84901234567",
    "fullName": "Nguyen Van A",
    "dateOfBirth": "1999-05-20",
    "customerType": "INDIVIDUAL",
    "status": "ACTIVE",
    "profileStatus": "COMPLETED"
  },
  "error": null,
  "correlationId": "corr-id",
  "timestamp": "2026-06-20T00:00:00Z"
}
```

---

## 13\.3 Suspend Customer

Endpoint:

```Plain Text
POST /admin/users/{customerId}/suspend
```

Request:

```JSON
{
  "reason": "Suspicious activity detected"
}
```

Response:

```JSON
{
  "success": true,
  "data": {
    "customerId": "uuid",
    "status": "SUSPENDED",
    "reason": "Suspicious activity detected"
  },
  "error": null,
  "correlationId": "corr-id",
  "timestamp": "2026-06-20T00:00:00Z"
}
```

---

## 13\.4 Check Eligibility

Endpoint:

```Plain Text
GET /internal/users/{customerId}/eligibility
```

Response:

```JSON
{
  "success": true,
  "data": {
    "customerId": "uuid",
    "eligible": true,
    "status": "ACTIVE",
    "profileStatus": "COMPLETED",
    "reasons": []
  },
  "error": null,
  "correlationId": "corr-id",
  "timestamp": "2026-06-20T00:00:00Z"
}
```

---

## Data Model

## 14\.1 customers

Stores main customer profile\.

```SQL
CREATE TABLE customers (
    id UUID PRIMARY KEY,
    auth_user_id UUID NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL,
    phone_number VARCHAR(50),
    full_name VARCHAR(255),
    date_of_birth DATE,
    customer_type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    profile_status VARCHAR(30) NOT NULL,

    address_line1 VARCHAR(255),
    address_line2 VARCHAR(255),
    city VARCHAR(100),
    country VARCHAR(10),

    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uq_customers_auth_user_id
ON customers(auth_user_id);

CREATE INDEX idx_customers_email
ON customers(email);

CREATE INDEX idx_customers_status
ON customers(status);

CREATE INDEX idx_customers_profile_status
ON customers(profile_status);
```

---

## 14\.2 customer\_status\_history

Stores status changes\.

```SQL
CREATE TABLE customer_status_history (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    from_status VARCHAR(30),
    to_status VARCHAR(30) NOT NULL,
    reason TEXT,
    changed_by UUID,
    correlation_id VARCHAR(100),
    changed_at TIMESTAMP NOT NULL,

    CONSTRAINT fk_customer_status_history_customer
        FOREIGN KEY (customer_id)
        REFERENCES customers(id)
);

CREATE INDEX idx_customer_status_history_customer_id
ON customer_status_history(customer_id);
```

---

## 14\.3 customer\_profile\_history

Stores important profile updates\.

```SQL
CREATE TABLE customer_profile_history (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    changed_fields JSONB NOT NULL,
    changed_by UUID,
    correlation_id VARCHAR(100),
    changed_at TIMESTAMP NOT NULL,

    CONSTRAINT fk_customer_profile_history_customer
        FOREIGN KEY (customer_id)
        REFERENCES customers(id)
);

CREATE INDEX idx_customer_profile_history_customer_id
ON customer_profile_history(customer_id);
```

---

## 14\.4 outbox\_events

Required for reliable event publishing\.

```SQL
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(150) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(30) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    max_retry_count INT NOT NULL DEFAULT 10,
    next_retry_at TIMESTAMP NULL,
    published_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    correlation_id VARCHAR(100),
    causation_id VARCHAR(100),
    error_message TEXT
);

CREATE INDEX idx_outbox_status_next_retry
ON outbox_events(status, next_retry_at);

CREATE INDEX idx_outbox_aggregate
ON outbox_events(aggregate_type, aggregate_id);

CREATE INDEX idx_outbox_event_type
ON outbox_events(event_type);
```

---

## Domain Events

## 15\.1 customer\.created\.v1

Published when customer profile is created\.

Publisher:

```Plain Text
fintech-user-service
```

Consumers:

```Plain Text
kyc-service
notification-service
audit-service
report-service
```

Payload:

```JSON
{
  "customerId": "uuid",
  "authUserId": "uuid",
  "email": "customer@example.com",
  "customerType": "INDIVIDUAL",
  "status": "ACTIVE",
  "profileStatus": "INCOMPLETE",
  "createdAt": "2026-06-20T00:00:00Z"
}
```

---

## 15\.2 customer\.profile\_updated\.v1

Published when customer updates important profile data\.

Publisher:

```Plain Text
fintech-user-service
```

Consumers:

```Plain Text
kyc-service
notification-service
audit-service
report-service
risk-service
```

Payload:

```JSON
{
  "customerId": "uuid",
  "authUserId": "uuid",
  "changedFields": [
    "fullName",
    "phoneNumber",
    "dateOfBirth",
    "address"
  ],
  "profileStatus": "COMPLETED",
  "updatedAt": "2026-06-20T00:00:00Z"
}
```

---

## 15\.3 customer\.suspended\.v1

Published when customer is suspended\.

Publisher:

```Plain Text
fintech-user-service
```

Consumers:

```Plain Text
wallet-service
payment-service
loan-service
risk-service
notification-service
audit-service
report-service
```

Payload:

```JSON
{
  "customerId": "uuid",
  "fromStatus": "ACTIVE",
  "toStatus": "SUSPENDED",
  "reason": "Suspicious activity detected",
  "changedBy": "admin-user-id",
  "changedAt": "2026-06-20T00:00:00Z"
}
```

Business impact:

```Plain Text
Customer should not initiate new wallet transfers.
Customer should not initiate new payments.
Customer should not apply for new loans.
```

---

## 15\.4 customer\.reactivated\.v1

Published when suspended customer is reactivated\.

Payload:

```JSON
{
  "customerId": "uuid",
  "fromStatus": "SUSPENDED",
  "toStatus": "ACTIVE",
  "reason": "Manual review completed",
  "changedBy": "admin-user-id",
  "changedAt": "2026-06-20T00:00:00Z"
}
```

---

## 15\.5 customer\.closed\.v1

Published when customer is closed\.

Payload:

```JSON
{
  "customerId": "uuid",
  "fromStatus": "ACTIVE",
  "toStatus": "CLOSED",
  "reason": "Customer requested account closure",
  "changedBy": "admin-user-id",
  "changedAt": "2026-06-20T00:00:00Z"
}
```

---

## Event Envelope

All events should follow the platform event envelope\.

```JSON
{
  "eventId": "uuid",
  "eventType": "customer.created.v1",
  "aggregateType": "Customer",
  "aggregateId": "customer-id",
  "occurredAt": "2026-06-20T00:00:00Z",
  "producer": "fintech-user-service",
  "correlationId": "corr-id",
  "causationId": "command-id",
  "payload": {}
}
```

---

## Error Codes



---

## Main Workflows

## 18\.1 Customer Profile Creation From Auth Event

```Plain Text
auth-service registers user
→ auth-service publishes auth.user_registered.v1
→ user-service consumes event
→ user-service checks if authUserId already exists
→ if not exists, create customer profile
→ status = ACTIVE
→ profileStatus = INCOMPLETE
→ insert outbox event customer.created.v1
→ commit transaction
```

For v0\.1, if event consumer is not ready, `auth-service` can call `user-service` synchronously after registration or profile can be manually created through an internal endpoint\.

Recommended final approach:

```Plain Text
auth.user_registered.v1 → user-service creates customer
```

---

## 18\.2 Customer Updates Own Profile

```Plain Text
customer calls PUT /users/me
→ service identifies authUserId from JWT
→ find customer by authUserId
→ validate update request
→ update profile fields
→ calculate profileStatus
→ insert customer_profile_history
→ insert outbox event customer.profile_updated.v1
→ return updated profile
```

---

## 18\.3 Admin Suspends Customer

```Plain Text
admin calls POST /admin/users/{customerId}/suspend
→ service validates admin permission
→ find customer
→ validate current status = ACTIVE
→ change status to SUSPENDED
→ insert customer_status_history
→ insert outbox event customer.suspended.v1
→ payment/loan/wallet services consume event later
```

---

## 18\.4 Internal Service Checks Customer Eligibility

```Plain Text
payment-service calls GET /internal/users/{customerId}/eligibility
→ user-service checks customer status
→ user-service checks profile status
→ return eligible true/false and reasons
```

Example rules:

```Plain Text
eligible = true only when:
- status = ACTIVE
- profileStatus = COMPLETED
```

KYC approval is not owned by user\-service\. Payment or wallet eligibility should combine:

```Plain Text
customer status from user-service
KYC status from kyc-service
wallet status from wallet-service
```

---

## Package Structure

Recommended structure:

```Plain Text
fintech-user-service/
├── src/main/java/com/yourorg/fintech/user/
│   ├── UserServiceApplication.java
│   │
│   ├── domain/
│   │   ├── model/
│   │   │   ├── Customer.java
│   │   │   ├── CustomerStatus.java
│   │   │   ├── CustomerType.java
│   │   │   ├── ProfileStatus.java
│   │   │   └── Address.java
│   │   │
│   │   ├── event/
│   │   │   ├── CustomerCreatedEvent.java
│   │   │   ├── CustomerProfileUpdatedEvent.java
│   │   │   ├── CustomerSuspendedEvent.java
│   │   │   ├── CustomerReactivatedEvent.java
│   │   │   └── CustomerClosedEvent.java
│   │   │
│   │   └── error/
│   │       └── UserErrorCode.java
│   │
│   ├── application/
│   │   ├── command/
│   │   │   ├── CreateCustomerCommand.java
│   │   │   ├── UpdateMyProfileCommand.java
│   │   │   ├── SuspendCustomerCommand.java
│   │   │   ├── ReactivateCustomerCommand.java
│   │   │   └── CloseCustomerCommand.java
│   │   │
│   │   ├── service/
│   │   │   ├── CustomerApplicationService.java
│   │   │   ├── CustomerStatusService.java
│   │   │   └── ProfileCompletionService.java
│   │   │
│   │   └── result/
│   │       ├── CustomerProfileResult.java
│   │       ├── ProfileStatusResult.java
│   │       └── CustomerEligibilityResult.java
│   │
│   ├── infrastructure/
│   │   ├── persistence/
│   │   │   ├── entity/
│   │   │   ├── repository/
│   │   │   └── mapper/
│   │   │
│   │   ├── outbox/
│   │   └── kafka/
│   │
│   └── interfaces/
│       └── rest/
│           ├── UserCustomerController.java
│           ├── UserAdminController.java
│           ├── UserInternalController.java
│           ├── request/
│           └── response/
```

---

## Feature Breakdown

## Feature 1 — Project Bootstrap

Goal:

```Plain Text
Create fintech-user-service repository and make it run.
```

Tasks:

```Plain Text
[ ] Create Spring Boot project
[ ] Add dependencies: Web, Security, JPA, PostgreSQL, Flyway, Validation, Actuator
[ ] Add fintech-common-java dependency
[ ] Configure application.yml
[ ] Connect to user_db
[ ] Add health check
[ ] Run service on port 8082
```

Acceptance Criteria:

```Plain Text
[ ] GET /actuator/health returns UP
[ ] Service connects to user_db
[ ] Flyway runs successfully
```

---

## Feature 2 — Database Migration

Goal:

```Plain Text
Create customer-related tables.
```

Tasks:

```Plain Text
[ ] Create customers table
[ ] Create customer_status_history table
[ ] Create customer_profile_history table
[ ] Create outbox_events table
[ ] Add indexes
```

Acceptance Criteria:

```Plain Text
[ ] Tables are created in user_db
[ ] Duplicate authUserId is prevented
[ ] Customer status and profile status are indexed
```

---

## Feature 3 — Create Customer Profile

Goal:

```Plain Text
Create customer profile after auth registration.
```

Tasks:

```Plain Text
[ ] Implement CreateCustomerCommand
[ ] Implement CustomerApplicationService.createCustomer
[ ] Validate authUserId uniqueness
[ ] Set default status ACTIVE
[ ] Set default customerType INDIVIDUAL
[ ] Set default profileStatus INCOMPLETE
[ ] Insert outbox event customer.created.v1
```

Acceptance Criteria:

```Plain Text
[ ] Customer is created with authUserId and email
[ ] Duplicate authUserId returns USER_ALREADY_EXISTS
[ ] customer.created.v1 is inserted into outbox
```

---

## Feature 4 — Get My Profile

Goal:

```Plain Text
Customer can get their own profile.
```

Tasks:

```Plain Text
[ ] Extract authUserId from JWT
[ ] Find customer by authUserId
[ ] Return customer profile response
[ ] Return USER_NOT_FOUND if missing
```

Acceptance Criteria:

```Plain Text
[ ] GET /users/me returns current customer profile
[ ] Customer cannot get another customer's profile
```

---

## Feature 5 — Update My Profile

Goal:

```Plain Text
Customer can update basic profile.
```

Tasks:

```Plain Text
[ ] Implement UpdateMyProfileRequest
[ ] Validate fullName
[ ] Validate phoneNumber
[ ] Validate dateOfBirth
[ ] Update address
[ ] Recalculate profileStatus
[ ] Insert customer_profile_history
[ ] Insert customer.profile_updated.v1 outbox event
```

Acceptance Criteria:

```Plain Text
[ ] PUT /users/me updates profile
[ ] Required fields completed -> profileStatus COMPLETED
[ ] Invalid phone returns USER_INVALID_PHONE_NUMBER
[ ] Invalid date of birth returns USER_INVALID_DATE_OF_BIRTH
[ ] Profile update event is created
```

---

## Feature 6 — Profile Completion Status

Goal:

```Plain Text
Customer can see what profile fields are missing.
```

Tasks:

```Plain Text
[ ] Implement ProfileCompletionService
[ ] Return profileStatus
[ ] Return missingFields list
```

Acceptance Criteria:

```Plain Text
[ ] GET /users/me/profile-status returns COMPLETED when fullName, phoneNumber, dateOfBirth exist
[ ] Returns INCOMPLETE with missingFields when data is missing
```

---

## Feature 7 — Admin List And Detail

Goal:

```Plain Text
Admin can search and view customer profiles.
```

Tasks:

```Plain Text
[ ] Implement GET /admin/users
[ ] Support status filter
[ ] Support profileStatus filter
[ ] Support pagination
[ ] Implement GET /admin/users/{customerId}
```

Acceptance Criteria:

```Plain Text
[ ] Admin can list customers
[ ] Admin can filter by ACTIVE/SUSPENDED/CLOSED
[ ] Admin can view customer detail
```

---

## Feature 8 — Suspend Customer

Goal:

```Plain Text
Admin can suspend active customer.
```

Tasks:

```Plain Text
[ ] Implement SuspendCustomerCommand
[ ] Validate reason
[ ] Validate current status ACTIVE
[ ] Change status to SUSPENDED
[ ] Insert customer_status_history
[ ] Insert customer.suspended.v1 outbox event
```

Acceptance Criteria:

```Plain Text
[ ] ACTIVE customer can be suspended
[ ] Missing reason returns USER_STATUS_REASON_REQUIRED
[ ] Suspending non-ACTIVE customer returns USER_INVALID_STATUS_TRANSITION
[ ] customer.suspended.v1 is created
```

---

## Feature 9 — Reactivate Customer

Goal:

```Plain Text
Admin can reactivate suspended customer.
```

Tasks:

```Plain Text
[ ] Implement ReactivateCustomerCommand
[ ] Validate reason
[ ] Validate current status SUSPENDED
[ ] Change status to ACTIVE
[ ] Insert customer_status_history
[ ] Insert customer.reactivated.v1 outbox event
```

Acceptance Criteria:

```Plain Text
[ ] SUSPENDED customer can be reactivated
[ ] ACTIVE customer cannot be reactivated again
[ ] customer.reactivated.v1 is created
```

---

## Feature 10 — Internal Customer Lookup

Goal:

```Plain Text
Other services can query customer basic info.
```

Tasks:

```Plain Text
[ ] Implement GET /internal/users/{customerId}
[ ] Return customer basic info
[ ] Do not expose profile history
[ ] Do not expose admin-only metadata
```

Acceptance Criteria:

```Plain Text
[ ] Internal endpoint returns basic customer profile
[ ] Unknown customer returns USER_NOT_FOUND
```

---

## Feature 11 — Internal Eligibility Check

Goal:

```Plain Text
Other services can check if customer is basically eligible for financial actions.
```

Tasks:

```Plain Text
[ ] Implement GET /internal/users/{customerId}/eligibility
[ ] Check customer status
[ ] Check profile status
[ ] Return eligible and reasons
```

Acceptance Criteria:

```Plain Text
[ ] ACTIVE + COMPLETED -> eligible true
[ ] SUSPENDED -> eligible false with CUSTOMER_SUSPENDED
[ ] INCOMPLETE -> eligible false with PROFILE_INCOMPLETE
[ ] CLOSED -> eligible false with CUSTOMER_CLOSED
```

---

## Feature 12 — Outbox Publisher

Goal:

```Plain Text
Publish customer events reliably.
```

Tasks:

```Plain Text
[ ] Implement outbox event entity
[ ] Implement outbox scheduler
[ ] Publish customer events to Kafka
[ ] Mark event as PUBLISHED
[ ] Retry failed events
```

Acceptance Criteria:

```Plain Text
[ ] customer.created.v1 can be published
[ ] customer.profile_updated.v1 can be published
[ ] customer.suspended.v1 can be published
[ ] Failed publish is retried
```

---

## Recommended MVP Scope

For Day 11, do not implement everything\.

Recommended MVP:

```Plain Text
Feature 1 — Project Bootstrap
Feature 2 — Database Migration
Feature 3 — Create Customer Profile
Feature 4 — Get My Profile
Feature 5 — Update My Profile
Feature 6 — Profile Completion Status
Feature 10 — Internal Customer Lookup
```

Admin suspension/reactivation and outbox publisher can be done after basic customer profile is stable\.

---

## Implementation Order

Recommended order:

```Plain Text
1. Bootstrap project
2. Configure DB and Flyway
3. Create customers table
4. Implement entity/repository
5. Implement create customer profile
6. Implement get my profile
7. Implement update my profile
8. Implement profile completion status
9. Implement internal customer lookup
10. Add admin list/detail
11. Add suspend/reactivate
12. Add outbox events
13. Add Kafka publisher
```

---

## Test Scenarios

## 23\.1 Create Customer Happy Path

```Plain Text
create customer with authUserId and email
expect status ACTIVE
expect customerType INDIVIDUAL
expect profileStatus INCOMPLETE
```

---

## 23\.2 Duplicate authUserId

```Plain Text
create customer once
create customer again with same authUserId
expect USER_ALREADY_EXISTS
```

---

## 23\.3 Update Profile To Completed

```Plain Text
customer updates fullName, phoneNumber, dateOfBirth
expect profileStatus COMPLETED
```

---

## 23\.4 Missing Profile Fields

```Plain Text
customer only has email
call GET /users/me/profile-status
expect INCOMPLETE
expect missingFields contains fullName, phoneNumber, dateOfBirth
```

---

## 23\.5 Suspend Customer

```Plain Text
admin suspends ACTIVE customer
expect status SUSPENDED
expect status history created
expect customer.suspended.v1 outbox event
```

---

## 23\.6 Eligibility Check

```Plain Text
customer ACTIVE + COMPLETED
expect eligible true

customer SUSPENDED
expect eligible false

customer INCOMPLETE
expect eligible false
```

---

## Demo Script

```Plain Text
1. Register user in auth-service.
2. Create customer profile in user-service.
3. Call GET /users/me.
4. See profileStatus = INCOMPLETE.
5. Update fullName, phoneNumber, dateOfBirth.
6. Call GET /users/me/profile-status.
7. See profileStatus = COMPLETED.
8. Call internal eligibility endpoint.
9. See eligible = true.
10. Admin suspends customer.
11. Call eligibility again.
12. See eligible = false with reason CUSTOMER_SUSPENDED.
```

---

## Summary

`fintech-user-service` is the customer profile and lifecycle service\.

Core principle:

```Plain Text
auth-service owns identity credentials.
user-service owns customer profile.
kyc-service owns identity verification.
wallet-service owns wallet.
ledger-service owns financial records.
```

The most important rule is:

```Plain Text
Do not mix login credentials with customer profile domain.
```

The first version should focus on:

```Plain Text
customer creation
profile update
profile completion
customer lookup
customer eligibility
```



