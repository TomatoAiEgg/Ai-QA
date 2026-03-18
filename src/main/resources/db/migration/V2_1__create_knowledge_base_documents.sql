-- ========================================
-- AI-QA 知识库文档关联表迁移脚本
-- 版本：v2.1.0
-- 日期：2026-03-17
-- ========================================

-- 1. 创建知识库文档关联表
CREATE TABLE IF NOT EXISTS knowledge_base_documents (
    id UUID PRIMARY KEY,
    kb_id UUID NOT NULL,
    filename VARCHAR(255) NOT NULL,
    file_hash VARCHAR(64),
    file_size BIGINT,
    chunk_count INTEGER DEFAULT 0,
    status VARCHAR(20) DEFAULT 'PROCESSING',
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT fk_kb FOREIGN KEY (kb_id) REFERENCES knowledge_bases(id) ON DELETE CASCADE
);

-- 2. 创建索引
CREATE INDEX IF NOT EXISTS idx_kb_documents_kb_id ON knowledge_base_documents(kb_id);
CREATE INDEX IF NOT EXISTS idx_kb_documents_status ON knowledge_base_documents(status);
CREATE INDEX IF NOT EXISTS idx_kb_documents_created_at ON knowledge_base_documents(created_at DESC);

-- 3. 添加注释
COMMENT ON TABLE knowledge_base_documents IS '知识库文档关联表';
COMMENT ON COLUMN knowledge_base_documents.id IS '记录 ID';
COMMENT ON COLUMN knowledge_base_documents.kb_id IS '知识库 ID';
COMMENT ON COLUMN knowledge_base_documents.filename IS '文件名';
COMMENT ON COLUMN knowledge_base_documents.file_hash IS '文件哈希值 (用于去重)';
COMMENT ON COLUMN knowledge_base_documents.file_size IS '文件大小 (字节)';
COMMENT ON COLUMN knowledge_base_documents.chunk_count IS '切片数量';
COMMENT ON COLUMN knowledge_base_documents.status IS '处理状态：PROCESSING, COMPLETED, FAILED';
COMMENT ON COLUMN knowledge_base_documents.error_message IS '错误信息';
