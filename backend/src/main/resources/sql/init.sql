-- AI Agent 数据库初始化脚本
-- 创建日期: 2025-01-03

-- 创建数据库
CREATE DATABASE IF NOT EXISTS zeno_agent DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE zeno_agent;

-- 会话表
CREATE TABLE IF NOT EXISTS `agent_conversation` (
  `id` VARCHAR(64) PRIMARY KEY COMMENT '会话ID（UUID）',
  `title` VARCHAR(255) NOT NULL DEFAULT '新对话' COMMENT '会话标题',
  `user_id` VARCHAR(64) DEFAULT NULL COMMENT '用户ID（预留）',
  `model_id` VARCHAR(64) DEFAULT NULL COMMENT '使用的模型ID',
  `model_name` VARCHAR(128) DEFAULT NULL COMMENT '模型名称',
  `status` VARCHAR(32) NOT NULL DEFAULT 'active' COMMENT '状态：active/archived/deleted',
  `message_count` INT NOT NULL DEFAULT 0 COMMENT '消息数量',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  INDEX `idx_user_id` (`user_id`),
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

