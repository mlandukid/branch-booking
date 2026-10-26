CREATE TABLE bookings (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reference        VARCHAR(36)  NOT NULL,
    branch_id        BIGINT       NOT NULL,
    appointment_date DATE         NOT NULL,
    start_time       TIME         NOT NULL,
    customer_name    VARCHAR(120) NOT NULL,
    customer_email   VARCHAR(254) NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_bookings_branch FOREIGN KEY (branch_id) REFERENCES branches (id),
    CONSTRAINT uq_bookings_reference UNIQUE (reference),
    -- The guarantee against double-booking lives here, in the database.
    CONSTRAINT uq_bookings_slot UNIQUE (branch_id, appointment_date, start_time)
);