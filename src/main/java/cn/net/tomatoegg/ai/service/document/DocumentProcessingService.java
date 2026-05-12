package cn.net.tomatoegg.ai.service.document;

import cn.net.tomatoegg.ai.common.ApiCode;
import cn.net.tomatoegg.ai.entity.KnowledgeBase;
import cn.net.tomatoegg.ai.entity.KnowledgeBaseDocument;
import cn.net.tomatoegg.ai.exception.BusinessException;
import cn.net.tomatoegg.ai.mapper.KnowledgeBaseDocumentMapper;
import cn.net.tomatoegg.ai.mapper.KnowledgeBaseMapper;
import cn.net.tomatoegg.ai.mapper.VectorStoreMapper;
import cn.net.tomatoegg.ai.mapper.param.VectorStoreChunkInsertParam;
import cn.net.tomatoegg.ai.service.document.storage.DocumentStorageService;
import cn.net.tomatoegg.ai.service.document.storage.StoredDocumentSource;
import cn.net.tomatoegg.ai.service.embedding.DashScopeEmbeddingService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
public class DocumentProcessingService {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 500;
    private static final int MAX_EMBEDDING_BATCH_SIZE = 10;
    private static final int STRUCTURED_SECTION_TARGET_CHARS = 1600;
    private static final Pattern PDF_GARBAGE_LINE = Pattern.compile("^[A-Za-z0-9_-]{24,}$");
    private static final Pattern MARKDOWN_HEADING = Pattern.compile("^#{1,6}\\s+(.+)$");
    private static final Pattern MAJOR_TEXT_HEADING = Pattern.compile(
            "^(个人信息|联系方式|掌握技能|专业技能|技能清单|教育经历|工作经历|工作经验|项目经历|项目经验|实习经历|校园经历|证书|个人优势|自我评价|总结)$");

    private final TokenTextSplitter tokenTextSplitter;
    private final DashScopeEmbeddingService embeddingService;
    private final ObjectMapper objectMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeBaseDocumentMapper documentMapper;
    private final VectorStoreMapper vectorStoreMapper;
    private final DocumentStorageService documentStorageService;
    private final CacheManager cacheManager;
    private final int embeddingBatchSize;

    public DocumentProcessingService(TokenTextSplitter tokenTextSplitter,
                                     DashScopeEmbeddingService embeddingService,
                                     ObjectMapper objectMapper,
                                     KnowledgeBaseMapper knowledgeBaseMapper,
                                     KnowledgeBaseDocumentMapper documentMapper,
                                     VectorStoreMapper vectorStoreMapper,
                                     DocumentStorageService documentStorageService,
                                     CacheManager cacheManager,
                                     @Value("${ai.service.document.embedding-batch-size:10}") int embeddingBatchSize) {
        this.tokenTextSplitter = tokenTextSplitter;
        this.embeddingService = embeddingService;
        this.objectMapper = objectMapper;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.documentMapper = documentMapper;
        this.vectorStoreMapper = vectorStoreMapper;
        this.documentStorageService = documentStorageService;
        this.cacheManager = cacheManager;
        if (embeddingBatchSize > MAX_EMBEDDING_BATCH_SIZE) {
            log.warn("Configured embedding batch size {} exceeds provider limit {}, clamping to {}",
                    embeddingBatchSize, MAX_EMBEDDING_BATCH_SIZE, MAX_EMBEDDING_BATCH_SIZE);
        }
        this.embeddingBatchSize = Math.min(Math.max(1, embeddingBatchSize), MAX_EMBEDDING_BATCH_SIZE);
    }

