package cn.net.tomatoegg.ai.service.document.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

public interface DocumentStorageProvider {

    String storageType();

    boolean supports(String storagePath);

    StoredDocument store(MultipartFile file, UUID userId, String safeFilename) throws IOException;

    StoredDocumentSource openProcessingSource(String storagePath, String filename) throws IOException;

    DocumentPreviewResource openPreview(String storagePath, String filename) throws IOException;

    void delete(String storagePath) throws IOException;
}
