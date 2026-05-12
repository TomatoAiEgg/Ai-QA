package cn.net.tomatoegg.ai.mapper;

import cn.net.tomatoegg.ai.entity.VectorStoreChunk;
import cn.net.tomatoegg.ai.mapper.param.VectorStoreChunkInsertParam;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface VectorStoreMapper extends BaseMapper<VectorStoreChunk> {

    void insertChunk(@Param("id") UUID id,
                     @Param("userId") UUID userId,
                     @Param("kbId") UUID kbId,
                     @Param("docId") UUID docId,
                     @Param("chunkIndex") Integer chunkIndex,
                     @Param("content") String content,
                     @Param("metadataJson") String metadataJson,
                     @Param("embedding") String embedding);

    void batchInsertChunks(@Param("items") List<VectorStoreChunkInsertParam> items);

    List<VectorStoreChunk> searchByKnowledgeBaseId(@Param("kbId") UUID kbId,
                                                    @Param("embedding") String embedding,
                                                    @Param("topK") int topK);

    List<VectorStoreChunk> searchByUserId(@Param("userId") UUID userId,
                                           @Param("embedding") String embedding,
                                           @Param("topK") int topK);

    void deleteByKnowledgeBaseId(@Param("kbId") UUID kbId);

    void deleteByDocumentId(@Param("docId") UUID docId);
}
