-- The per-customer booking limit counts upcoming bookings by email, so index that lookup.
CREATE INDEX idx_bookings_email_date ON bookings (customer_email, appointment_date);

-- The service stores emails in lower case. Make the database refuse anything else.
ALTER TABLE bookings ADD CONSTRAINT ck_bookings_email_lower CHECK (customer_email = lower(customer_email));