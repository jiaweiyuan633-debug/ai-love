-- 应用业务表（幂等建表，启动时自动执行）
-- pgvector 的 vector_store 表由 Spring AI 的 initialize-schema 自动创建

CREATE TABLE IF NOT EXISTS users (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(64)  UNIQUE NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    nickname      VARCHAR(64),
    memory_enabled BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS conversations (
    id          VARCHAR(48) PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    title       VARCHAR(100) NOT NULL DEFAULT '新对话',
    rag_enabled BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_conversations_user ON conversations (user_id, updated_at DESC);

CREATE TABLE IF NOT EXISTS messages (
    id              BIGSERIAL PRIMARY KEY,
    conversation_id VARCHAR(48) NOT NULL,
    role            VARCHAR(16) NOT NULL, -- user / assistant
    content         TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_messages_conversation ON messages (conversation_id, id);

CREATE TABLE IF NOT EXISTS user_memories (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    content    VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_user_memories_user ON user_memories (user_id, id DESC);
