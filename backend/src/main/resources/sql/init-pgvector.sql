-- 在 zeno_agent 数据库中启用 pgvector 扩展
-- 此脚本由 PostgreSQL Docker 容器在首次启动时通过 /docker-entrypoint-initdb.d/ 自动执行
CREATE EXTENSION IF NOT EXISTS vector;
