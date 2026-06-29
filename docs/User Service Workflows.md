# User Service Workflows

This document describes the implemented workflows in `fintech-user-service`.

The diagrams reflect the current codebase:

- Customer APIs read the current user from `X-Auth-User-Id`.
- Internal customer creation is exposed as `POST /internal/users`.
- Events are written to `outbox_events`; a Kafka outbox publisher is not implemented yet.
- Profile completion is calculated from `fullName`, `phoneNumber`, and `dateOfBirth`.

## Component Overview

```mermaid
flowchart LR
    Customer[Customer Client]
    Admin[Admin Client]
    Internal[Internal Service]

    CustomerController[UserCustomerController]
    AdminController[UserAdminController]
    InternalController[UserInternalController]
    ExceptionHandler[RestExceptionHandler]

    CustomerService[CustomerApplicationService]
    ProfileService[ProfileCompletionService]

    CustomerRepo[CustomerRepository]
    ProfileHistoryRepo[CustomerProfileHistoryRepository]
    StatusHistoryRepo[CustomerStatusHistoryRepository]
    OutboxRepo[OutboxEventRepository]

    DB[(PostgreSQL)]

    Customer --> CustomerController
    Admin --> AdminController
    Internal --> InternalController

    CustomerController --> CustomerService
    AdminController --> CustomerService
    InternalController --> CustomerService

    CustomerService --> ProfileService
    CustomerService --> CustomerRepo
    CustomerService --> ProfileHistoryRepo
    CustomerService --> StatusHistoryRepo
    CustomerService --> OutboxRepo

    CustomerRepo --> DB
    ProfileHistoryRepo --> DB
    StatusHistoryRepo --> DB
    OutboxRepo --> DB

    CustomerService -. throws UserServiceException .-> ExceptionHandler
```

## Customer Creation Workflow

Implemented endpoint:

```text
POST /internal/users
```

```mermaid
flowchart TD
    Start([Internal service requests customer creation])
    ValidateInput{authUserId and email valid?}
    DuplicateCheck{authUserId already exists?}
    BuildCustomer[Build CustomerEntity]
    Defaults[Set status ACTIVE, type INDIVIDUAL by default, profileStatus INCOMPLETE]
    SaveCustomer[(Save customers row)]
    WriteOutbox[(Insert customer.created.v1 outbox event)]
    ReturnProfile[Return CustomerProfileResult]
    Invalid[Return USER_INVALID_REQUEST]
    Duplicate[Return USER_ALREADY_EXISTS]

    Start --> ValidateInput
    ValidateInput -- No --> Invalid
    ValidateInput -- Yes --> DuplicateCheck
    DuplicateCheck -- Yes --> Duplicate
    DuplicateCheck -- No --> BuildCustomer
    BuildCustomer --> Defaults
    Defaults --> SaveCustomer
    SaveCustomer --> WriteOutbox
    WriteOutbox --> ReturnProfile
```

## Customer Creation Sequence

```mermaid
sequenceDiagram
    autonumber
    participant Internal as Internal Service
    participant Controller as UserInternalController
    participant App as CustomerApplicationService
    participant CustomerRepo as CustomerRepository
    participant OutboxRepo as OutboxEventRepository
    participant DB as PostgreSQL

    Internal->>Controller: POST /internal/users
    Controller->>App: createCustomer(CreateCustomerCommand)
    App->>App: validate authUserId and email
    App->>CustomerRepo: existsByAuthUserId(authUserId)
    CustomerRepo->>DB: select exists
    DB-->>CustomerRepo: exists=false
    CustomerRepo-->>App: false
    App->>App: set defaults
    App->>CustomerRepo: save(CustomerEntity)
    CustomerRepo->>DB: insert customers
    DB-->>CustomerRepo: saved customer
    CustomerRepo-->>App: CustomerEntity
    App->>OutboxRepo: save(customer.created.v1)
    OutboxRepo->>DB: insert outbox_events
    DB-->>OutboxRepo: saved event
    App-->>Controller: CustomerProfileResult
    Controller-->>Internal: ApiResponse success
```

## Get My Profile Workflow

Implemented endpoint:

```text
GET /users/me
```

