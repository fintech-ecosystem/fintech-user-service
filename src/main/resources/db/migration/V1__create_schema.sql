-- src/main/resources/db/migration/V1__create_customer_schema.sql

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- =========================================================
-- CUSTOMERS
-- =========================================================
CREATE TABLE customers (
   id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

-- Reference to auth-service user.
-- No physical FK because auth_user_id belongs to another service/database.
   auth_user_id UUID NOT NULL,

-- Snapshot from auth-service
   email VARCHAR(255) NOT NULL,
   phone_number VARCHAR(50),
   full_name VARCHAR(255),
   date_of_birth DATE,

   customer_type VARCHAR(30) NOT NULL,
   status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
   profile_status VARCHAR(30) NOT NULL DEFAULT 'INCOMPLETE',

   address_line1 VARCHAR(255),
   address_line2 VARCHAR(255),
   city VARCHAR(100),
   country VARCHAR(10),

   created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
   updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

-- Optimistic locking
   version BIGINT NOT NULL DEFAULT 0,

   CONSTRAINT uk_customers_auth_user_id UNIQUE (auth_user_id),

   CONSTRAINT chk_customers_customer_type
       CHECK (customer_type IN ('INDIVIDUAL', 'BUSINESS')),

   CONSTRAINT chk_customers_status
       CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED')),

   CONSTRAINT chk_customers_profile_status
       CHECK (profile_status IN ('INCOMPLETE', 'COMPLETED')),

   CONSTRAINT chk_customers_version
       CHECK (version >= 0)
);

CREATE INDEX idx_customers_email
    ON customers(email);

CREATE INDEX idx_customers_phone_number
    ON customers(phone_number);

CREATE INDEX idx_customers_status
    ON customers(status);

CREATE INDEX idx_customers_profile_status
    ON customers(profile_status);

CREATE INDEX idx_customers_customer_type
    ON customers(customer_type);

CREATE INDEX idx_customers_created_at
    ON customers(created_at);


-- =========================================================
-- CUSTOMER_STATUS_HISTORY
-- =========================================================
CREATE TABLE customer_status_history (
     id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

     customer_id UUID NOT NULL,

     from_status VARCHAR(30),
     to_status VARCHAR(30) NOT NULL,

     reason TEXT,

    -- Admin/system actor id
     changed_by UUID,

     correlation_id VARCHAR(100),

     changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

     CONSTRAINT fk_customer_status_history_customer
         FOREIGN KEY (customer_id)
             REFERENCES customers(id),

     CONSTRAINT chk_customer_status_history_from_status
         CHECK (
             from_status IS NULL
                 OR from_status IN ('ACTIVE', 'SUSPENDED', 'CLOSED')
             ),

     CONSTRAINT chk_customer_status_history_to_status
         CHECK (to_status IN ('ACTIVE', 'SUSPENDED', 'CLOSED'))
);

CREATE INDEX idx_customer_status_history_customer_id
    ON customer_status_history(customer_id);

CREATE INDEX idx_customer_status_history_changed_at
    ON customer_status_history(changed_at);

CREATE INDEX idx_customer_status_history_correlation_id
    ON customer_status_history(correlation_id);


-- =========================================================
-- CUSTOMER_PROFILE_HISTORY
-- =========================================================
CREATE TABLE customer_profile_history (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

  customer_id UUID NOT NULL,

-- Business-significant changed fields
  changed_fields JSONB NOT NULL,

-- Customer/admin actor id
  changed_by UUID,

  correlation_id VARCHAR(100),

  changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

  CONSTRAINT fk_customer_profile_history_customer
      FOREIGN KEY (customer_id)
          REFERENCES customers(id),

  CONSTRAINT chk_customer_profile_history_changed_fields_object
      CHECK (jsonb_typeof(changed_fields) = 'object')
);

CREATE INDEX idx_customer_profile_history_customer_id
    ON customer_profile_history(customer_id);

CREATE INDEX idx_customer_profile_history_changed_at
    ON customer_profile_history(changed_at);

CREATE INDEX idx_customer_profile_history_correlation_id
    ON customer_profile_history(correlation_id);

CREATE INDEX idx_customer_profile_history_changed_fields
    ON customer_profile_history
    USING GIN (changed_fields);


-- =========================================================
-- OUTBOX_EVENTS
-- =========================================================
CREATE TABLE outbox_events (
   id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

   aggregate_type VARCHAR(100) NOT NULL,

-- Logical customer id.
-- No physical FK because outbox should stay decoupled from domain table lifecycle.
   aggregate_id VARCHAR(100) NOT NULL,

   event_type VARCHAR(150) NOT NULL,

   payload JSONB NOT NULL,

   status VARCHAR(30) NOT NULL DEFAULT 'PENDING',

   retry_count INT NOT NULL DEFAULT 0,
   max_retry_count INT NOT NULL DEFAULT 5,

   next_retry_at TIMESTAMP,
   published_at TIMESTAMP,

   created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
   updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

   correlation_id VARCHAR(100),
   causation_id VARCHAR(100),

   error_message TEXT,

   CONSTRAINT chk_outbox_events_status
       CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),

   CONSTRAINT chk_outbox_events_retry_count
       CHECK (retry_count >= 0),

   CONSTRAINT chk_outbox_events_max_retry_count
       CHECK (max_retry_count >= 0),

   CONSTRAINT chk_outbox_events_payload_object
       CHECK (jsonb_typeof(payload) = 'object')
);

CREATE INDEX idx_outbox_events_status_next_retry_at
    ON outbox_events(status, next_retry_at);

CREATE INDEX idx_outbox_events_status_created_at
    ON outbox_events(status, created_at);

CREATE INDEX idx_outbox_events_aggregate
    ON outbox_events(aggregate_type, aggregate_id);

CREATE INDEX idx_outbox_events_event_type
    ON outbox_events(event_type);

CREATE INDEX idx_outbox_events_correlation_id
    ON outbox_events(correlation_id);

CREATE INDEX idx_outbox_events_created_at
    ON outbox_events(created_at);
