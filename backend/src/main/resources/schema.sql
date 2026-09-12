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
-- 邮箱：用于验证码找回密码（可空；绑定后唯一）
ALTER TABLE users ADD COLUMN IF NOT EXISTS email VARCHAR(120);
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_email ON users (email);

-- 邮箱验证码（绑定邮箱 / 找回密码共用，按 email+purpose 一行，重发即覆盖）
CREATE TABLE IF NOT EXISTS auth_codes (
    email      VARCHAR(120) NOT NULL,
    purpose    VARCHAR(16)  NOT NULL, -- bind / reset
    code_hash  VARCHAR(64)  NOT NULL, -- SHA-256（不存明文）
    expires_at TIMESTAMPTZ  NOT NULL,
    attempts   INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (email, purpose)
);

CREATE TABLE IF NOT EXISTS conversations (
    id          VARCHAR(48) PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    title       VARCHAR(100) NOT NULL DEFAULT '新对话',
    rag_enabled BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
-- 角色化会话：会话绑定的角色与模式（advisor=恋爱顾问 / companion=暖心陪伴）
ALTER TABLE conversations ADD COLUMN IF NOT EXISTS persona VARCHAR(24) NOT NULL DEFAULT 'jiejie';
ALTER TABLE conversations ADD COLUMN IF NOT EXISTS mode VARCHAR(16) NOT NULL DEFAULT 'advisor';
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

-- 情侣绑定：code 供一方生成、另一方提交完成绑定
CREATE TABLE IF NOT EXISTS couples (
    id               BIGSERIAL PRIMARY KEY,
    code             VARCHAR(12) UNIQUE NOT NULL,
    user_a           BIGINT NOT NULL,
    user_b           BIGINT,
    anniversary_date DATE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_couples_users ON couples (user_a, user_b);

-- 每日情话：一天一句，全站共享缓存
CREATE TABLE IF NOT EXISTS daily_quotes (
    qdate      DATE PRIMARY KEY,
    content    VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- AI 朋友圈：角色以自己的口吻每天发一条动态；用户可点赞、评论（AI 会回复）
CREATE TABLE IF NOT EXISTS moments (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    persona    VARCHAR(24)  NOT NULL DEFAULT 'jiejie',
    content    VARCHAR(300) NOT NULL,
    liked      BOOLEAN      NOT NULL DEFAULT FALSE,
    mdate      DATE         NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (user_id, persona, mdate)
);
CREATE INDEX IF NOT EXISTS idx_moments_user ON moments (user_id, persona, mdate DESC);

CREATE TABLE IF NOT EXISTS moment_comments (
    id         BIGSERIAL PRIMARY KEY,
    moment_id  BIGINT       NOT NULL,
    role       VARCHAR(8)   NOT NULL, -- user / ai
    content    VARCHAR(300) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_moment_comments_moment ON moment_comments (moment_id, id);

-- 心情打卡：每天一条（1-5 分），用于情绪趋势与成就
CREATE TABLE IF NOT EXISTS mood_logs (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    score      INT         NOT NULL CHECK (score BETWEEN 1 AND 5),
    note       VARCHAR(200),
    mdate      DATE        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, mdate)
);
CREATE INDEX IF NOT EXISTS idx_mood_user ON mood_logs (user_id, mdate DESC);

-- 会员订阅：VIP 状态与订单（模拟支付；接入真实支付网关时仅替换回调入口）
CREATE TABLE IF NOT EXISTS memberships (
    user_id    BIGINT      PRIMARY KEY,
    plan       VARCHAR(16),
    vip_until  TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS membership_orders (
    id         TEXT        PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    plan       VARCHAR(16) NOT NULL,
    price_fen  BIGINT      NOT NULL,
    status     VARCHAR(16) NOT NULL DEFAULT 'pending', -- pending / paid
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    paid_at    TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_membership_orders_user ON membership_orders (user_id, created_at DESC);

-- 免费用户每日 AI 额度计数（每天一行，跨天自然滚动）
CREATE TABLE IF NOT EXISTS membership_daily_usage (
    user_id BIGINT NOT NULL,
    uday    DATE   NOT NULL,
    used    INT    NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, uday)
);
