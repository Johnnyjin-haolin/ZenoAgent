-- AI Agent 数据库初始化脚本
-- 创建日期: 2025-01-03

-- 创建数据库
CREATE DATABASE IF NOT EXISTS zeno_agent DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE zeno_agent;

-- Agent 定义表（用户自定义 Agent 配置，先建以便会话表外键引用）
CREATE TABLE IF NOT EXISTS `agent` (
  `id`           VARCHAR(64)   PRIMARY KEY                COMMENT 'Agent ID（UUID 或 builtin-xxx）',
  `name`         VARCHAR(128)  NOT NULL                   COMMENT 'Agent 名称',
  `description`  VARCHAR(512)  DEFAULT NULL               COMMENT 'Agent 描述',
  `system_prompt` TEXT         DEFAULT NULL               COMMENT '系统提示词',
  `tools_config`   JSON          DEFAULT NULL               COMMENT '工具配置 JSON，包含 mcpGroups / systemTools / knowledgeIds',
  `context_config` JSON          DEFAULT NULL               COMMENT '上下文行为配置 JSON，包含 historyMessageLoadLimit / maxToolRounds',
  `rag_config`     JSON          DEFAULT NULL               COMMENT 'RAG 检索配置 JSON，包含 maxResults / minScore / maxDocumentLength',
  `skill_tree`     JSON          DEFAULT NULL               COMMENT 'Agent 私有 Skill 目录树 JSON，结构为 SkillTreeNode[]',
  `is_builtin`   TINYINT(1)    NOT NULL DEFAULT 0         COMMENT '是否内置（1=内置示例, 0=用户创建）',
  `status`       VARCHAR(16)   NOT NULL DEFAULT 'active'  COMMENT '状态：active / deleted',
  `create_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  INDEX `idx_status`      (`status`),
  INDEX `idx_is_builtin`  (`is_builtin`),
  INDEX `idx_update_time` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 定义表';

-- 会话表
CREATE TABLE IF NOT EXISTS `agent_conversation` (
  `id` VARCHAR(64) PRIMARY KEY COMMENT '会话ID（UUID）',
  `title` VARCHAR(255) NOT NULL DEFAULT '新对话' COMMENT '会话标题',
  `user_id` VARCHAR(64) DEFAULT NULL COMMENT '用户ID（预留）',
  `agent_id` VARCHAR(64) DEFAULT NULL COMMENT '绑定的 Agent ID',
  `model_id` VARCHAR(64) DEFAULT NULL COMMENT '使用的模型ID',
  `model_name` VARCHAR(128) DEFAULT NULL COMMENT '模型名称',
  `status` VARCHAR(32) NOT NULL DEFAULT 'active' COMMENT '状态：active/archived/deleted',
  `message_count` INT NOT NULL DEFAULT 0 COMMENT '消息数量',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_agent_id` (`agent_id`),
  INDEX `idx_status` (`status`),
  INDEX `idx_update_time` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent会话表';

-- 消息表
CREATE TABLE IF NOT EXISTS `agent_message` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '消息ID',
  `conversation_id` VARCHAR(64) NOT NULL COMMENT '会话ID',
  `message_id` VARCHAR(64) NOT NULL COMMENT '消息唯一标识（UUID）',
  `role` VARCHAR(32) NOT NULL COMMENT '角色：user/assistant/system',
  `content` TEXT NOT NULL COMMENT '消息内容',
  `model_id` VARCHAR(64) DEFAULT NULL COMMENT '使用的模型ID',
  `agent_id` VARCHAR(64) DEFAULT NULL COMMENT '执行此消息的 Agent ID（assistant 消息时有值）',
  `tokens` INT DEFAULT NULL COMMENT 'Token数量',
  `duration` INT DEFAULT NULL COMMENT '耗时（毫秒）',
  `metadata` JSON DEFAULT NULL COMMENT '元数据（工具调用、RAG结果等）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  INDEX `idx_conversation_id` (`conversation_id`),
  INDEX `idx_message_id` (`message_id`),
  INDEX `idx_create_time` (`create_time`),
  CONSTRAINT `fk_message_conversation` FOREIGN KEY (`conversation_id`) 
    REFERENCES `agent_conversation`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent消息表';

-- 知识库表
CREATE TABLE IF NOT EXISTS `knowledge_base` (
  `id` VARCHAR(64) PRIMARY KEY COMMENT '知识库ID（UUID）',
  `name` VARCHAR(255) NOT NULL COMMENT '知识库名称',
  `description` TEXT COMMENT '描述',
  `embedding_model_id` VARCHAR(128) NOT NULL COMMENT '向量模型ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  INDEX `idx_create_time` (`create_time`),
  INDEX `idx_update_time` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库表';

-- Skill 定义表
CREATE TABLE IF NOT EXISTS `agent_skill` (
  `id`          VARCHAR(64)   PRIMARY KEY  COMMENT 'Skill ID（UUID）',
  `name`        VARCHAR(128)  NOT NULL     COMMENT 'Skill 名称',
  `summary`     VARCHAR(512)  NOT NULL     COMMENT '摘要（注入 System Prompt 的一行描述）',
  `content`     LONGTEXT      NOT NULL     COMMENT 'Skill 全文（LLM 按需加载）',
  `tags`        JSON          DEFAULT NULL COMMENT '标签列表，如 ["代码","SQL"]',
  `status`      VARCHAR(16)   NOT NULL DEFAULT 'active'  COMMENT '状态：active / deleted',
  `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  INDEX `idx_status`      (`status`),
  INDEX `idx_update_time` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Skill 定义表';

-- 文档表
CREATE TABLE IF NOT EXISTS `document` (
  `id` VARCHAR(64) PRIMARY KEY COMMENT '文档ID（UUID）',
  `knowledge_base_id` VARCHAR(64) NOT NULL COMMENT '知识库ID',
  `title` VARCHAR(500) NOT NULL COMMENT '文档标题',
  `type` VARCHAR(32) NOT NULL COMMENT '文档类型：FILE/TEXT/WEB',
  `content` LONGTEXT COMMENT '文档内容（文本类型直接存储）',
  `metadata` JSON COMMENT '元数据（文件路径、文件大小等信息）',
  `status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/BUILDING/COMPLETE/FAILED',
  `failed_reason` TEXT COMMENT '失败原因（当status为FAILED时）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  INDEX `idx_knowledge_base_id` (`knowledge_base_id`),
  INDEX `idx_status` (`status`),
  INDEX `idx_type` (`type`),
  INDEX `idx_create_time` (`create_time`),
  CONSTRAINT `fk_document_knowledge_base` FOREIGN KEY (`knowledge_base_id`) 
    REFERENCES `knowledge_base`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档表';

-- MCP 服务器配置表（scope: 0=GLOBAL 服务端执行, 1=PERSONAL 客户端执行）
CREATE TABLE IF NOT EXISTS `mcp_server` (
  `id`              VARCHAR(64)   NOT NULL PRIMARY KEY                              COMMENT 'MCP 服务器ID（UUID）',
  `name`            VARCHAR(128)  NOT NULL                                          COMMENT '服务器名称',
  `description`     VARCHAR(512)  DEFAULT NULL                                      COMMENT '服务器描述',
  `scope`           TINYINT       NOT NULL DEFAULT 0                                COMMENT '作用域：0=GLOBAL（服务端执行），1=PERSONAL（客户端执行）',
  `owner_user_id`   VARCHAR(64)   DEFAULT NULL                                      COMMENT '所属用户ID（PERSONAL 时有效）',
  `connection_type` VARCHAR(32)   NOT NULL DEFAULT 'streamable-http'               COMMENT '连接类型',
  `endpoint_url`    VARCHAR(512)  NOT NULL                                          COMMENT '服务端点 URL',
  `auth_header`     TEXT          DEFAULT NULL                                      COMMENT '认证请求头（JSON）',
  `extra_headers`   TEXT          DEFAULT NULL                                      COMMENT '额外请求头（JSON）',
  `timeout_ms`      INT           NOT NULL DEFAULT 10000                            COMMENT '连接超时（毫秒）',
  `read_timeout_ms` INT           NOT NULL DEFAULT 30000                            COMMENT '读取超时（毫秒）',
  `retry_count`     INT           NOT NULL DEFAULT 3                                COMMENT '失败重试次数',
  `enabled`         TINYINT(1)    NOT NULL DEFAULT 1                                COMMENT '是否启用：1=启用，0=禁用',
  `created_by`      VARCHAR(64)   DEFAULT NULL                                      COMMENT '创建人用户ID',
  `create_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP               COMMENT '创建时间',
  `update_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  INDEX `idx_scope`         (`scope`),
  INDEX `idx_owner_user_id` (`owner_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MCP 服务器配置表';
