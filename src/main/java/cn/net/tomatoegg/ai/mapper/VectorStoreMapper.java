package cn.net.tomatoegg.ai.mapper;

import cn.net.tomatoegg.ai.entity.VectorStoreChunk;
import cn.net.tomatoegg.ai.mapper.param.VectorStoreChunkInsertParam;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

@Mapper
public interface VectorStoreMapper {

    @Insert("INSERT INTO vector_store_1024_v2 "
            + "(id, user_id, kb_id, doc_id, chunk_index, content, metadata, embedding) "
            + "VALUES "
            + "(#{id}, #{userId}, #{kbId}, #{docId}, #{chunkIndex}, #{content}, CAST(#{metadataJson} AS jsonb), CAST(#{embedding} AS vector))")
    void insertChunk(@Param("id") UUID id,
                     @Param("userId") UUID userId,
                     @Param("kbId") UUID kbId,
                     @Param("docId") UUID docId,
                     @Param("chunkIndex") Integer chunkIndex,
                     @Param("content") String content,
                     @Param("metadataJson") String metadataJson,
                     @Param("embedding") String embedding);

    @Insert({
            "<script>",
            "INSERT INTO vector_store_1024_v2 ",
            "(id, user_id, kb_id, doc_id, chunk_index, content, metadata, embedding) ",
            "VALUES ",
            "<foreach collection='items' item='item' separator=','>",
            "(#{item.id}, #{item.userId}, #{item.kbId}, #{item.docId}, #{item.chunkIndex}, ",
            "#{item.content}, CAST(#{item.metadataJson} AS jsonb), CAST(#{item.embedding} AS vector))",
            "</foreach>",
            "</script>"
    })
    void batchInsertChunks(@Param("items") List<VectorStoreChunkInsertParam> items);

    @Select("SELECT content, metadata::text AS metadata_json, "
            + "1 - (embedding <=> CAST(#{embedding} AS vector)) AS score "
            + "FROM vector_store_1024_v2 "
            + "WHERE kb_id = #{kbId} "
            + "ORDER BY embedding <=> CAST(#{embedding} AS vector) "
            + "LIMIT #{topK}")
    @Results({
            @Result(column = "content", property = "content"),
            @Result(column = "metadata_json", property = "metadataJson"),
            @Result(column = "score", property = "score")
    })
    List<VectorStoreChunk> searchByKnowledgeBaseId(@Param("kbId") UUID kbId,
                                                   @Param("embedding") String embedding,
                                                   @Param("topK") int topK);

    @Select("SELECT content, metadata::text AS metadata_json, "
            + "1 - (embedding <=> CAST(#{embedding} AS vector)) AS score "
            + "FROM vector_store_1024_v2 "
            + "WHERE user_id = #{userId} "
            + "ORDER BY embedding <=> CAST(#{embedding} AS vector) "
            + "LIMIT #{topK}")
    @Results({
            @Result(column = "content", property = "content"),
            @Result(column = "metadata_json", property = "metadataJson"),
            @Result(column = "score", property = "score")
    })
    List<VectorStoreChunk> searchByUserId(@Param("userId") UUID userId,
                                          @Param("embedding") String embedding,
                                          @Param("topK") int topK);

    @Delete("DELETE FROM vector_store_1024_v2 WHERE kb_id = #{kbId}")
    void deleteByKnowledgeBaseId(@Param("kbId") UUID kbId);

    @Delete("DELETE FROM vector_store_1024_v2 WHERE doc_id = #{docId}")
    void deleteByDocumentId(@Param("docId") UUID docId);
}
