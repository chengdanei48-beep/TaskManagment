CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE columns (
    id       BIGSERIAL PRIMARY KEY,
    user_id  BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name     VARCHAR(20) NOT NULL,
    position INTEGER NOT NULL
);

CREATE INDEX idx_columns_user_id ON columns (user_id);

CREATE TABLE cards (
    id          BIGSERIAL PRIMARY KEY,
    column_id   BIGINT NOT NULL REFERENCES columns (id) ON DELETE CASCADE,
    title       VARCHAR(50) NOT NULL,
    description VARCHAR(500),
    due_date    DATE,
    priority    VARCHAR(10),
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    position    INTEGER NOT NULL
);

CREATE INDEX idx_cards_column_id ON cards (column_id);
