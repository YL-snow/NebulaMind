-- ============================================================================
-- 云盘智能体应用 - 数据库DDL脚本
-- 数据库: PostgreSQL 15.x
-- 创建日期: 2026-07-11
-- 字符集: UTF-8
-- ============================================================================

-- 启用扩展
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";          -- 模糊搜索支持
CREATE EXTENSION IF NOT EXISTS "pg_stat_statements"; -- 查询性能监控

-- ============================================================================
-- 1. 用户表 (users)
-- ============================================================================
CREATE TABLE users (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    username        VARCHAR(100)    NOT NULL,
    email           VARCHAR(255)    NOT NULL,
    password_hash   VARCHAR(255)    NOT NULL,
    display_name    VARCHAR(100)    NOT NULL,
    avatar_url      VARCHAR(500),
    role            VARCHAR(20)     NOT NULL DEFAULT 'user',
    status          VARCHAR(20)     NOT NULL DEFAULT 'active',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    last_login_at   TIMESTAMPTZ,
    
    -- 约束
    CONSTRAINT ck_users_role CHECK (role IN ('admin', 'user')),
    CONSTRAINT ck_users_status CHECK (status IN ('active', 'disabled'))
);

-- 用户表索引
CREATE UNIQUE INDEX idx_users_username ON users (username);
CREATE UNIQUE INDEX idx_users_email ON users (email);
CREATE INDEX idx_users_status ON users (status) WHERE status = 'active';

-- 自动更新 updated_at
CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at();

