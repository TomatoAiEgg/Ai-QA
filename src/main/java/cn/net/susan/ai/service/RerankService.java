package cn.net.susan.ai.service;

import com.alibaba.dashscope.aio.rerank.Rerank;
import com.alibaba.dashscope.aio.rerank.RerankParam;
import com.alibaba.dashscope.aio.rerank.RerankResult;
import com.alibaba.dashscope.aio.rerank.RerankScore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Rerank 重排序服务
 * 使用阿里百炼 Rerank API 对检索结果进行重排序，提高检索精度
 *
 * @author 苏三
 * @date 2026/3/15
 */
@Slf4j
@Service
public class RerankService {

    private final String apiKey;
    private final String model;
    private final double minScore;
    private final int topN;

    public RerankService(
            @Value("${spring.ai.openai.api-key}") String apiKey,
            @Value("${ai.service.rerank.model:gte-rerank}") String model,
            @Value("${ai.service.rerank.min-score:0.5}") double minScore,
            @Value("${ai.service.rerank.top-n:3}") int topN) {
        this.apiKey = apiKey;
        this.model = model;
        this.minScore = minScore;
        this.topN = topN;
    }

    /**
     * 对检索结果进行重排序
     *
     * @param query   查询问题
     * @param documents 待重排序的文档列表
     * @return 重排序后的文档列表（按相关性降序）
     */
    public List<Document> rerank(String query, List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            log.debug("文档列表为空，跳过重排序");
            return documents;
        }

        if (documents.size() == 1) {
            log.debug("只有一个文档，跳过重排序");
            return documents;
        }

        log.info("开始 Rerank 重排序，查询：{}，文档数：{}", query, documents.size());

        try {
            // 1. 提取文档内容
            List<String> contents = documents.stream()
                    .map(Document::getContent)
                    .toList();

            // 2. 调用 Rerank API
            Rerank rerank = new Rerank();
            RerankParam param = RerankParam.builder()
                    .apiKey(apiKey)
                    .model(model)
                    .query(query)
                    .documents(contents)
                    .build();

            RerankResult result = rerank.call(param);

            // 3. 解析 Rerank 结果
            List<RerankScore> scores = result.getOut().getScores();
            log.info("Rerank 完成，原始文档数：{}，返回评分数：{}", documents.size(), scores.size());

            // 4. 将评分添加到文档元数据
            List<Document> scoredDocuments = new ArrayList<>();
            for (int i = 0; i < scores.size(); i++) {
                RerankScore score = scores.get(i);
                Document doc = documents.get(score.getIndex());
                
                // 添加 Rerank 评分到元数据
                doc.getMetadata().put("rerank_score", score.getScore());
                doc.getMetadata().put("rerank_index", i);
                
                scoredDocuments.add(doc);
            }

            // 5. 过滤低分文档
            List<Document> filteredDocuments = scoredDocuments.stream()
                    .filter(doc -> {
                        double score = (double) doc.getMetadata().getOrDefault("rerank_score", 0.0);
                        return score >= minScore;
                    })
                    .toList();

            log.info("过滤低分文档后，剩余文档数：{} (阈值：{})", filteredDocuments.size(), minScore);

            // 6. 按 Rerank 评分降序排序
            filteredDocuments.sort(Comparator.comparingDouble(doc -> {
                double score = (double) doc.getMetadata().getOrDefault("rerank_score", 0.0);
                return -score; // 降序
            }));

            // 7. 返回 Top-N 文档
            List<Document> topDocuments = filteredDocuments.stream()
                    .limit(topN)
                    .toList();

            log.info("Rerank 重排序完成，返回 Top-{} 文档", topDocuments.size());
            
            // 打印评分日志
            for (int i = 0; i < topDocuments.size(); i++) {
                Document doc = topDocuments.get(i);
                double score = (double) doc.getMetadata().getOrDefault("rerank_score", 0.0);
                String contentSnippet = doc.getContent().length() > 50 ?
                        doc.getContent().substring(0, 50) + "..." : doc.getContent();
                log.info("Top-{} 评分：{:.4f}, 内容：{}", i + 1, score, contentSnippet);
            }

            return topDocuments;

        } catch (Exception e) {
            log.error("Rerank 重排序失败，返回原始文档列表", e);
            // 失败时返回原始文档列表，不影响正常流程
            return documents;
        }
    }

    /**
     * 检查 Rerank 是否启用
     */
    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }
}
