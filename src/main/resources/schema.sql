CREATE TABLE IF NOT EXISTS conversations (
  id UUID PRIMARY KEY,
  title VARCHAR(255) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS conversation_messages (
  id UUID PRIMARY KEY,
  conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
  role VARCHAR(20) NOT NULL,
  content TEXT NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_conversation_messages_conv_time
  ON conversation_messages(conversation_id, created_at);

CREATE TABLE IF NOT EXISTS knowledge_bases (
  id UUID PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  description TEXT,
  cover_color VARCHAR(32),
  created_by UUID,
  is_public BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_knowledge_bases_created_at
  ON knowledge_bases(created_at DESC);

CREATE TABLE IF NOT EXISTS knowledge_base_documents (
  id UUID PRIMARY KEY,
  kb_id UUID REFERENCES knowledge_bases(id) ON DELETE CASCADE,
  filename VARCHAR(512) NOT NULL,
  storage_path TEXT,
  file_hash VARCHAR(128),
  file_size BIGINT,
  chunk_count INTEGER NOT NULL DEFAULT 0,
  status VARCHAR(32) NOT NULL,
  error_message TEXT,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

ALTER TABLE knowledge_base_documents ADD COLUMN IF NOT EXISTS storage_path TEXT;

CREATE INDEX IF NOT EXISTS idx_kb_documents_kb_id
  ON knowledge_base_documents(kb_id);

CREATE INDEX IF NOT EXISTS idx_kb_documents_created_at
  ON knowledge_base_documents(created_at DESC);