-- ============================================================================
-- 2. 文件表 (files)
-- ============================================================================
CREATE TABLE files (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    name                VARCHAR(500)    NOT NULL,
    path                VARCHAR(1000)   NOT NULL,
    size                BIGINT          NOT NULL DEFAULT 0,
    mime_type           VARCHAR(100)    NOT NULL,
    file_type           VARCHAR(50)     NOT NULL,
    hash                VARCHAR(64)     NOT NULL,
    parent_id           UUID,
    user_id             UUID            NOT NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'UPLOADING',
    ai_status           VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    category            VARCHAR(100),
    tags                JSONB           DEFAULT '[]'::jsonb,
    summary             TEXT,
    ai_error_message    TEXT,
    sensitive_level     VARCHAR(20)     NOT NULL DEFAULT 'NORMAL',
    is_encrypted        BOOLEAN         NOT NULL DEFAULT FALSE,
    encryption_key_id   VARCHAR(100),
    encryption_mode     VARCHAR(20)     NOT NULL DEFAULT 'NONE',
    cloud_drive_file_id VARCHAR(200),                             -- 云盘/OSS文件原始ID（S3/WebDAV 外部存储）
    version             INTEGER         NOT NULL DEFAULT 1,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    -- 约束
    CONSTRAINT ck_files_status CHECK (status IN ('UPLOADING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_files_ai_status CHECK (ai_status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'SKIPPED')),
    CONSTRAINT ck_files_sensitive_level CHECK (sensitive_level IN ('NORMAL', 'LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT ck_files_size CHECK (size >= 0),
    CONSTRAINT ck_files_version CHECK (version >= 1),

    -- 外键
    CONSTRAINT fk_files_parent FOREIGN KEY (parent_id) REFERENCES files(id) ON DELETE SET NULL,
    CONSTRAINT fk_files_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 文件表索引
CREATE INDEX idx_files_user_id ON files (user_id);
CREATE INDEX idx_files_parent_id ON files (parent_id);
CREATE INDEX idx_files_hash ON files (hash);
CREATE INDEX idx_files_status ON files (status);
CREATE INDEX idx_files_ai_status ON files (ai_status);
CREATE INDEX idx_files_category ON files (category);
CREATE INDEX idx_files_created_at ON files (created_at DESC);
CREATE INDEX idx_files_sensitive_level ON files (sensitive_level);
CREATE INDEX idx_files_cloud_drive_file_id ON files (cloud_drive_file_id);  -- 云盘文件ID索引
CREATE INDEX idx_files_tags ON files (tags);
CREATE INDEX idx_files_name_trgm ON files USING GIN (name gin_trgm_ops);

-- 文件表更新触发器
CREATE TRIGGER trg_files_updated_at
    BEFORE UPDATE ON files
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at();

-- ============================================================================
-- 3. 文件内容分片表 (file_contents)
--    用于全文检索和RAG
-- ============================================================================
CREATE TABLE file_contents (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    file_id         UUID            NOT NULL,
    chunk_index     INTEGER         NOT NULL,
    chunk_content   TEXT            NOT NULL,
    chunk_metadata  JSONB           DEFAULT '{}'::jsonb,
    char_count      INTEGER         NOT NULL DEFAULT 0,
    token_count     INTEGER         NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    -- 约束
    CONSTRAINT ck_file_contents_chunk_index CHECK (chunk_index >= 0),
    CONSTRAINT ck_file_contents_char_count CHECK (char_count >= 0),
    CONSTRAINT ck_file_contents_token_count CHECK (token_count >= 0),

    -- 外键
    CONSTRAINT fk_file_contents_file FOREIGN KEY (file_id) REFERENCES files(id) ON DELETE CASCADE
);

-- 文件内容分片索引
CREATE INDEX idx_file_contents_file_id ON file_contents (file_id);
CREATE UNIQUE INDEX idx_file_contents_file_chunk ON file_contents (file_id, chunk_index);

-- BM25全文检索索引 (tsvector)
-- 使用 PostgreSQL 内置的全文检索实现 BM25 风格搜索
-- 中文分词需要配置 zhparser 或 jieba 扩展
CREATE INDEX idx_file_contents_fts ON file_contents
    USING GIN (to_tsvector('simple', coalesce(chunk_content, '')));

-- ============================================================================
-- 4. 文件版本表 (file_versions)
-- ============================================================================
CREATE TABLE file_versions (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    file_id         UUID            NOT NULL,
    version_number  INTEGER         NOT NULL,
    file_size       BIGINT          NOT NULL,
    file_hash       VARCHAR(64)     NOT NULL,
    storage_path    VARCHAR(1000)   NOT NULL,
    comment         VARCHAR(500),
    created_by      UUID            NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    -- 约束
    CONSTRAINT ck_file_versions_version_number CHECK (version_number >= 1),
    CONSTRAINT ck_file_versions_file_size CHECK (file_size >= 0),

    -- 外键
    CONSTRAINT fk_file_versions_file FOREIGN KEY (file_id) REFERENCES files(id) ON DELETE CASCADE,
    CONSTRAINT fk_file_versions_user FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT
);

-- 文件版本索引
CREATE INDEX idx_file_versions_file_id ON file_versions (file_id);
CREATE UNIQUE INDEX idx_file_versions_file_version ON file_versions (file_id, version_number);
CREATE INDEX idx_file_versions_created_by ON file_versions (created_by);

-- ============================================================================
-- 5. 权限表 (permissions)
-- ============================================================================
CREATE TABLE permissions (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    file_id         UUID            NOT NULL,
    user_id         UUID,
    permission_type VARCHAR(20)     NOT NULL,
    expires_at      TIMESTAMPTZ,
    created_by      UUID            NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    -- 约束
    CONSTRAINT ck_permissions_type CHECK (permission_type IN ('read', 'write', 'admin')),

    -- 外键
    CONSTRAINT fk_permissions_file FOREIGN KEY (file_id) REFERENCES files(id) ON DELETE CASCADE,
    CONSTRAINT fk_permissions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_permissions_creator FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT
);

-- 权限索引
CREATE INDEX idx_permissions_file_id ON permissions (file_id);
CREATE INDEX idx_permissions_user_id ON permissions (user_id);
CREATE UNIQUE INDEX idx_permissions_file_user_type
    ON permissions (file_id, COALESCE(user_id, '00000000-0000-0000-0000-000000000000'), permission_type);
CREATE INDEX idx_permissions_expires_at ON permissions (expires_at) WHERE expires_at IS NOT NULL;

-- ============================================================================
-- 6. 语义索引表 (semantic_indexes)
--    用于 RAG 检索，存储分片文本和向量数据
--    Milvus 负责向量检索，PostgreSQL 负责 BM25 全文检索
-- ============================================================================
CREATE TABLE semantic_indexes (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    file_id         UUID            NOT NULL,
    chunk_index     INTEGER         NOT NULL,
    chunk_text      TEXT            NOT NULL,
    embedding       TEXT,
    metadata        JSONB           DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    -- 约束
    CONSTRAINT ck_semantic_indexes_chunk_index CHECK (chunk_index >= 0),

    -- 外键
    CONSTRAINT fk_semantic_indexes_file FOREIGN KEY (file_id) REFERENCES files(id) ON DELETE CASCADE
);

-- 语义索引索引
CREATE INDEX idx_semantic_indexes_file_id ON semantic_indexes (file_id);
CREATE UNIQUE INDEX idx_semantic_indexes_file_chunk ON semantic_indexes (file_id, chunk_index);
CREATE INDEX idx_semantic_indexes_fts ON semantic_indexes
    USING GIN (to_tsvector('simple', coalesce(chunk_text, '')));
CREATE INDEX idx_semantic_indexes_metadata_gin ON semantic_indexes USING GIN (metadata);

-- ============================================================================
-- 7. AI调用日志表 (ai_call_logs)
-- ============================================================================
CREATE TABLE ai_call_logs (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id          UUID            NOT NULL,
    module              VARCHAR(50)     NOT NULL,
    model               VARCHAR(100)    NOT NULL,
    prompt              TEXT,
    response            TEXT,
    prompt_tokens       INTEGER         NOT NULL DEFAULT 0,
    completion_tokens   INTEGER         NOT NULL DEFAULT 0,
    total_tokens        INTEGER         NOT NULL DEFAULT 0,
    latency_ms          INTEGER         NOT NULL DEFAULT 0,
    success             BOOLEAN         NOT NULL DEFAULT TRUE,
    error_message       TEXT,
    file_id             UUID,
    user_id             UUID            NOT NULL,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    -- 约束
    CONSTRAINT ck_ai_call_logs_tokens CHECK (
        prompt_tokens >= 0 AND completion_tokens >= 0 AND total_tokens >= 0
    ),
    CONSTRAINT ck_ai_call_logs_latency CHECK (latency_ms >= 0),

    -- 外键
    CONSTRAINT fk_ai_call_logs_file FOREIGN KEY (file_id) REFERENCES files(id) ON DELETE SET NULL,
    CONSTRAINT fk_ai_call_logs_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT
);

-- AI调用日志索引
CREATE INDEX idx_ai_call_logs_request_id ON ai_call_logs (request_id);
CREATE INDEX idx_ai_call_logs_module ON ai_call_logs (module);
CREATE INDEX idx_ai_call_logs_model ON ai_call_logs (model);
CREATE INDEX idx_ai_call_logs_user_id ON ai_call_logs (user_id);
CREATE INDEX idx_ai_call_logs_file_id ON ai_call_logs (file_id);
CREATE INDEX idx_ai_call_logs_created_at ON ai_call_logs (created_at DESC);
CREATE INDEX idx_ai_call_logs_failed ON ai_call_logs (success) WHERE success = FALSE;

-- 自动清理过期日志 (90天)
CREATE OR REPLACE FUNCTION cleanup_ai_call_logs()
RETURNS void AS $$
BEGIN
    DELETE FROM ai_call_logs WHERE created_at < NOW() - INTERVAL '90 days';
END;
$$ LANGUAGE plpgsql;

-- ============================================================================
-- 8. 审计日志表 (audit_logs)
-- ============================================================================
CREATE TABLE audit_logs (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID,
    action          VARCHAR(50)     NOT NULL,
    resource_type   VARCHAR(50)     NOT NULL,
    resource_id     VARCHAR(100),
    details         JSONB           DEFAULT '{}'::jsonb,
    ip_address      VARCHAR(45)     NOT NULL,
    user_agent      TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    -- 约束
    CONSTRAINT ck_audit_logs_action CHECK (action IN (
        'login', 'logout', 'upload', 'download', 'delete',
        'share', 'revoke_share', 'encrypt', 'decrypt',
        'version_restore', 'classify', 'search', 'qa',
        'user_create', 'user_disable', 'role_change'
    )),
    CONSTRAINT ck_audit_logs_resource_type CHECK (resource_type IN (
        'file', 'permission', 'user', 'system'
    )),

    -- 外键
    CONSTRAINT fk_audit_logs_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
);

-- 审计日志索引
CREATE INDEX idx_audit_logs_user_id ON audit_logs (user_id);
CREATE INDEX idx_audit_logs_action ON audit_logs (action);
CREATE INDEX idx_audit_logs_resource_type ON audit_logs (resource_type);
CREATE INDEX idx_audit_logs_created_at ON audit_logs (created_at DESC);
CREATE INDEX idx_audit_logs_action_time ON audit_logs (action, created_at DESC);

-- 自动清理过期审计日志 (180天)
CREATE OR REPLACE FUNCTION cleanup_audit_logs()
RETURNS void AS $$
BEGIN
    DELETE FROM audit_logs WHERE created_at < NOW() - INTERVAL '180 days';
END;
$$ LANGUAGE plpgsql;

-- ============================================================================
-- 9. 刷新令牌表 (refresh_tokens)
-- ============================================================================
CREATE TABLE refresh_tokens (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID            NOT NULL,
    token_hash      VARCHAR(255)    NOT NULL,
    expires_at      TIMESTAMPTZ     NOT NULL,
    revoked         BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    -- 约束
    CONSTRAINT ck_refresh_tokens_expires_at CHECK (expires_at > created_at),

    -- 外键
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 刷新令牌索引
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
CREATE UNIQUE INDEX idx_refresh_tokens_hash ON refresh_tokens (token_hash);
CREATE INDEX idx_refresh_tokens_active ON refresh_tokens (expires_at)
    WHERE revoked = FALSE;

-- 自动清理过期令牌
CREATE OR REPLACE FUNCTION cleanup_refresh_tokens()
RETURNS void AS $$
BEGIN
    DELETE FROM refresh_tokens WHERE expires_at < NOW() OR revoked = TRUE;
END;
$$ LANGUAGE plpgsql;

-- ============================================================================
-- 10. 云存储配置表 (cloud_storage_configs)
--     用户可配置多个云存储账号（S3兼容存储、WebDAV云盘等）
-- ============================================================================
CREATE TABLE cloud_storage_configs (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID            NOT NULL,
    name                VARCHAR(100)    NOT NULL,
    provider_type       VARCHAR(20)     NOT NULL,
    endpoint_url        VARCHAR(500),
    access_key          VARCHAR(256),
    secret_key          VARCHAR(512),
    bucket_name         VARCHAR(100),
    region              VARCHAR(50),
    is_active           BOOLEAN         NOT NULL DEFAULT FALSE,
    last_test_success   BOOLEAN,
    last_test_at        TIMESTAMPTZ,
    extra_config        TEXT,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    -- 约束
    CONSTRAINT ck_cloud_storage_provider CHECK (provider_type IN ('S3', 'WEBDAV')),

    -- 外键
    CONSTRAINT fk_cloud_storage_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 云存储配置索引
CREATE INDEX idx_cloud_storage_user_id ON cloud_storage_configs (user_id);
CREATE INDEX idx_cloud_storage_provider ON cloud_storage_configs (provider_type);
CREATE UNIQUE INDEX idx_cloud_storage_user_provider_name
    ON cloud_storage_configs (user_id, provider_type, name);

-- 更新触发器
CREATE TRIGGER trg_cloud_storage_updated_at
    BEFORE UPDATE ON cloud_storage_configs
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at();

-- ============================================================================
-- 11. 定时清理任务 (pg_cron 可选)
-- ============================================================================
-- 如果安装了 pg_cron 扩展，可以启用自动清理
-- SELECT cron.schedule('cleanup-ai-logs', '0 3 * * *', 'SELECT cleanup_ai_call_logs()');
-- SELECT cron.schedule('cleanup-audit-logs', '0 4 * * *', 'SELECT cleanup_audit_logs()');
-- SELECT cron.schedule('cleanup-tokens', '0 5 * * *', 'SELECT cleanup_refresh_tokens()');

-- ============================================================================
-- 11. 数据库注释
-- ============================================================================
COMMENT ON TABLE users IS '用户表 - 存储用户账号信息';
COMMENT ON TABLE files IS '文件表 - 存储文件元数据和AI分析结果';
COMMENT ON TABLE file_contents IS '文件内容分片表 - 存储解析后的文本内容，用于全文检索和RAG';
COMMENT ON TABLE file_versions IS '文件版本表 - 存储文件版本历史';
COMMENT ON TABLE permissions IS '权限表 - 文件共享权限控制';
COMMENT ON TABLE semantic_indexes IS '语义索引表 - 用于RAG检索的分片文本和向量数据';
COMMENT ON TABLE ai_call_logs IS 'AI调用日志表 - 记录大模型调用记录(保留90天)';
COMMENT ON TABLE audit_logs IS '审计日志表 - 记录安全敏感操作(保留180天)';
COMMENT ON TABLE refresh_tokens IS '刷新令牌表 - 管理JWT Refresh Token';
COMMENT ON TABLE cloud_storage_configs IS '云存储配置表 - 用户配置的云存储账号（S3/WebDAV等）';

COMMENT ON COLUMN files.hash IS 'SHA-256文件哈希，用于重复检测';
COMMENT ON COLUMN files.tags IS 'AI生成的标签数组，JSONB格式';
COMMENT ON COLUMN files.category IS 'AI分类结果';
COMMENT ON COLUMN files.summary IS 'AI生成的文档摘要';
COMMENT ON COLUMN files.sensitive_level IS '敏感等级：NORMAL/LOW/MEDIUM/HIGH';
COMMENT ON COLUMN files.ai_status IS 'AI处理状态：PENDING/PROCESSING/COMPLETED/FAILED/SKIPPED';
COMMENT ON COLUMN files.cloud_drive_file_id IS '云盘/OSS文件原始ID，用于S3/WebDAV外部存储导入去重';
COMMENT ON COLUMN files.encryption_mode IS '加密模式：NONE/SERVER/CLIENT';
COMMENT ON COLUMN cloud_storage_configs.provider_type IS '存储类型：S3（兼容存储）、WEBDAV（WebDAV云盘）';
COMMENT ON COLUMN file_contents.chunk_metadata IS '分片元数据，如{"page":5,"section":"第三章"}';
COMMENT ON COLUMN semantic_indexes.metadata IS '分片元数据，如{"page":5,"section":"第三章","title":"标题"}';
COMMENT ON COLUMN ai_call_logs.prompt IS 'Prompt内容(脱敏后)，生产环境建议不存储原始Prompt';
COMMENT ON COLUMN ai_call_logs.response IS '模型响应(脱敏后)，生产环境建议不存储完整响应';
COMMENT ON COLUMN audit_logs.details IS '操作详情JSON，如{"fileId":"uuid","oldPermission":"read","newPermission":"write"}';

-- ============================================================================
-- 结尾
-- ============================================================================