```mermaid
flowchart TD
    Start([Customer requests own profile])
    ReadHeader[Read X-Auth-User-Id]
    ParseUuid{Valid UUID?}
    FindCustomer{Customer found by authUserId?}
    MapResult[Map CustomerEntity to CustomerProfileResult]
    ReturnProfile[Return profile]
    Invalid[Return USER_INVALID_REQUEST]
    NotFound[Return USER_NOT_FOUND]

    Start --> ReadHeader
    ReadHeader --> ParseUuid
    ParseUuid -- No --> Invalid
    ParseUuid -- Yes --> FindCustomer
    FindCustomer -- No --> NotFound
    FindCustomer -- Yes --> MapResult
    MapResult --> ReturnProfile
```

## Get My Profile Sequence

```mermaid
sequenceDiagram
    autonumber
    participant Customer
    participant Controller as UserCustomerController
    participant App as CustomerApplicationService
    participant CustomerRepo as CustomerRepository
    participant ProfileService as ProfileCompletionService
    participant DB as PostgreSQL

    Customer->>Controller: GET /users/me with X-Auth-User-Id
    Controller->>Controller: parse authUserId
    Controller->>App: getMyProfile(authUserId)
    App->>CustomerRepo: findByAuthUserId(authUserId)
    CustomerRepo->>DB: select customers by auth_user_id
    DB-->>CustomerRepo: customer row
    CustomerRepo-->>App: CustomerEntity
    App->>ProfileService: calculate(customer)
    ProfileService-->>App: profileStatus
    App-->>Controller: CustomerProfileResult
    Controller-->>Customer: ApiResponse success
```

## Update My Profile Workflow

Implemented endpoint:

```text
PUT /users/me
```

```mermaid
flowchart TD
    Start([Customer submits profile update])
    ReadHeader[Read X-Auth-User-Id]
    FindCustomer{Customer found?}
    ValidatePhone{phoneNumber null or E.164?}
    ValidateDob{dateOfBirth null or in past?}
    ApplyChanges[Apply non-null changed fields]
    Recalculate[Recalculate profile status]
    SaveCustomer[(Save customers row)]
    Changed{Any changed fields?}
    SaveHistory[(Insert customer_profile_history)]
    SaveOutbox[(Insert customer.profile_updated.v1 outbox event)]
    ReturnProfile[Return updated profile]
    NotFound[Return USER_NOT_FOUND]
    InvalidPhone[Return USER_INVALID_PHONE_NUMBER]
    InvalidDob[Return USER_INVALID_DATE_OF_BIRTH]

    Start --> ReadHeader
    ReadHeader --> FindCustomer
    FindCustomer -- No --> NotFound
    FindCustomer -- Yes --> ValidatePhone
    ValidatePhone -- No --> InvalidPhone
    ValidatePhone -- Yes --> ValidateDob
    ValidateDob -- No --> InvalidDob
    ValidateDob -- Yes --> ApplyChanges
    ApplyChanges --> Recalculate
    Recalculate --> SaveCustomer
    SaveCustomer --> Changed
    Changed -- No --> ReturnProfile
    Changed -- Yes --> SaveHistory
    SaveHistory --> SaveOutbox
    SaveOutbox --> ReturnProfile
```

## Update My Profile Sequence

```mermaid
sequenceDiagram
    autonumber
    participant Customer
    participant Controller as UserCustomerController
    participant App as CustomerApplicationService
    participant ProfileService as ProfileCompletionService
    participant CustomerRepo as CustomerRepository
    participant ProfileHistoryRepo as CustomerProfileHistoryRepository
    participant OutboxRepo as OutboxEventRepository
    participant DB as PostgreSQL

    Customer->>Controller: PUT /users/me
    Controller->>Controller: parse X-Auth-User-Id
    Controller->>App: updateMyProfile(UpdateMyProfileCommand)
    App->>CustomerRepo: findByAuthUserId(authUserId)
    CustomerRepo->>DB: select customers by auth_user_id
    DB-->>CustomerRepo: customer row
    CustomerRepo-->>App: CustomerEntity
    App->>App: validate phone and dateOfBirth
    App->>App: apply changed fields
    App->>ProfileService: calculate(customer)
    ProfileService-->>App: COMPLETED or INCOMPLETE
    App->>CustomerRepo: save(customer)
    CustomerRepo->>DB: update customers
    DB-->>CustomerRepo: saved customer

    alt changedFields is not empty
        App->>ProfileHistoryRepo: save(changedFields)
        ProfileHistoryRepo->>DB: insert customer_profile_history
        App->>OutboxRepo: save(customer.profile_updated.v1)
        OutboxRepo->>DB: insert outbox_events
    else no changed fields
        App->>App: skip history and outbox
    end

    App-->>Controller: CustomerProfileResult
    Controller-->>Customer: ApiResponse success
```

