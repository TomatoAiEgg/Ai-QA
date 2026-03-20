package cn.net.tomatoegg.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
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
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    private static final String RERANK_URL = "https://dashscope.aliyuncs.com/api/v1/services/rerank/text-generation/rerank";

    public RerankService(
            @Value("${spring.ai.openai.api-key}") String apiKey,
            @Value("${ai.service.rerank.model:qwen3-rerank}") String model,
            @Value("${ai.service.rerank.min-score:0.5}") double minScore,
            @Value("${ai.service.rerank.top-n:3}") int topN) {
        this.apiKey = apiKey;
        this.model = model;
        this.minScore = minScore;
        this.topN = topN;
        this.httpClient = new OkHttpClient();
        this.objectMapper = new ObjectMapper();
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

            // 2. 构建请求 JSON
            String requestBody = buildRerankRequest(query, contents);

            // 3. 调用 Rerank API
            Request request = new Request.Builder()
                    .url(RERANK_URL)
                    .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                    throw new IOException("Rerank API 调用失败：" + response.code() + " - " + errorBody);
                }

                String responseBody = response.body().string();
                log.debug("Rerank API 响应：{}", responseBody);

                // 4. 解析响应
                List<Document> result = parseRerankResponse(responseBody, documents);

                log.info("Rerank 重排序完成，返回 {} 个文档", result.size());
                return result;
            }

        } catch (Exception e) {
            log.error("Rerank 重排序失败，返回原始文档列表", e);
            // 失败时返回原始文档列表，不影响正常流程
            return documents;
        }
    }

    /**
     * 构建 Rerank 请求 JSON
     */
    private String buildRerankRequest(String query, List<String> documents) throws IOException {
        // 构建 documents 数组
        List<Object> docsArray = new ArrayList<>();
        for (String content : documents) {
            docsArray.add(new TextDocument(content));
        }

        // 构建请求体
        RerankRequest request = new RerankRequest();
        request.model = this.model;
        request.input = new RerankInput(query, docsArray);
        request.parameters = new RerankParameters(topN);

        return objectMapper.writeValueAsString(request);
    }

    /**
     * 解析 Rerank 响应
     */
    private List<Document> parseRerankResponse(String responseBody, List<Document> originalDocuments) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode outputNode = root.path("output");
        JsonNode resultsNode = outputNode.path("results");

        if (!resultsNode.isArray()) {
            log.warn("Rerank 响应格式异常，返回原始文档");
            return originalDocuments;
        }

        // 解析结果并添加到文档元数据
        List<Document> scoredDocuments = new ArrayList<>();
        for (JsonNode resultNode : resultsNode) {
            int index = resultNode.path("index").asInt();
            double score = resultNode.path("relevance_score").asDouble();

            if (index >= 0 && index < originalDocuments.size()) {
                Document doc = originalDocuments.get(index);
                // 添加 Rerank 评分到元数据
                doc.getMetadata().put("rerank_score", score);
                doc.getMetadata().put("rerank_index", index);
                scoredDocuments.add(doc);
            }
        }

        // 过滤低分文档
        List<Document> filteredDocuments = scoredDocuments.stream()
                .filter(doc -> {
                    double score = (double) doc.getMetadata().getOrDefault("rerank_score", 0.0);
                    return score >= minScore;
                })
                .toList();

        log.info("过滤低分文档后，剩余文档数：{} (阈值：{})", filteredDocuments.size(), minScore);

        // 按 Rerank 评分降序排序（API 已排序，这里再确认一下）
        filteredDocuments.sort(Comparator.comparingDouble(doc -> {
            double score = (double) doc.getMetadata().getOrDefault("rerank_score", 0.0);
            return -score; // 降序
        }));

        // 返回 Top-N 文档
        return filteredDocuments.stream()
                .limit(topN)
                .toList();
    }

    /**
     * 检查 Rerank 是否启用
     */
    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    // 内部类：请求体结构
    private static class RerankRequest {
        public String model;
        public RerankInput input;
        public RerankParameters parameters;
    }

    private static class RerankInput {
        public String query;
        public List<Object> documents;

        public RerankInput(String query, List<Object> documents) {
            this.query = query;
            this.documents = documents;
        }
    }

    private static class TextDocument {
        public String text;

        public TextDocument(String text) {
            this.text = text;
        }
    }

    private static class RerankParameters {
        public int top_n;

        public RerankParameters(int topN) {
            this.top_n = topN;
        }
    }
}
