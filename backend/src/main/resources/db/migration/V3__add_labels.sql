CREATE TABLE labels (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name       VARCHAR(20) NOT NULL,
    color      VARCHAR(7) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_labels_user_name UNIQUE (user_id, name)
);
