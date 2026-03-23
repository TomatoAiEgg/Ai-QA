package cn.net.tomatoegg.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import cn.net.tomatoegg.ai.entity.KnowledgeBase;
import cn.net.tomatoegg.ai.entity.KnowledgeBaseDocument;
import cn.net.tomatoegg.ai.mapper.KnowledgeBaseDocumentMapper;
import cn.net.tomatoegg.ai.mapper.KnowledgeBaseMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    public KnowledgeBase createKnowledgeBase(String name, String description) {
        KnowledgeBase kb = KnowledgeBase.builder()
                .id(UUID.randomUUID())
                .name(name)
                .description(description)
                .coverColor("#4F46E5")
                .isPublic(false)
                .build();
        knowledgeBaseMapper.insert(kb);
        log.info("创建知识库: {} ({})", kb.getName(), kb.getId());
        return kb;
    }

    public List<KnowledgeBase> listKnowledgeBases() {
        List<KnowledgeBase> knowledgeBases = knowledgeBaseMapper.selectList(
                new LambdaQueryWrapper<KnowledgeBase>().orderByDesc(KnowledgeBase::getCreatedAt)
        );

        Map<UUID, Integer> documentCounts = documentMapper.selectList(
                        new LambdaQueryWrapper<KnowledgeBaseDocument>().isNotNull(KnowledgeBaseDocument::getKbId))
                .stream()
                .collect(Collectors.toMap(KnowledgeBaseDocument::getKbId, item -> 1, Integer::sum));

        knowledgeBases.forEach(kb -> kb.setDocumentCount(documentCounts.getOrDefault(kb.getId(), 0)));
        return knowledgeBases;
    }

    public KnowledgeBase getKnowledgeBase(UUID id) {
        KnowledgeBase knowledgeBase = knowledgeBaseMapper.selectById(id);
        if (knowledgeBase == null) {
            throw new RuntimeException("知识库不存在: " + id);
        }
        Long documentCount = documentMapper.selectCount(
                new LambdaQueryWrapper<KnowledgeBaseDocument>().eq(KnowledgeBaseDocument::getKbId, id)
        );
        knowledgeBase.setDocumentCount(documentCount == null ? 0 : documentCount.intValue());
        return knowledgeBase;
    }

    public KnowledgeBase updateKnowledgeBase(UUID id, String name, String description) {
        knowledgeBaseMapper.update(null, new LambdaUpdateWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getId, id)
                .set(KnowledgeBase::getName, name)
                .set(KnowledgeBase::getDescription, description)
                .setSql("updated_at = NOW()"));
        return getKnowledgeBase(id);
    }

    public void deleteKnowledgeBase(UUID id) {
        knowledgeBaseService.deleteKnowledgeBaseVectors(id);
        documentMapper.selectList(new LambdaQueryWrapper<KnowledgeBaseDocument>()
                        .eq(KnowledgeBaseDocument::getKbId, id))
                .forEach(doc -> knowledgeBaseService.deleteStoredFile(doc.getStoragePath()));
        documentMapper.delete(new LambdaQueryWrapper<KnowledgeBaseDocument>()
                .eq(KnowledgeBaseDocument::getKbId, id));
        knowledgeBaseMapper.deleteById(id);
        log.info("删除知识库: {}", id);
    }

    public List<KnowledgeBaseDocument> listDocuments(UUID kbId) {
        return documentMapper.selectList(new LambdaQueryWrapper<KnowledgeBaseDocument>()
                .eq(KnowledgeBaseDocument::getKbId, kbId)
                .orderByDesc(KnowledgeBaseDocument::getCreatedAt));
    }
}
