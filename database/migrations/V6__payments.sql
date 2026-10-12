-- V6: Payments and payment events
ALTER TABLE payments RENAME TO legacy_payments;

CREATE TABLE payments (
    id UUID PRIMARY KEY,
    quote_id UUID NOT NULL,
    booking_id UUID,
    customer_id UUID NOT NULL,
    amount numeric(12,3) NOT NULL,
    currency varchar(3) NOT NULL,
    method varchar(30) NOT NULL,
    status varchar(30) NOT NULL,
    idempotency_key varchar(100),
    transaction_ref varchar(128),
    created_at timestamptz NOT NULL DEFAULT now(),
    captured_at timestamptz,
    refunded_at timestamptz,
    refunded_amount numeric(12,3) DEFAULT 0
);
CREATE UNIQUE INDEX payments_idempotency_idx ON payments(idempotency_key);
ALTER TABLE payments ADD CONSTRAINT fk_payments_quote FOREIGN KEY (quote_id) REFERENCES quotes(id) ON DELETE CASCADE;
ALTER TABLE payments ADD CONSTRAINT fk_payments_booking FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE SET NULL;

CREATE TABLE payment_events (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL,
    action varchar(60) NOT NULL,
    message text,
    created_at timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE payment_events ADD CONSTRAINT fk_payment_events_payment FOREIGN KEY (payment_id) REFERENCES payments(id) ON DELETE CASCADE;
