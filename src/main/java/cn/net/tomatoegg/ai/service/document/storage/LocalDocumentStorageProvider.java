package cn.net.tomatoegg.ai.service.document.storage;

import cn.net.tomatoegg.ai.config.DocumentStorageProperties;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

@Component
public class LocalDocumentStorageProvider implements DocumentStorageProvider {

    private static final String PREFIX = "file:";

    private final Path rootPath;

    public LocalDocumentStorageProvider(DocumentStorageProperties properties) {
        this.rootPath = Paths.get(properties.getLocal().getRoot());
    }

    @Override
    public String storageType() {
        return "local";
    }

    @Override
    public boolean supports(String storagePath) {
        return storagePath == null || !storagePath.startsWith("minio:");
    }

    @Override
    public StoredDocument store(MultipartFile file, UUID userId, String safeFilename) throws IOException {
        Path userStorageRoot = rootPath.resolve(userId.toString());
        Files.createDirectories(userStorageRoot);
        String storedName = UUID.randomUUID() + "_" + safeFilename.replaceAll("[\\\\/:*?\"<>|]", "_");
        Path target = userStorageRoot.resolve(storedName).toAbsolutePath();
        MessageDigest digest = createDigest();
        try (InputStream inputStream = new DigestInputStream(file.getInputStream(), digest)) {
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return new StoredDocument(PREFIX + target, toHex(digest.digest()));
    }

    @Override
    public StoredDocumentSource openProcessingSource(String storagePath, String filename) {
        return new StoredDocumentSource(resolvePath(storagePath), false);
    }

    @Override
    public DocumentPreviewResource openPreview(String storagePath, String filename) throws IOException {
        Path path = resolvePath(storagePath);
        return DocumentPreviewResource.forStream(
                new FileSystemResource(path),
                resolveMediaType(path, filename),
                Files.size(path),
                filename
        );
    }

    @Override
    public void delete(String storagePath) throws IOException {
        Files.deleteIfExists(resolvePath(storagePath));
    }

    private Path resolvePath(String storagePath) {
        if (!StringUtils.hasText(storagePath)) {
            throw new IllegalArgumentException("storagePath must not be blank");
        }
        if (storagePath.startsWith(PREFIX)) {
            return Path.of(storagePath.substring(PREFIX.length()));
        }
        return Path.of(storagePath);
    }

    private MediaType resolveMediaType(Path path, String filename) {
        try {
            String probedType = Files.probeContentType(path);
            if (StringUtils.hasText(probedType)) {
                return MediaType.parseMediaType(probedType);
            }
        } catch (Exception ignored) {
        }
        return MediaTypeFactory.getMediaType(filename).orElse(MediaType.APPLICATION_OCTET_STREAM);
    }

    private MessageDigest createDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private String toHex(byte[] hash) {
        StringBuilder builder = new StringBuilder(hash.length * 2);
        for (byte value : hash) {
            String hex = Integer.toHexString(0xff & value);
            if (hex.length() == 1) {
                builder.append('0');
            }
            builder.append(hex);
        }
        return builder.toString();
    }
}