    public String processDocument(UUID docId) {
        KnowledgeBaseDocument docRecord = documentMapper.selectById(docId);
        if (docRecord == null) {
            log.warn("Skip document processing because the document record is missing, docId={}", docId);
            return "文档记录不存在";
        }
        if (DocumentProcessStatus.COMPLETED.equalsIgnoreCase(docRecord.getStatus())) {
            log.info("Skip document processing because the document has already completed, docId={}", docId);
            return "文档已经处理完成";
        }
        if (!tryAcquireProcessing(docId)) {
            return describeSkippedProcessing(docId);
        }

        UUID userId = docRecord.getUserId();
        UUID kbId = docRecord.getKbId();
        try {
            requireOwnedKnowledgeBase(userId, kbId);

            if (docRecord.getStoragePath() == null || docRecord.getStoragePath().isBlank()) {
                throw new BusinessException(ApiCode.NOT_FOUND, "原始文件路径缺失");
            }

            List<Document> documents;
            int totalOriginalChars;
            List<Document> splitDocuments;
            try (StoredDocumentSource source =
                         documentStorageService.openProcessingSource(docRecord.getStoragePath(), docRecord.getFilename())) {
                documents = readDocuments(source.path(), docRecord.getFilename());
                totalOriginalChars = documents.stream().mapToInt(doc -> doc.getContent().length()).sum();
                splitDocuments = tokenTextSplitter.apply(documents);
            }

            for (int i = 0; i < splitDocuments.size(); i++) {
                Document document = splitDocuments.get(i);
                document.getMetadata().put("doc_id", docRecord.getId().toString());
                document.getMetadata().put("filename", docRecord.getFilename());
                document.getMetadata().put("file_hash", docRecord.getFileHash());
                document.getMetadata().put("uploadTime", System.currentTimeMillis());
                document.getMetadata().put("kb_id", kbId.toString());
                document.getMetadata().put("user_id", userId.toString());
                document.getMetadata().put("chunkIndex", i);
                document.getMetadata().put("totalChunks", splitDocuments.size());
            }

            vectorStoreMapper.deleteByDocumentId(docRecord.getId());
            persistDocumentChunks(docRecord, splitDocuments);

            Map<String, Object> stats = analyzeChunks(splitDocuments, totalOriginalChars);
            updateDocumentStatus(docRecord.getId(), DocumentProcessStatus.COMPLETED, splitDocuments.size(), null);
            log.info("Document processing completed, userId={}, kbId={}, docId={}, filename={}, chunkCount={}",
                    userId, kbId, docRecord.getId(), docRecord.getFilename(), splitDocuments.size());

            return String.format(
                    "文档处理成功，共生成 %d 个切片，平均切片长度 %d 个字符，重叠率 %.1f%%",
                    splitDocuments.size(),
                    stats.get("avgChunkSize"),
                    stats.get("overlapRatio")
            );
        } catch (Exception ex) {
            log.error("Document processing failed, userId={}, kbId={}, docId={}, filename={}",
                    userId, kbId, docRecord.getId(), docRecord.getFilename(), ex);
            vectorStoreMapper.deleteByDocumentId(docRecord.getId());
            updateDocumentStatus(docRecord.getId(), DocumentProcessStatus.FAILED, 0, normalizeErrorMessage(ex));
            throw ex instanceof BusinessException
                    ? (BusinessException) ex
                    : new BusinessException(ApiCode.DOCUMENT_UPLOAD_FAILED, "文档处理失败: " + ex.getMessage(), ex);
        } finally {
            evictDocumentCaches(userId);
        }
    }

    public void deleteKnowledgeBaseVectors(UUID kbId) {
        vectorStoreMapper.deleteByKnowledgeBaseId(kbId);
        log.info("Deleted knowledge base vectors, kbId={}", kbId);
    }

    private boolean tryAcquireProcessing(UUID docId) {
        return documentMapper.update(null, new LambdaUpdateWrapper<KnowledgeBaseDocument>()
                .eq(KnowledgeBaseDocument::getId, docId)
                .and(wrapper -> wrapper.eq(KnowledgeBaseDocument::getStatus, DocumentProcessStatus.PENDING)
                        .or()
                        .eq(KnowledgeBaseDocument::getStatus, DocumentProcessStatus.FAILED))
                .set(KnowledgeBaseDocument::getStatus, DocumentProcessStatus.PROCESSING)
                .set(KnowledgeBaseDocument::getChunkCount, 0)
                .set(KnowledgeBaseDocument::getErrorMessage, null)
                .setSql("updated_at = NOW()")) > 0;
    }