## Profile Completion Workflow

Implemented endpoint:

```text
GET /users/me/profile-status
```

```mermaid
flowchart TD
    Start([Customer requests profile status])
    FindCustomer[Find customer by authUserId]
    CheckFullName{fullName present?}
    CheckPhone{phoneNumber present?}
    CheckDob{dateOfBirth present?}
    Missing[Build missingFields]
    Complete{missingFields empty?}
    Completed[profileStatus COMPLETED]
    Incomplete[profileStatus INCOMPLETE]
    ReturnResult[Return ProfileStatusResult]

    Start --> FindCustomer
    FindCustomer --> CheckFullName
    CheckFullName --> CheckPhone
    CheckPhone --> CheckDob
    CheckDob --> Missing
    Missing --> Complete
    Complete -- Yes --> Completed
    Complete -- No --> Incomplete
    Completed --> ReturnResult
    Incomplete --> ReturnResult
```

## Admin List And Detail Workflow

Implemented endpoints:

```text
GET /admin/users
GET /admin/users/{customerId}
```

```mermaid
flowchart TD
    Start([Admin requests customers])
    Kind{List or detail?}
    List[Read status/profileStatus/page/size]
    BuildPage[Sort by createdAt desc]
    Search[(Search customers)]
    Detail[Read customerId]
    FindById{Customer found?}
    ReturnList[Return paged customer profiles]
    ReturnDetail[Return customer profile]
    NotFound[Return USER_NOT_FOUND]

    Start --> Kind
    Kind -- List --> List
    List --> BuildPage
    BuildPage --> Search
    Search --> ReturnList
    Kind -- Detail --> Detail
    Detail --> FindById
    FindById -- No --> NotFound
    FindById -- Yes --> ReturnDetail
```

## Admin Status Change Workflow

Implemented endpoints:

```text
POST /admin/users/{customerId}/suspend
POST /admin/users/{customerId}/reactivate
POST /admin/users/{customerId}/close
```

```mermaid
flowchart TD
    Start([Admin requests status change])
    Operation{Operation}
    FindCustomer{Customer found?}
    Reason{Reason present?}
    SuspendRule{Current status ACTIVE?}
    ReactivateRule{Current status SUSPENDED?}
    CloseRule{Current status not CLOSED?}
    SetStatus[Set target status]
    SaveCustomer[(Save customers row)]
    SaveHistory[(Insert customer_status_history)]
    SaveOutbox[(Insert status outbox event)]
    ReturnStatus[Return status change result]
    NotFound[Return USER_NOT_FOUND]
    MissingReason[Return USER_STATUS_REASON_REQUIRED]
    InvalidTransition[Return USER_INVALID_STATUS_TRANSITION]

    Start --> Operation
    Operation --> FindCustomer
    FindCustomer -- No --> NotFound
    FindCustomer -- Yes --> Reason
    Reason -- No --> MissingReason
    Reason -- Yes --> SuspendRule

    SuspendRule -- Suspend and ACTIVE --> SetStatus
    SuspendRule -- Suspend and not ACTIVE --> InvalidTransition
    SuspendRule -- Reactivate --> ReactivateRule
    ReactivateRule -- SUSPENDED --> SetStatus
    ReactivateRule -- not SUSPENDED --> InvalidTransition
    SuspendRule -- Close --> CloseRule
    CloseRule -- not CLOSED --> SetStatus
    CloseRule -- CLOSED --> InvalidTransition

    SetStatus --> SaveCustomer
    SaveCustomer --> SaveHistory
    SaveHistory --> SaveOutbox
    SaveOutbox --> ReturnStatus
```

