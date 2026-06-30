CREATE SEQUENCE IF NOT EXISTS msg_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE chat
(
    id                 VARCHAR(255)                NOT NULL,
    created_date       TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    last_modified_date TIMESTAMP WITHOUT TIME ZONE,
    sender_id          VARCHAR(255),
    recipient_id       VARCHAR(255),
    CONSTRAINT pk_chat PRIMARY KEY (id)
);

CREATE TABLE messages
(
    id                 BIGINT                      NOT NULL,
    created_date       TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    last_modified_date TIMESTAMP WITHOUT TIME ZONE,
    content            TEXT,
    state              VARCHAR(255),
    type               VARCHAR(255),
    chat_id            VARCHAR(255),
    sender_id          VARCHAR(255)                NOT NULL,
    receiver_id        VARCHAR(255)                NOT NULL,
    media_file_path    VARCHAR(255),
    CONSTRAINT pk_messages PRIMARY KEY (id)
);

CREATE TABLE users
(
    id                 VARCHAR(255)                NOT NULL,
    created_date       TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    last_modified_date TIMESTAMP WITHOUT TIME ZONE,
    first_name         VARCHAR(255),
    last_name          VARCHAR(255),
    email              VARCHAR(255),
    username           VARCHAR(255) UNIQUE,
    last_seen          TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT pk_users PRIMARY KEY (id)
);

-- Migration for existing databases:
-- ALTER TABLE users ADD COLUMN IF NOT EXISTS username VARCHAR(255);
-- ALTER TABLE users ADD CONSTRAINT users_username_key UNIQUE (username);

ALTER TABLE chat
    ADD CONSTRAINT FK_CHAT_ON_RECIPIENT FOREIGN KEY (recipient_id) REFERENCES users (id);

ALTER TABLE chat
    ADD CONSTRAINT FK_CHAT_ON_SENDER FOREIGN KEY (sender_id) REFERENCES users (id);

ALTER TABLE messages
    ADD CONSTRAINT FK_MESSAGES_ON_CHAT FOREIGN KEY (chat_id) REFERENCES chat (id);