    private String describeSkippedProcessing(UUID docId) {
        KnowledgeBaseDocument latest = documentMapper.selectById(docId);
        if (latest == null) {
            log.warn("Skip document processing because the document record is missing, docId={}", docId);
            return "文档记录不存在";
        }

        String status = latest.getStatus();
        if (DocumentProcessStatus.COMPLETED.equalsIgnoreCase(status)) {
            return "文档已经处理完成";
        }
        if (DocumentProcessStatus.PROCESSING.equalsIgnoreCase(status)) {
            log.info("Skip duplicate document processing because the document is already in progress, docId={}", docId);
            return "文档正在处理中";
        }
        if (DocumentProcessStatus.PENDING.equalsIgnoreCase(status)) {
            log.info("Skip document processing because the document is still pending, docId={}", docId);
            return "文档仍在排队中";
        }
        if (DocumentProcessStatus.FAILED.equalsIgnoreCase(status)) {
            log.info("Skip document processing because the document is marked as failed, docId={}", docId);
            return "文档处理失败";
        }

        log.warn("Skip document processing because document status is unexpected, docId={}, status={}", docId, status);
        return "文档状态异常";
    }

    private void updateDocumentStatus(UUID docId, String status, int chunkCount, String errorMessage) {
        documentMapper.update(null, new LambdaUpdateWrapper<KnowledgeBaseDocument>()
                .eq(KnowledgeBaseDocument::getId, docId)
                .set(KnowledgeBaseDocument::getChunkCount, chunkCount)
                .set(KnowledgeBaseDocument::getStatus, status)
                .set(KnowledgeBaseDocument::getErrorMessage, errorMessage)
                .setSql("updated_at = NOW()"));
    }

    private String normalizeErrorMessage(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        if (message.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }

    private void evictDocumentCaches(UUID userId) {
        evictSingleCacheEntry("knowledgeBaseListCache", userId.toString());
        clearCache("knowledgeBaseDetailCache");
        clearCache("knowledgeBaseDocumentsCache");
        clearCache("documentListCache");
        clearCache("documentPreviewCache");
    }

    private void clearCache(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
        }
    }

