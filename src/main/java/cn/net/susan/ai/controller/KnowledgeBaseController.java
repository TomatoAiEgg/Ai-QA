package cn.net.susan.ai.controller;

import cn.net.susan.ai.service.KnowledgeBaseManagementService;
import cn.net.susan.ai.entity.KnowledgeBase;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 知识库管理控制器
 *
 * @author 苏三
 * @date 2026/3/17
 */
@RestController
@RequestMapping("/api/knowledge-bases")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseManagementService kbService;

    /**
     * 创建知识库
     */
    @PostMapping
    public KnowledgeBase create(@RequestBody Map<String, String> request) {
        String name = request.get("name");
        String description = request.getOrDefault("description", "");
        return kbService.createKnowledgeBase(name, description);
    }

    /**
     * 获取知识库列表
     */
    @GetMapping
    public List<KnowledgeBase> list() {
        return kbService.listKnowledgeBases();
    }

    /**
     * 获取知识库详情
     */
    @GetMapping("/{id}")
    public KnowledgeBase get(@PathVariable UUID id) {
        return kbService.getKnowledgeBase(id);
    }

    /**
     * 更新知识库
     */
    @PutMapping("/{id}")
    public KnowledgeBase update(@PathVariable UUID id, @RequestBody Map<String, String> request) {
        String name = request.get("name");
        String description = request.get("description");
        return kbService.updateKnowledgeBase(id, name, description);
    }

    /**
     * 删除知识库
     */
    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        kbService.deleteKnowledgeBase(id);
    }
}
