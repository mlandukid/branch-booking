-- Customers manage a booking with a private token. Only its hash is stored, so a database leak
-- cannot be used to change or cancel bookings.
ALTER TABLE bookings ADD COLUMN manage_token_hash VARCHAR(64);

-- Optimistic locking: two changes to the same booking at the same moment cannot both win.
ALTER TABLE bookings ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Cancelling deletes the booking, which frees its slot. Its idempotency key goes with it.
ALTER TABLE idempotency_keys DROP CONSTRAINT fk_idempotency_keys_booking;
ALTER TABLE idempotency_keys ADD CONSTRAINT fk_idempotency_keys_booking
    FOREIGN KEY (booking_id) REFERENCES bookings (id) ON DELETE CASCADE;

-- What happened to each booking, kept after the booking itself is gone. No personal data.
CREATE TABLE booking_history (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reference  VARCHAR(36) NOT NULL,
    action     VARCHAR(20) NOT NULL,
    branch_id  BIGINT      NOT NULL,
    old_date   DATE        NOT NULL,
    old_time   TIME        NOT NULL,
    new_date   DATE,
    new_time   TIME,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_booking_history_reference ON booking_history (reference);