    private void evictSingleCacheEntry(String cacheName, String key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.evict(key);
        }
    }

    private List<Document> readDocuments(Path filePath, String filename) {
        String extension = resolveExtension(filePath, filename);
        if ("pdf".equals(extension)) {
            return readPdfDocuments(filePath, filename);
        }
        if ("md".equals(extension) || "markdown".equals(extension)) {
            return readMarkdownDocuments(filePath, filename);
        }
        if ("txt".equals(extension)) {
            return readTextDocuments(filePath, filename);
        }
        return readTikaDocuments(filePath, filename, extension);
    }

    private List<Document> readPdfDocuments(Path filePath, String filename) {
        try (PDDocument pdfDocument = Loader.loadPDF(filePath.toFile())) {
            PDFTextStripper textStripper = new PDFTextStripper();
            textStripper.setSortByPosition(true);

            String text = cleanPdfText(textStripper.getText(pdfDocument));
            if (text.isBlank()) {
                return List.of();
            }

            return splitStructuredTextDocuments(text, filePath, filename, "pdf");
        } catch (IOException ex) {
            throw new BusinessException(ApiCode.DOCUMENT_UPLOAD_FAILED, "PDF 文档解析失败: " + ex.getMessage(), ex);
        }
    }

    private List<Document> readMarkdownDocuments(Path filePath, String filename) {
        String text = cleanPlainText(readTextFile(filePath));
        if (text.isBlank()) {
            return List.of();
        }
        return splitMarkdownDocuments(text, filePath, filename);
    }

    private List<Document> readTextDocuments(Path filePath, String filename) {
        String text = cleanPlainText(readTextFile(filePath));
        if (text.isBlank()) {
            return List.of();
        }
        return splitStructuredTextDocuments(text, filePath, filename, "txt");
    }

    private List<Document> readTikaDocuments(Path filePath, String filename, String extension) {
        TikaDocumentReader reader = new TikaDocumentReader(new FileSystemResource(filePath));
        String sourceType = extension.isBlank() ? "document" : extension;
        return reader.read().stream()
                .flatMap(document -> {
                    String text = cleanPlainText(document.getContent());
                    if (text.isBlank()) {
                        return List.<Document>of().stream();
                    }
                    return splitStructuredTextDocuments(text, filePath, filename, sourceType).stream();
                })
                .toList();
    }

    private String readTextFile(Path filePath) {
        try {
            return Files.readString(filePath, StandardCharsets.UTF_8);
        } catch (MalformedInputException ex) {
            try {
                return Files.readString(filePath, Charset.forName("GB18030"));
            } catch (IOException fallbackEx) {
                throw new BusinessException(ApiCode.DOCUMENT_UPLOAD_FAILED, "文本文件读取失败: " + fallbackEx.getMessage(), fallbackEx);
            }
        } catch (IOException ex) {
            throw new BusinessException(ApiCode.DOCUMENT_UPLOAD_FAILED, "文本文件读取失败: " + ex.getMessage(), ex);
        }
    }

    private String resolveExtension(Path filePath, String filename) {
        String name = filename != null && !filename.isBlank()
                ? filename
                : filePath.getFileName().toString();
        int dotIndex = name.lastIndexOf('.');
        return dotIndex >= 0 && dotIndex + 1 < name.length()
                ? name.substring(dotIndex + 1).toLowerCase(Locale.ROOT)
                : "";
    }

    private String cleanPdfText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder cleaned = new StringBuilder(normalized.length());
        String[] lines = normalized.split("\n", -1);

        for (String line : lines) {
            String trimmed = line.trim();
            if (isPdfGarbageLine(trimmed)) {
                continue;
            }
            cleaned.append(trimmed).append('\n');
        }

        return cleaned.toString()
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }

    private boolean isPdfGarbageLine(String line) {
        return PDF_GARBAGE_LINE.matcher(line).matches();
    }

    private String cleanPlainText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder cleaned = new StringBuilder(normalized.length());
        String[] lines = normalized.split("\n", -1);

        for (String line : lines) {
            String normalizedLine = line.stripTrailing();
            cleaned.append(normalizedLine).append('\n');
        }

        return cleaned.toString()
                .replaceAll("[\\t ]{2,}", " ")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }

    private List<Document> splitMarkdownDocuments(String text, Path filePath, String filename) {
        List<Document> sections = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String currentTitle = "正文";
        int sectionIndex = 0;
        boolean foundHeading = false;

        for (String line : text.split("\n")) {
            java.util.regex.Matcher matcher = MARKDOWN_HEADING.matcher(line.trim());
            if (matcher.matches()) {
                sectionIndex = appendSection(sections, current, currentTitle, sectionIndex, filePath, filename, "markdown");
                currentTitle = matcher.group(1).trim();
                foundHeading = true;
            }
            current.append(line).append('\n');
        }
        sectionIndex = appendSection(sections, current, currentTitle, sectionIndex, filePath, filename, "markdown");

        return foundHeading && !sections.isEmpty()
                ? sections
                : splitParagraphDocuments(text, filePath, filename, "markdown", "正文");
    }

    private List<Document> splitStructuredTextDocuments(String text, Path filePath, String filename, String sourceType) {
        List<Document> sections = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String currentTitle = "正文";
        int sectionIndex = 0;
        boolean foundHeading = false;

        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (isMajorTextHeading(trimmed)) {
                sectionIndex = appendSection(sections, current, currentTitle, sectionIndex, filePath, filename, sourceType);
                currentTitle = normalizeHeading(trimmed);
                foundHeading = true;
            }
            current.append(line).append('\n');
        }
        sectionIndex = appendSection(sections, current, currentTitle, sectionIndex, filePath, filename, sourceType);

        return foundHeading && !sections.isEmpty()
                ? sections
                : splitParagraphDocuments(text, filePath, filename, sourceType, "正文");
    }

    private List<Document> splitParagraphDocuments(String text,
                                                   Path filePath,
                                                   String filename,
                                                   String sourceType,
                                                   String defaultTitle) {
        List<Document> sections = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int sectionIndex = 0;

        for (String block : text.split("\n\\s*\n")) {
            String normalizedBlock = block.trim();
            if (normalizedBlock.isBlank()) {
                continue;
            }
            if (current.length() > 0
                    && current.length() + normalizedBlock.length() > STRUCTURED_SECTION_TARGET_CHARS) {
                sectionIndex = appendSection(sections, current, defaultTitle, sectionIndex, filePath, filename, sourceType);
            }
            current.append(normalizedBlock).append("\n\n");
        }
        appendSection(sections, current, defaultTitle, sectionIndex, filePath, filename, sourceType);
        return sections;
    }

    private int appendSection(List<Document> sections,
                              StringBuilder content,
                              String sectionTitle,
                              int sectionIndex,
                              Path filePath,
                              String filename,
                              String sourceType) {
        String sectionContent = content.toString().trim();
        content.setLength(0);
        if (sectionContent.isBlank() || isHeadingOnlySection(sectionContent, sectionTitle)) {
            return sectionIndex;
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", filePath.toString());
        metadata.put("filename", filename != null && !filename.isBlank() ? filename : filePath.getFileName().toString());
        metadata.put("source_type", sourceType);
        metadata.put("section_title", sectionTitle);
        metadata.put("section_index", sectionIndex);
        sections.add(new Document(sectionContent, metadata));
        return sectionIndex + 1;
    }

    private boolean isHeadingOnlySection(String content, String sectionTitle) {
        return normalizeHeading(content).equals(normalizeHeading(sectionTitle));
    }

    private boolean isMajorTextHeading(String line) {
        return !line.isBlank() && MAJOR_TEXT_HEADING.matcher(normalizeHeading(line)).matches();
    }

    private String normalizeHeading(String line) {
        return line.replaceAll("[：:]+$", "").trim();
    }

    private void persistDocumentChunks(KnowledgeBaseDocument docRecord, List<Document> splitDocuments) {
        for (int start = 0; start < splitDocuments.size(); start += embeddingBatchSize) {
            int end = Math.min(start + embeddingBatchSize, splitDocuments.size());
            List<Document> batchDocuments = splitDocuments.subList(start, end);
            List<String> batchContents = batchDocuments.stream()
                    .map(Document::getContent)
                    .toList();
            List<float[]> embeddings = embeddingService.embedBatch(batchContents);

            if (embeddings.size() != batchDocuments.size()) {
                throw new BusinessException(ApiCode.SERVER_ERROR, "批量向量化结果数量与文档切片数量不一致");
            }

            List<VectorStoreChunkInsertParam> batchRows = new ArrayList<>(batchDocuments.size());
            for (int offset = 0; offset < batchDocuments.size(); offset++) {
                int chunkIndex = start + offset;
                Document document = batchDocuments.get(offset);
                batchRows.add(VectorStoreChunkInsertParam.builder()
                        .id(UUID.randomUUID())
                        .userId(docRecord.getUserId())
                        .kbId(docRecord.getKbId())
                        .docId(docRecord.getId())
                        .chunkIndex(chunkIndex)
                        .content(document.getContent())
                        .metadataJson(toMetadataJson(document.getMetadata()))
                        .embedding(toVectorLiteral(embeddings.get(offset)))
                        .build());
            }
            vectorStoreMapper.batchInsertChunks(batchRows);
        }
    }

    private String toMetadataJson(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ApiCode.SERVER_ERROR, "向量元数据序列化失败", ex);
        }
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

    private Map<String, Object> analyzeChunks(List<Document> chunks, int totalOriginalChars) {
        Map<String, Object> stats = new HashMap<>();
        if (chunks.isEmpty()) {
            stats.put("avgChunkSize", 0);
            stats.put("overlapRatio", 0.0);
            return stats;
        }

        List<Integer> chunkSizes = chunks.stream().map(doc -> doc.getContent().length()).toList();
        int avgSize = chunkSizes.stream().mapToInt(Integer::intValue).sum() / chunkSizes.size();
        int totalChunkChars = chunkSizes.stream().mapToInt(Integer::intValue).sum();
        double overlapRatio = totalOriginalChars > 0
                ? ((double) totalChunkChars / totalOriginalChars - 1) * 100
                : 0;
        stats.put("avgChunkSize", avgSize);
        stats.put("overlapRatio", Math.round(overlapRatio * 10.0) / 10.0);
        return stats;
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
