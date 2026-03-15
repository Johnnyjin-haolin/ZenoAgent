-- MCP 服务器配置表
-- scope: 0=GLOBAL(服务端执行), 1=PERSONAL(客户端执行)
CREATE TABLE IF NOT EXISTS mcp_server (
    id              VARCHAR(64)   NOT NULL PRIMARY KEY,
    name            VARCHAR(128)  NOT NULL,
    description     VARCHAR(512),
    scope           TINYINT       NOT NULL DEFAULT 0,
    owner_user_id   VARCHAR(64),
    capability      VARCHAR(64),
    connection_type VARCHAR(32)   NOT NULL DEFAULT 'streamable-http',
    endpoint_url    VARCHAR(512)  NOT NULL,
    auth_header     TEXT,
    extra_headers   TEXT,
    timeout_ms      INT           NOT NULL DEFAULT 10000,
    read_timeout_ms INT           NOT NULL DEFAULT 30000,
    retry_count     INT           NOT NULL DEFAULT 3,
    enabled         TINYINT(1)    NOT NULL DEFAULT 1,
    created_by      VARCHAR(64),
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_mcp_server_scope ON mcp_server (scope);
CREATE INDEX IF NOT EXISTS idx_mcp_server_owner ON mcp_server (owner_user_id);
CREATE INDEX IF NOT EXISTS idx_mcp_server_capability ON mcp_server (capability);
