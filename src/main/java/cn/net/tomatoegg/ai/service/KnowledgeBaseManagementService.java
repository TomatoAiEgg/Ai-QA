package cn.net.tomatoegg.ai.service;

import cn.net.tomatoegg.ai.common.ApiCode;
import cn.net.tomatoegg.ai.entity.KnowledgeBase;
import cn.net.tomatoegg.ai.entity.KnowledgeBaseDocument;
import cn.net.tomatoegg.ai.exception.BusinessException;
import cn.net.tomatoegg.ai.mapper.KnowledgeBaseDocumentMapper;
import cn.net.tomatoegg.ai.mapper.KnowledgeBaseMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseManagementService {

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeBaseDocumentMapper documentMapper;
    private final KnowledgeBaseService knowledgeBaseService;

    @Caching(evict = {
            @CacheEvict(value = "knowledgeBaseListCache", key = "#userId.toString()"),
            @CacheEvict(value = "knowledgeBaseDetailCache", allEntries = true),
            @CacheEvict(value = "knowledgeBaseDocumentsCache", allEntries = true),
            @CacheEvict(value = "documentListCache", allEntries = true),
            @CacheEvict(value = "documentPreviewCache", allEntries = true)
    })
    public KnowledgeBase createKnowledgeBase(UUID userId, String name, String description) {
        KnowledgeBase kb = KnowledgeBase.builder()
                .id(UUID.randomUUID())
                .name(name)
                .description(description)
                .coverColor("#4F46E5")
                .createdBy(userId)
                .isPublic(false)
                .build();
        knowledgeBaseMapper.insert(kb);
        log.info("创建知识库成功, userId={}, kbId={}, name={}", userId, kb.getId(), kb.getName());
        return kb;
    }

    @Cacheable(value = "knowledgeBaseListCache", key = "#userId.toString()")
    public List<KnowledgeBase> listKnowledgeBases(UUID userId) {
        List<KnowledgeBase> knowledgeBases = knowledgeBaseMapper.selectList(
                new LambdaQueryWrapper<KnowledgeBase>()
                        .eq(KnowledgeBase::getCreatedBy, userId)
                        .orderByDesc(KnowledgeBase::getCreatedAt)
        );

        Map<UUID, Integer> documentCounts = documentMapper.selectList(
                        new LambdaQueryWrapper<KnowledgeBaseDocument>()
                                .eq(KnowledgeBaseDocument::getUserId, userId)
                                .isNotNull(KnowledgeBaseDocument::getKbId))
                .stream()
                .collect(Collectors.toMap(KnowledgeBaseDocument::getKbId, item -> 1, Integer::sum));

        knowledgeBases.forEach(kb -> kb.setDocumentCount(documentCounts.getOrDefault(kb.getId(), 0)));
        return knowledgeBases;
    }

    @Cacheable(value = "knowledgeBaseDetailCache", key = "#userId.toString() + ':' + #id.toString()")
    public KnowledgeBase getKnowledgeBase(UUID userId, UUID id) {
        KnowledgeBase knowledgeBase = requireOwnedKnowledgeBase(userId, id);
        Long documentCount = documentMapper.selectCount(
                new LambdaQueryWrapper<KnowledgeBaseDocument>()
                        .eq(KnowledgeBaseDocument::getUserId, userId)
                        .eq(KnowledgeBaseDocument::getKbId, id)
        );
        knowledgeBase.setDocumentCount(documentCount == null ? 0 : documentCount.intValue());
        return knowledgeBase;
    }

    @Caching(evict = {
            @CacheEvict(value = "knowledgeBaseListCache", key = "#userId.toString()"),
            @CacheEvict(value = "knowledgeBaseDetailCache", allEntries = true),
            @CacheEvict(value = "knowledgeBaseDocumentsCache", allEntries = true),
            @CacheEvict(value = "documentListCache", allEntries = true)
    })
    public KnowledgeBase updateKnowledgeBase(UUID userId, UUID id, String name, String description) {
        requireOwnedKnowledgeBase(userId, id);
        knowledgeBaseMapper.update(null, new LambdaUpdateWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getId, id)
                .eq(KnowledgeBase::getCreatedBy, userId)
                .set(KnowledgeBase::getName, name)
                .set(KnowledgeBase::getDescription, description)
                .setSql("updated_at = NOW()"));
        log.info("更新知识库成功, userId={}, kbId={}, name={}", userId, id, name);
        return getKnowledgeBase(userId, id);
    }

    @Caching(evict = {
            @CacheEvict(value = "knowledgeBaseListCache", key = "#userId.toString()"),
            @CacheEvict(value = "knowledgeBaseDetailCache", allEntries = true),
            @CacheEvict(value = "knowledgeBaseDocumentsCache", allEntries = true),
            @CacheEvict(value = "documentListCache", allEntries = true),
            @CacheEvict(value = "documentPreviewCache", allEntries = true)
    })
    public void deleteKnowledgeBase(UUID userId, UUID id) {
        KnowledgeBase knowledgeBase = requireOwnedKnowledgeBase(userId, id);
        long documentCount = documentMapper.selectCount(new LambdaQueryWrapper<KnowledgeBaseDocument>()
                .eq(KnowledgeBaseDocument::getUserId, userId)
                .eq(KnowledgeBaseDocument::getKbId, id));
        knowledgeBaseService.deleteKnowledgeBaseVectors(id);
        documentMapper.selectList(new LambdaQueryWrapper<KnowledgeBaseDocument>()
                        .eq(KnowledgeBaseDocument::getUserId, userId)
                        .eq(KnowledgeBaseDocument::getKbId, id))
                .forEach(doc -> knowledgeBaseService.deleteStoredFile(doc.getStoragePath()));
        documentMapper.delete(new LambdaQueryWrapper<KnowledgeBaseDocument>()
                .eq(KnowledgeBaseDocument::getUserId, userId)
                .eq(KnowledgeBaseDocument::getKbId, id));
        knowledgeBaseMapper.delete(new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getId, id)
                .eq(KnowledgeBase::getCreatedBy, userId));
        log.info("删除知识库成功, userId={}, kbId={}, name={}, documentCount={}", userId, id, knowledgeBase.getName(), documentCount);
    }

    @Cacheable(value = "knowledgeBaseDocumentsCache", key = "#userId.toString() + ':' + #kbId.toString()")
    public List<KnowledgeBaseDocument> listDocuments(UUID userId, UUID kbId) {
        requireOwnedKnowledgeBase(userId, kbId);
        return documentMapper.selectList(new LambdaQueryWrapper<KnowledgeBaseDocument>()
                .eq(KnowledgeBaseDocument::getUserId, userId)
                .eq(KnowledgeBaseDocument::getKbId, kbId)
                .orderByDesc(KnowledgeBaseDocument::getCreatedAt));
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
