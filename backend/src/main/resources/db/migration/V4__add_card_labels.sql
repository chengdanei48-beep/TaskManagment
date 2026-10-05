CREATE TABLE card_labels (
    card_id  BIGINT NOT NULL REFERENCES cards (id) ON DELETE CASCADE,
    label_id BIGINT NOT NULL REFERENCES labels (id) ON DELETE CASCADE,
    PRIMARY KEY (card_id, label_id)
);

CREATE INDEX idx_card_labels_label_id ON card_labels (label_id);
