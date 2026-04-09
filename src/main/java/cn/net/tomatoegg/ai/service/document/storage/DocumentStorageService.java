package cn.net.tomatoegg.ai.service.document.storage;

import cn.net.tomatoegg.ai.common.ApiCode;
import cn.net.tomatoegg.ai.config.DocumentStorageProperties;
import cn.net.tomatoegg.ai.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class DocumentStorageService {

    private final DocumentStorageProperties properties;
    private final List<DocumentStorageProvider> providers;

    public DocumentStorageService(DocumentStorageProperties properties, List<DocumentStorageProvider> providers) {
        this.properties = properties;
        this.providers = providers;
    }

    public StoredDocument store(MultipartFile file, UUID userId, String safeFilename) {
        try {
            return selectDefaultProvider().store(file, userId, safeFilename);
        } catch (IOException ex) {
            throw new BusinessException(ApiCode.DOCUMENT_UPLOAD_FAILED, "保存上传文档失败: " + ex.getMessage(), ex);
        } catch (RuntimeException ex) {
            throw new BusinessException(ApiCode.DOCUMENT_UPLOAD_FAILED, "保存上传文档失败: " + ex.getMessage(), ex);
        }
    }

    public StoredDocumentSource openProcessingSource(String storagePath, String filename) {
        try {
            return selectProvider(storagePath).openProcessingSource(storagePath, filename);
        } catch (IOException ex) {
            throw new BusinessException(ApiCode.NOT_FOUND, "原始文件不存在或无法读取", ex);
        } catch (RuntimeException ex) {
            throw new BusinessException(ApiCode.NOT_FOUND, "原始文件不存在或无法读取", ex);
        }
    }

    public DocumentPreviewResource openPreview(String storagePath, String filename) {
        try {
            return selectProvider(storagePath).openPreview(storagePath, filename);
        } catch (IOException ex) {
            throw new BusinessException(ApiCode.DOCUMENT_PREVIEW_FAILED, "文档预览失败，无法读取源文件", ex);
        } catch (RuntimeException ex) {
            throw new BusinessException(ApiCode.DOCUMENT_PREVIEW_FAILED, "文档预览失败，无法读取源文件", ex);
        }
    }

    public void delete(String storagePath) {
        if (!StringUtils.hasText(storagePath)) {
            return;
        }
        try {
            selectProvider(storagePath).delete(storagePath);
        } catch (IOException ex) {
            throw new BusinessException(ApiCode.DOCUMENT_DELETE_FAILED, "删除源文件失败: " + ex.getMessage(), ex);
        } catch (RuntimeException ex) {
            throw new BusinessException(ApiCode.DOCUMENT_DELETE_FAILED, "删除源文件失败: " + ex.getMessage(), ex);
        }
    }

    private DocumentStorageProvider selectDefaultProvider() {
        String type = properties.getType() == null ? "local" : properties.getType().trim().toLowerCase(Locale.ROOT);
        return providers.stream()
                .filter(provider -> provider.storageType().equalsIgnoreCase(type))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Unsupported document storage type: " + type));
    }

    private DocumentStorageProvider selectProvider(String storagePath) {
        return providers.stream()
                .filter(provider -> provider.supports(storagePath))
                .findFirst()
                .orElseGet(this::selectDefaultProvider);
    }
}
