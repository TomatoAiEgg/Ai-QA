package cn.net.tomatoegg.ai.service.knowledgebase;

import cn.net.tomatoegg.ai.common.ApiCode;
import cn.net.tomatoegg.ai.entity.KnowledgeBase;
import cn.net.tomatoegg.ai.entity.VectorStoreChunk;
import cn.net.tomatoegg.ai.exception.BusinessException;
import cn.net.tomatoegg.ai.mapper.KnowledgeBaseMapper;
import cn.net.tomatoegg.ai.mapper.VectorStoreMapper;
import cn.net.tomatoegg.ai.service.embedding.DashScopeEmbeddingService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class KnowledgeBaseSearchService {

    private final DashScopeEmbeddingService embeddingService;
    private final ObjectMapper objectMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final VectorStoreMapper vectorStoreMapper;

    public KnowledgeBaseSearchService(DashScopeEmbeddingService embeddingService,
                                      ObjectMapper objectMapper,
                                      KnowledgeBaseMapper knowledgeBaseMapper,
                                      VectorStoreMapper vectorStoreMapper) {
        this.embeddingService = embeddingService;
        this.objectMapper = objectMapper;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.vectorStoreMapper = vectorStoreMapper;
    }

    public List<Document> searchFromKnowledgeBase(String query, UUID kbId, int topK, UUID userId) {
        String queryEmbedding = toVectorLiteral(embeddingService.embed(query));
        List<VectorStoreChunk> chunks;
        if (kbId != null) {
            requireOwnedKnowledgeBase(userId, kbId);
            chunks = vectorStoreMapper.searchByKnowledgeBaseId(kbId, queryEmbedding, topK);
            log.info("RAG 检索完成, mode=single-kb, userId={}, kbId={}, topK={}, hitCount={}", userId, kbId, topK, chunks.size());
        } else {
            chunks = vectorStoreMapper.searchByUserId(userId, queryEmbedding, topK);
            log.info("RAG 检索完成, mode=all-kb, userId={}, topK={}, hitCount={}", userId, topK, chunks.size());
        }
        return chunks.stream()
                .map(this::toDocument)
                .collect(Collectors.toList());
    }

    private String toVectorLiteral(float[] embedding) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(embedding[i]);
        }
        builder.append(']');
        return builder.toString();
    }

    private Document toDocument(VectorStoreChunk chunk) {
        try {
            Map<String, Object> metadata = objectMapper.readValue(chunk.getMetadataJson(), new TypeReference<>() {});
            metadata.put("score", chunk.getScore());
            return new Document(chunk.getContent(), metadata);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ApiCode.SERVER_ERROR, "解析向量元数据失败", ex);
        }
    }

    private KnowledgeBase requireOwnedKnowledgeBase(UUID userId, UUID kbId) {
        KnowledgeBase knowledgeBase = knowledgeBaseMapper.selectOne(new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getId, kbId)
                .eq(KnowledgeBase::getCreatedBy, userId)
                .last("LIMIT 1"));
        if (knowledgeBase == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "知识库不存在或无权访问");
        }
        return knowledgeBase;
    }
}
