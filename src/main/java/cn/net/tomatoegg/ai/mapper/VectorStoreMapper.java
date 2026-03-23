package cn.net.tomatoegg.ai.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface VectorStoreMapper {

    @Delete("DELETE FROM vector_store_1024 WHERE metadata->>'kb_id' = #{kbId}")
    void deleteByKnowledgeBaseId(String kbId);

    @Delete("DELETE FROM vector_store_1024 WHERE metadata->>'doc_id' = #{docId}")
    void deleteByDocumentId(String docId);
}
