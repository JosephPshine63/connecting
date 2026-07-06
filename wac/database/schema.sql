CREATE SEQUENCE IF NOT EXISTS msg_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE chat
(
    id                     VARCHAR(255)                NOT NULL,
    created_date           TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    last_modified_date     TIMESTAMP WITHOUT TIME ZONE,
    sender_id              VARCHAR(255),
    recipient_id           VARCHAR(255),
    status                 VARCHAR(50)                 NOT NULL DEFAULT 'ACCEPTED',
    pending_message_count  INTEGER                     NOT NULL DEFAULT 0,
    sender_favorite        BOOLEAN                     NOT NULL DEFAULT FALSE,
    recipient_favorite     BOOLEAN                     NOT NULL DEFAULT FALSE,
    sender_archived        BOOLEAN                     NOT NULL DEFAULT FALSE,
    recipient_archived     BOOLEAN                     NOT NULL DEFAULT FALSE,
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
    reply_to_id        BIGINT,
    forwarded          BOOLEAN                     NOT NULL DEFAULT FALSE,
    deleted            BOOLEAN                     NOT NULL DEFAULT FALSE,
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
    avatar_url         VARCHAR(500),
    active_session_id  VARCHAR(255),
    CONSTRAINT pk_users PRIMARY KEY (id)
);

-- Migration for existing databases:
-- ALTER TABLE users ADD COLUMN IF NOT EXISTS username VARCHAR(255);
-- ALTER TABLE users ADD CONSTRAINT users_username_key UNIQUE (username);
-- ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_url VARCHAR(500);
-- ALTER TABLE users ADD COLUMN IF NOT EXISTS active_session_id VARCHAR(255);
-- ALTER TABLE chat ADD COLUMN IF NOT EXISTS sender_favorite BOOLEAN NOT NULL DEFAULT FALSE;
-- ALTER TABLE chat ADD COLUMN IF NOT EXISTS recipient_favorite BOOLEAN NOT NULL DEFAULT FALSE;
-- CREATE INDEX IF NOT EXISTS idx_messages_chat_id_created_date ON messages (chat_id, created_date);
-- ALTER TABLE chat ADD COLUMN IF NOT EXISTS sender_archived BOOLEAN NOT NULL DEFAULT FALSE;
-- ALTER TABLE chat ADD COLUMN IF NOT EXISTS recipient_archived BOOLEAN NOT NULL DEFAULT FALSE;
-- ALTER TABLE messages ADD COLUMN IF NOT EXISTS reply_to_id BIGINT;
-- ALTER TABLE messages ADD COLUMN IF NOT EXISTS forwarded BOOLEAN NOT NULL DEFAULT FALSE;
-- ALTER TABLE messages ADD CONSTRAINT FK_MESSAGES_ON_REPLY_TO FOREIGN KEY (reply_to_id) REFERENCES messages (id);
-- ALTER TABLE messages ADD COLUMN IF NOT EXISTS deleted BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE chat
    ADD CONSTRAINT FK_CHAT_ON_RECIPIENT FOREIGN KEY (recipient_id) REFERENCES users (id);

ALTER TABLE chat
    ADD CONSTRAINT FK_CHAT_ON_SENDER FOREIGN KEY (sender_id) REFERENCES users (id);

ALTER TABLE messages
    ADD CONSTRAINT FK_MESSAGES_ON_CHAT FOREIGN KEY (chat_id) REFERENCES chat (id);

ALTER TABLE messages
    ADD CONSTRAINT FK_MESSAGES_ON_REPLY_TO FOREIGN KEY (reply_to_id) REFERENCES messages (id);

CREATE INDEX IF NOT EXISTS idx_messages_chat_id_created_date ON messages (chat_id, created_date);

-- New tables (blocked_users, user_reports) — for existing databases, run the
-- CREATE TABLE IF NOT EXISTS + FK ALTER TABLE statements below manually once;
-- no column-migration comment block is needed since these are net-new tables,
-- not new columns on existing tables. If a constraint already exists, its
-- ALTER TABLE will error harmlessly — drop and re-add, or skip if already applied.

CREATE SEQUENCE IF NOT EXISTS blocked_users_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS blocked_users
(
    id                 BIGINT                      NOT NULL,
    created_date       TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    last_modified_date TIMESTAMP WITHOUT TIME ZONE,
    blocker_id         VARCHAR(255)                NOT NULL,
    blocked_id         VARCHAR(255)                NOT NULL,
    CONSTRAINT pk_blocked_users PRIMARY KEY (id),
    CONSTRAINT uk_blocked_users_pair UNIQUE (blocker_id, blocked_id)
);

ALTER TABLE blocked_users
    ADD CONSTRAINT FK_BLOCKED_USERS_ON_BLOCKER FOREIGN KEY (blocker_id) REFERENCES users (id);
ALTER TABLE blocked_users
    ADD CONSTRAINT FK_BLOCKED_USERS_ON_BLOCKED FOREIGN KEY (blocked_id) REFERENCES users (id);

CREATE SEQUENCE IF NOT EXISTS user_reports_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS user_reports
(
    id                 BIGINT                      NOT NULL,
    created_date       TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    last_modified_date TIMESTAMP WITHOUT TIME ZONE,
    reporter_id        VARCHAR(255)                NOT NULL,
    reported_id        VARCHAR(255)                NOT NULL,
    reason             VARCHAR(50)                 NOT NULL,
    details            TEXT,
    status             VARCHAR(50)                 NOT NULL DEFAULT 'OPEN',
    CONSTRAINT pk_user_reports PRIMARY KEY (id)
);

ALTER TABLE user_reports
    ADD CONSTRAINT FK_USER_REPORTS_ON_REPORTER FOREIGN KEY (reporter_id) REFERENCES users (id);
ALTER TABLE user_reports
    ADD CONSTRAINT FK_USER_REPORTS_ON_REPORTED FOREIGN KEY (reported_id) REFERENCES users (id);

CREATE SEQUENCE IF NOT EXISTS message_reactions_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS message_reactions
(
    id                 BIGINT                      NOT NULL,
    created_date       TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    last_modified_date TIMESTAMP WITHOUT TIME ZONE,
    message_id         BIGINT                      NOT NULL,
    user_id            VARCHAR(255)                NOT NULL,
    emoji              VARCHAR(32)                 NOT NULL,
    CONSTRAINT pk_message_reactions PRIMARY KEY (id),
    CONSTRAINT uk_message_reactions_user_per_message UNIQUE (message_id, user_id)
);

ALTER TABLE message_reactions
    ADD CONSTRAINT FK_MESSAGE_REACTIONS_ON_MESSAGE FOREIGN KEY (message_id) REFERENCES messages (id);