## Suspend Customer Sequence

```mermaid
sequenceDiagram
    autonumber
    participant Admin
    participant Controller as UserAdminController
    participant App as CustomerApplicationService
    participant CustomerRepo as CustomerRepository
    participant StatusHistoryRepo as CustomerStatusHistoryRepository
    participant OutboxRepo as OutboxEventRepository
    participant DB as PostgreSQL

    Admin->>Controller: POST /admin/users/{customerId}/suspend
    Controller->>App: suspendCustomer(ChangeCustomerStatusCommand)
    App->>CustomerRepo: findById(customerId)
    CustomerRepo->>DB: select customers by id
    DB-->>CustomerRepo: ACTIVE customer row
    CustomerRepo-->>App: CustomerEntity
    App->>App: require reason
    App->>App: validate current status is ACTIVE
    App->>CustomerRepo: save(status=SUSPENDED)
    CustomerRepo->>DB: update customers
    App->>StatusHistoryRepo: save(ACTIVE -> SUSPENDED)
    StatusHistoryRepo->>DB: insert customer_status_history
    App->>OutboxRepo: save(customer.suspended.v1)
    OutboxRepo->>DB: insert outbox_events
    App-->>Controller: CustomerStatusChangeResult
    Controller-->>Admin: ApiResponse success
```

## Reactivate Customer Sequence

```mermaid
sequenceDiagram
    autonumber
    participant Admin
    participant Controller as UserAdminController
    participant App as CustomerApplicationService
    participant CustomerRepo as CustomerRepository
    participant StatusHistoryRepo as CustomerStatusHistoryRepository
    participant OutboxRepo as OutboxEventRepository
    participant DB as PostgreSQL

    Admin->>Controller: POST /admin/users/{customerId}/reactivate
    Controller->>App: reactivateCustomer(ChangeCustomerStatusCommand)
    App->>CustomerRepo: findById(customerId)
    CustomerRepo->>DB: select customers by id
    DB-->>CustomerRepo: SUSPENDED customer row
    CustomerRepo-->>App: CustomerEntity
    App->>App: require reason
    App->>App: validate current status is SUSPENDED
    App->>CustomerRepo: save(status=ACTIVE)
    CustomerRepo->>DB: update customers
    App->>StatusHistoryRepo: save(SUSPENDED -> ACTIVE)
    StatusHistoryRepo->>DB: insert customer_status_history
    App->>OutboxRepo: save(customer.reactivated.v1)
    OutboxRepo->>DB: insert outbox_events
    App-->>Controller: CustomerStatusChangeResult
    Controller-->>Admin: ApiResponse success
```

## Close Customer Sequence

```mermaid
sequenceDiagram
    autonumber
    participant Admin
    participant Controller as UserAdminController
    participant App as CustomerApplicationService
    participant CustomerRepo as CustomerRepository
    participant StatusHistoryRepo as CustomerStatusHistoryRepository
    participant OutboxRepo as OutboxEventRepository
    participant DB as PostgreSQL

    Admin->>Controller: POST /admin/users/{customerId}/close
    Controller->>App: closeCustomer(ChangeCustomerStatusCommand)
    App->>CustomerRepo: findById(customerId)
    CustomerRepo->>DB: select customers by id
    DB-->>CustomerRepo: ACTIVE or SUSPENDED customer row
    CustomerRepo-->>App: CustomerEntity
    App->>App: require reason
    App->>App: validate current status is not CLOSED
    App->>CustomerRepo: save(status=CLOSED)
    CustomerRepo->>DB: update customers
    App->>StatusHistoryRepo: save(previous -> CLOSED)
    StatusHistoryRepo->>DB: insert customer_status_history
    App->>OutboxRepo: save(customer.closed.v1)
    OutboxRepo->>DB: insert outbox_events
    App-->>Controller: CustomerStatusChangeResult
    Controller-->>Admin: ApiResponse success
```

## Internal Customer Lookup Workflow

Implemented endpoint:

```text
GET /internal/users/{customerId}
```

