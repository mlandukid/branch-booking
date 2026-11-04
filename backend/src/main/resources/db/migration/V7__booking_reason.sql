-- Why the customer is visiting. Existing bookings get OTHER.
ALTER TABLE bookings ADD COLUMN reason VARCHAR(30) NOT NULL DEFAULT 'OTHER';
ALTER TABLE bookings ADD CONSTRAINT ck_bookings_reason
    CHECK (reason IN ('OPEN_ACCOUNT', 'REPLACE_CARD', 'TROUBLESHOOTING', 'UPDATE_DETAILS', 'LOANS_AND_SAVINGS', 'OTHER'));