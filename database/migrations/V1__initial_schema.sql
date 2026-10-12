CREATE TABLE roles (
    role_key VARCHAR(40) PRIMARY KEY,
    display_name VARCHAR(80) NOT NULL
);

CREATE TABLE permissions (
    permission_key VARCHAR(80) PRIMARY KEY,
    description VARCHAR(160) NOT NULL
);

CREATE TABLE role_permissions (
    role_key VARCHAR(40) NOT NULL REFERENCES roles(role_key) ON DELETE CASCADE,
    permission_key VARCHAR(80) NOT NULL REFERENCES permissions(permission_key) ON DELETE CASCADE,
    PRIMARY KEY (role_key, permission_key)
);

CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role_key VARCHAR(40) NOT NULL REFERENCES roles(role_key),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE customers (
    id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    first_name VARCHAR(80),
    last_name VARCHAR(80),
    phone VARCHAR(40),
    customer_level VARCHAR(20) NOT NULL DEFAULT 'STANDARD'
);

CREATE TABLE employees (
    id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    employee_number VARCHAR(40) NOT NULL UNIQUE,
    job_title VARCHAR(80) NOT NULL
);

CREATE TABLE brands (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    slug VARCHAR(120) NOT NULL UNIQUE,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE categories (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    slug VARCHAR(120) NOT NULL UNIQUE,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE vehicles (
    id UUID PRIMARY KEY,
    brand_id UUID NOT NULL REFERENCES brands(id),
    category_id UUID NOT NULL REFERENCES categories(id),
    model VARCHAR(120) NOT NULL,
    model_year SMALLINT NOT NULL,
    price_per_day NUMERIC(12, 3) NOT NULL CHECK (price_per_day >= 0),
    price_with_driver NUMERIC(12, 3) CHECK (price_with_driver >= 0),
    passengers SMALLINT NOT NULL CHECK (passengers > 0),
    doors SMALLINT NOT NULL CHECK (doors > 0),
    transmission VARCHAR(30) NOT NULL,
    fuel VARCHAR(30) NOT NULL,
    description TEXT NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE vehicle_images (
    id UUID PRIMARY KEY,
    vehicle_id UUID NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
    storage_key VARCHAR(500) NOT NULL,
    alt_text VARCHAR(200) NOT NULL,
    display_order SMALLINT NOT NULL DEFAULT 0
);

CREATE TABLE vehicle_features (
    vehicle_id UUID NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
    feature VARCHAR(100) NOT NULL,
    PRIMARY KEY (vehicle_id, feature)
);

CREATE TABLE drivers (
    id UUID PRIMARY KEY,
    first_name VARCHAR(80) NOT NULL,
    last_name VARCHAR(80) NOT NULL,
    phone VARCHAR(40) NOT NULL,
    license_number VARCHAR(80) NOT NULL UNIQUE,
    status VARCHAR(30) NOT NULL
);

CREATE TABLE driver_documents (
    id UUID PRIMARY KEY,
    driver_id UUID NOT NULL REFERENCES drivers(id) ON DELETE CASCADE,
    document_id UUID NOT NULL,
    document_type VARCHAR(60) NOT NULL,
    expires_at DATE
);

CREATE TABLE bookings (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES customers(id),
    vehicle_id UUID NOT NULL REFERENCES vehicles(id),
    driver_id UUID REFERENCES drivers(id),
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(30) NOT NULL,
    total_amount NUMERIC(12, 3) NOT NULL CHECK (total_amount >= 0),
    currency CHAR(3) NOT NULL DEFAULT 'TND',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (ends_at > starts_at)
);

CREATE TABLE services (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    description TEXT NOT NULL,
    price NUMERIC(12, 3) NOT NULL CHECK (price >= 0),
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE booking_services (
    booking_id UUID NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
    service_id UUID NOT NULL REFERENCES services(id),
    quantity SMALLINT NOT NULL DEFAULT 1 CHECK (quantity > 0),
    unit_price NUMERIC(12, 3) NOT NULL CHECK (unit_price >= 0),
    PRIMARY KEY (booking_id, service_id)
);

CREATE TABLE quotes (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES customers(id),
    vehicle_id UUID REFERENCES vehicles(id),
    status VARCHAR(30) NOT NULL,
    valid_until DATE,
    subtotal NUMERIC(12, 3) NOT NULL DEFAULT 0 CHECK (subtotal >= 0),
    total NUMERIC(12, 3) NOT NULL DEFAULT 0 CHECK (total >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE quote_items (
    id UUID PRIMARY KEY,
    quote_id UUID NOT NULL REFERENCES quotes(id) ON DELETE CASCADE,
    description VARCHAR(200) NOT NULL,
    quantity SMALLINT NOT NULL DEFAULT 1 CHECK (quantity > 0),
    unit_price NUMERIC(12, 3) NOT NULL CHECK (unit_price >= 0)
);

CREATE TABLE payments (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL REFERENCES bookings(id),
    amount NUMERIC(12, 3) NOT NULL CHECK (amount >= 0),
    currency CHAR(3) NOT NULL DEFAULT 'TND',
    status VARCHAR(30) NOT NULL,
    provider VARCHAR(60),
    provider_reference VARCHAR(160),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE invoices (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL UNIQUE REFERENCES bookings(id),
    invoice_number VARCHAR(60) NOT NULL UNIQUE,
    status VARCHAR(30) NOT NULL,
    total NUMERIC(12, 3) NOT NULL CHECK (total >= 0),
    issued_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE service_requests (
    id UUID PRIMARY KEY,
    customer_id UUID REFERENCES customers(id),
    service_id UUID REFERENCES services(id),
    request_type VARCHAR(40) NOT NULL,
    status VARCHAR(30) NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE partners (
    id UUID PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    partner_type VARCHAR(40) NOT NULL,
    email VARCHAR(254),
    phone VARCHAR(40),
    commission_rate NUMERIC(5, 2) CHECK (commission_rate BETWEEN 0 AND 100),
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    channel VARCHAR(20) NOT NULL,
    title VARCHAR(160) NOT NULL,
    body TEXT NOT NULL,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE documents (
    id UUID PRIMARY KEY,
    owner_user_id UUID REFERENCES users(id),
    storage_key VARCHAR(500) NOT NULL UNIQUE,
    content_type VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE driver_documents
    ADD CONSTRAINT fk_driver_documents_document FOREIGN KEY (document_id) REFERENCES documents(id);

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,
    actor_user_id UUID REFERENCES users(id),
    action VARCHAR(120) NOT NULL,
    entity_type VARCHAR(80) NOT NULL,
    entity_id UUID,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_bookings_vehicle_dates ON bookings(vehicle_id, starts_at, ends_at);
CREATE INDEX idx_bookings_customer_created ON bookings(customer_id, created_at DESC);
CREATE INDEX idx_quotes_customer_created ON quotes(customer_id, created_at DESC);
CREATE INDEX idx_notifications_user_created ON notifications(user_id, created_at DESC);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);

INSERT INTO roles (role_key, display_name) VALUES
    ('SUPER_ADMIN', 'Super administrateur'),
    ('MANAGER', 'Gestionnaire'),
    ('AGENT', 'Agent'),
    ('CONCIERGE', 'Conciergerie'),
    ('ACCOUNTANT', 'Comptabilite'),
    ('CUSTOMER', 'Client');