```mermaid
flowchart TD
    Start([Internal service requests customer profile])
    FindCustomer{Customer found by customerId?}
    MapResult[Map CustomerEntity to CustomerProfileResult]
    ReturnProfile[Return basic profile]
    NotFound[Return USER_NOT_FOUND]

    Start --> FindCustomer
    FindCustomer -- No --> NotFound
    FindCustomer -- Yes --> MapResult
    MapResult --> ReturnProfile
```

## Eligibility Workflow

Implemented endpoint:

```text
GET /internal/users/{customerId}/eligibility
```

```mermaid
flowchart TD
    Start([Internal service checks eligibility])
    FindCustomer{Customer found?}
    InitReasons[Start with empty reasons]
    Suspended{status SUSPENDED?}
    Closed{status CLOSED?}
    ProfileComplete{profileStatus COMPLETED?}
    AddSuspended[Add CUSTOMER_SUSPENDED]
    AddClosed[Add CUSTOMER_CLOSED]
    AddIncomplete[Add PROFILE_INCOMPLETE]
    Eligible{reasons empty?}
    ReturnTrue[Return eligible true]
    ReturnFalse[Return eligible false with reasons]
    NotFound[Return USER_NOT_FOUND]

    Start --> FindCustomer
    FindCustomer -- No --> NotFound
    FindCustomer -- Yes --> InitReasons
    InitReasons --> Suspended
    Suspended -- Yes --> AddSuspended
    Suspended -- No --> Closed
    AddSuspended --> Closed
    Closed -- Yes --> AddClosed
    Closed -- No --> ProfileComplete
    AddClosed --> ProfileComplete
    ProfileComplete -- No --> AddIncomplete
    ProfileComplete -- Yes --> Eligible
    AddIncomplete --> Eligible
    Eligible -- Yes --> ReturnTrue
    Eligible -- No --> ReturnFalse
```

## Eligibility Sequence

```mermaid
sequenceDiagram
    autonumber
    participant Internal as Internal Service
    participant Controller as UserInternalController
    participant App as CustomerApplicationService
    participant CustomerRepo as CustomerRepository
    participant ProfileService as ProfileCompletionService
    participant DB as PostgreSQL

    Internal->>Controller: GET /internal/users/{customerId}/eligibility
    Controller->>App: checkEligibility(customerId)
    App->>CustomerRepo: findById(customerId)
    CustomerRepo->>DB: select customers by id
    DB-->>CustomerRepo: customer row
    CustomerRepo-->>App: CustomerEntity
    App->>App: add status reasons
    App->>ProfileService: calculate(customer)
    ProfileService-->>App: profileStatus
    App->>App: add PROFILE_INCOMPLETE if needed
    App-->>Controller: CustomerEligibilityResult
    Controller-->>Internal: ApiResponse success
```

## Error Handling Workflow

```mermaid
flowchart TD
    Start([Controller or service detects error])
    ErrorType{Error type}
    UserError[UserServiceException]
    ValidationError[MethodArgumentNotValidException]
    MapStatus[Map UserErrorCode to HTTP status]
    BadRequest[HTTP 400 USER_INVALID_REQUEST]
    Envelope[Build ApiResponse failure envelope]
    Return[Return error response]

    Start --> ErrorType
    ErrorType -- Domain/application error --> UserError
    ErrorType -- Bean validation error --> ValidationError
    UserError --> MapStatus
    ValidationError --> BadRequest
    MapStatus --> Envelope
    BadRequest --> Envelope
    Envelope --> Return
```

## Outbox Event Workflow

The current service writes outbox records inside the same transaction as customer changes.

```mermaid
flowchart TD
    DomainChange[Customer state changes]
    BuildEnvelope[Build platform event envelope]
    Serialize[Serialize payload to JSON]
    InsertOutbox[(Insert outbox_events row)]
    Pending[status = PENDING]
    PublisherMissing[Kafka publisher scheduler not implemented yet]

    DomainChange --> BuildEnvelope
    BuildEnvelope --> Serialize
    Serialize --> InsertOutbox
    InsertOutbox --> Pending
    Pending -. future work .-> PublisherMissing
```

Current outbox event types:

```text
customer.created.v1
customer.profile_updated.v1
customer.suspended.v1
customer.reactivated.v1
customer.closed.v1
```

