-- Emails are written here in the same transaction as the booking change, then sent by a dispatcher.
CREATE TABLE email_outbox (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    to_address      VARCHAR(254)  NOT NULL,
    subject         VARCHAR(200)  NOT NULL,
    body            VARCHAR(4000) NOT NULL,
    status          VARCHAR(10)   NOT NULL,
    attempts        INTEGER       NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    claimed_until   TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    sent_at         TIMESTAMP WITH TIME ZONE,
    last_error      VARCHAR(500),
    CONSTRAINT ck_email_outbox_status CHECK (status IN ('PENDING', 'SENT', 'FAILED'))
);
CREATE INDEX idx_email_outbox_due ON email_outbox (status, next_attempt_at);