CREATE TABLE branches (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name         VARCHAR(100) NOT NULL,
    address      VARCHAR(255) NOT NULL,
    city         VARCHAR(100) NOT NULL,
    opens_at     TIME         NOT NULL,
    closes_at    TIME         NOT NULL,
    slot_minutes INTEGER      NOT NULL,
    CONSTRAINT uq_branches_name UNIQUE (name),
    CONSTRAINT ck_branches_hours CHECK (opens_at < closes_at),
    CONSTRAINT ck_branches_slot_minutes CHECK (slot_minutes > 0)
);