-- Remembers which booking an Idempotency-Key produced, so a retry can return it.
CREATE TABLE idempotency_keys (
    key_value  VARCHAR(64) PRIMARY KEY,
    booking_id BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_idempotency_keys_booking FOREIGN KEY (booking_id) REFERENCES bookings (id)
);