package cn.net.susan.ai.service;

import cn.net.susan.ai.entity.KnowledgeBase;
import cn.net.susan.ai.repository.KnowledgeBaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 知识库服务
 *
 * @author 苏三
 * @date 2026/3/17
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseManagementService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;

    /**
     * 创建知识库
     */
    public KnowledgeBase createKnowledgeBase(String name, String description) {
        KnowledgeBase kb = KnowledgeBase.builder()
            .id(UUID.randomUUID())
            .name(name)
            .description(description)
            .coverColor("#4F46E5")
            .isPublic(false)
            .build();
        
        knowledgeBaseRepository.create(kb);
        log.info("创建知识库：{} ({})", kb.getName(), kb.getId());
        return kb;
    }

    /**
     * 获取所有知识库
     */
    public List<KnowledgeBase> listKnowledgeBases() {
        return knowledgeBaseRepository.findAll();
    }

    /**
     * 获取知识库详情
     */
    public KnowledgeBase getKnowledgeBase(UUID id) {
        return knowledgeBaseRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("知识库不存在：" + id));
    }

    /**
     * 更新知识库
     */
    public KnowledgeBase updateKnowledgeBase(UUID id, String name, String description) {
        KnowledgeBase kb = getKnowledgeBase(id);
        kb.setName(name);
        kb.setDescription(description);
        knowledgeBaseRepository.update(kb);
        log.info("更新知识库：{} ({})", kb.getName(), kb.getId());
        return kb;
    }

    /**
     * 删除知识库
     */
    public void deleteKnowledgeBase(UUID id) {
        knowledgeBaseRepository.delete(id);
        log.info("删除知识库：{}", id);
    }
}
