package cn.net.tomatoegg.ai.service.embedding;

import cn.net.tomatoegg.ai.service.model.ModelRouterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class DashScopeEmbeddingService {

    private final EmbeddingModel embeddingModel;
    private final ModelRouterService modelRouterService;

    public DashScopeEmbeddingService(EmbeddingModel embeddingModel, ModelRouterService modelRouterService) {
        this.embeddingModel = embeddingModel;
        this.modelRouterService = modelRouterService;
    }

    public float[] embed(String text) {
        return embedWithFallback(text, modelRouterService.getEmbeddingCandidates(), 0, 0);
    }

    public float[] embed(Document document) {
        return embed(document.getContent());
    }

    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        return embedBatchWithFallback(texts, modelRouterService.getEmbeddingCandidates(), 0, 0);
    }

    private float[] embedWithFallback(String text, List<String> candidates, int candidateIndex, int retryAttempt) {

        if (candidateIndex >= candidates.size()) {
            throw new IllegalStateException("当前没有可用的向量模型，请稍后再试");
        }

        String model = candidates.get(candidateIndex);
        OpenAiEmbeddingOptions options = buildEmbeddingOptions(model);

        try {
            log.info("开始调用向量模型, model={}, candidateIndex={}, retryAttempt={}", model, candidateIndex, retryAttempt);
            EmbeddingResponse response = embeddingModel.call(new EmbeddingRequest(List.of(text), options));
            Embedding embedding = response.getResult();
            if (embedding == null || embedding.getOutput() == null) {
                throw new IllegalStateException("向量模型未返回有效 embedding");
            }
            return embedding.getOutput();
        } catch (RuntimeException error) {
            if (modelRouterService.isQuotaExhausted(error)) {
                modelRouterService.markEmbeddingModelExhausted(model, error);
                log.warn("向量模型免费额度或配额已耗尽，切换下一个模型, model={}", model);
                return embedWithFallback(text, candidates, candidateIndex + 1, 0);
            }

            if (modelRouterService.isTransientFailure(error)
                    && retryAttempt < modelRouterService.getEmbeddingTransientRetries()) {
                log.warn("向量模型调用瞬时失败，重试当前模型, model={}, retryAttempt={}", model, retryAttempt + 1);
                return embedWithFallback(text, candidates, candidateIndex, retryAttempt + 1);
            }

            if (modelRouterService.isTransientFailure(error) && candidateIndex + 1 < candidates.size()) {
                log.warn("向量模型调用失败，切换备用模型, currentModel={}, nextModel={}", model, candidates.get(candidateIndex + 1));
                return embedWithFallback(text, candidates, candidateIndex + 1, 0);
            }

            throw error;
        }
    }

    private List<float[]> embedBatchWithFallback(List<String> texts, List<String> candidates, int candidateIndex, int retryAttempt) {
        if (candidateIndex >= candidates.size()) {
            throw new IllegalStateException("当前没有可用的向量模型，请稍后再试");
        }

        String model = candidates.get(candidateIndex);
        OpenAiEmbeddingOptions options = buildEmbeddingOptions(model);

        try {
            log.info("开始批量调用向量模型, model={}, candidateIndex={}, retryAttempt={}, batchSize={}",
                    model, candidateIndex, retryAttempt, texts.size());
            EmbeddingResponse response = embeddingModel.call(new EmbeddingRequest(texts, options));
            List<Embedding> embeddings = response.getResults();
            if (embeddings == null || embeddings.size() != texts.size()) {
                throw new IllegalStateException("向量模型未返回完整的 embedding 结果");
            }

            List<float[]> outputs = new ArrayList<>(embeddings.size());
            for (Embedding embedding : embeddings) {
                if (embedding == null || embedding.getOutput() == null) {
                    throw new IllegalStateException("向量模型返回了无效的 embedding");
                }
                outputs.add(embedding.getOutput());
            }
            return outputs;
        } catch (RuntimeException error) {
            if (modelRouterService.isQuotaExhausted(error)) {
                modelRouterService.markEmbeddingModelExhausted(model, error);
                log.warn("批量向量模型免费额度或配额已耗尽，切换下一个模型, model={}", model);
                return embedBatchWithFallback(texts, candidates, candidateIndex + 1, 0);
            }

            if (modelRouterService.isTransientFailure(error)
                    && retryAttempt < modelRouterService.getEmbeddingTransientRetries()) {
                log.warn("批量向量模型调用瞬时失败，重试当前模型, model={}, retryAttempt={}", model, retryAttempt + 1);
                return embedBatchWithFallback(texts, candidates, candidateIndex, retryAttempt + 1);
            }

            if (modelRouterService.isTransientFailure(error) && candidateIndex + 1 < candidates.size()) {
                log.warn("批量向量模型调用失败，切换备用模型, currentModel={}, nextModel={}", model, candidates.get(candidateIndex + 1));
                return embedBatchWithFallback(texts, candidates, candidateIndex + 1, 0);
            }

            throw error;
        }
    }

    private OpenAiEmbeddingOptions buildEmbeddingOptions(String model) {
        return OpenAiEmbeddingOptions.builder()
                .model(model)
                .build();
    }
}
