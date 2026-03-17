-- ========================================
-- AI-QA 知识库管理数据库迁移脚本
-- 版本：v2.0.0
-- 日期：2026-03-17
-- ========================================

-- 1. 创建知识库表
CREATE TABLE IF NOT EXISTS knowledge_bases (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    cover_color VARCHAR(7) DEFAULT '#4F46E5',
    created_by UUID,
    is_public BOOLEAN DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- 2. 创建索引
CREATE INDEX IF NOT EXISTS idx_knowledge_bases_created_at ON knowledge_bases(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_knowledge_bases_created_by ON knowledge_bases(created_by);

-- 3. 添加注释
COMMENT ON TABLE knowledge_bases IS '知识库表';
COMMENT ON COLUMN knowledge_bases.id IS '知识库 ID';
COMMENT ON COLUMN knowledge_bases.name IS '知识库名称';
COMMENT ON COLUMN knowledge_bases.description IS '知识库描述';
COMMENT ON COLUMN knowledge_bases.cover_color IS '封面颜色';
COMMENT ON COLUMN knowledge_bases.created_by IS '创建者 ID';
COMMENT ON COLUMN knowledge_bases.is_public IS '是否公开';

-- 4. 插入默认知识库（如果需要一个默认库）
-- INSERT INTO knowledge_bases (id, name, description, cover_color, is_public)
-- VALUES ('00000000-0000-0000-0000-000000000001', '默认知识库', '系统默认知识库', '#4F46E5', true);

-- 5. 查看创建结果
SELECT tablename FROM pg_tables WHERE tablename = 'knowledge_bases';
