-- V7: invoices, invoice_lines, invoice_events, invoice_payments, invoice sequence
ALTER TABLE invoices RENAME TO legacy_invoices;

CREATE SEQUENCE invoice_seq START 1;

CREATE TABLE invoices (
    id uuid PRIMARY KEY,
    reference varchar(60) NOT NULL UNIQUE,
    booking_id uuid,
    quote_id uuid,
    customer_id uuid NOT NULL,
    currency char(3) NOT NULL,
    subtotal numeric(12,3) NOT NULL,
    discount_amount numeric(12,3) NOT NULL,
    tax_amount numeric(12,3) NOT NULL,
    total_amount numeric(12,3) NOT NULL,
    issued_at timestamptz NOT NULL,
    due_at timestamptz,
    status varchar(30) NOT NULL,
    created_at timestamptz DEFAULT now() NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL
);

CREATE TABLE invoice_lines (
    id uuid PRIMARY KEY,
    invoice_id uuid NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    type varchar(40) NOT NULL,
    description varchar(400) NOT NULL,
    quantity int NOT NULL,
    unit_price numeric(12,3) NOT NULL,
    amount numeric(12,3) NOT NULL,
    metadata jsonb DEFAULT '{}'::jsonb,
    position int NOT NULL
);

CREATE TABLE invoice_events (
    id uuid PRIMARY KEY,
    invoice_id uuid NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    action varchar(60) NOT NULL,
    message text,
    created_at timestamptz DEFAULT now() NOT NULL
);

CREATE TABLE invoice_payments (
    invoice_id uuid NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    payment_id uuid NOT NULL REFERENCES payments(id) ON DELETE CASCADE,
    PRIMARY KEY (invoice_id, payment_id)
);

CREATE INDEX idx_invoices_booking ON invoices(booking_id);
CREATE INDEX idx_invoices_quote ON invoices(quote_id);
CREATE INDEX idx_invoice_lines_invoice ON invoice_lines(invoice_id);
