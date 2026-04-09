package cn.net.tomatoegg.ai.service.document.storage;

import cn.net.tomatoegg.ai.config.DocumentStorageProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.http.Method;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

@Component
public class MinioDocumentStorageProvider implements DocumentStorageProvider {

    private static final String PREFIX = "minio:";

    private final DocumentStorageProperties properties;
    private volatile MinioClient client;

    public MinioDocumentStorageProvider(DocumentStorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public String storageType() {
        return "minio";
    }

    @Override
    public boolean supports(String storagePath) {
        return StringUtils.hasText(storagePath) && storagePath.startsWith(PREFIX);
    }

    @Override
    public StoredDocument store(MultipartFile file, UUID userId, String safeFilename) throws IOException {
        StorageLocation location = createLocation(userId, safeFilename);
        MessageDigest digest = createDigest();
        try (InputStream inputStream = new DigestInputStream(file.getInputStream(), digest)) {
            ensureBucketExists(location.bucket());
            getClient().putObject(PutObjectArgs.builder()
                    .bucket(location.bucket())
                    .object(location.objectName())
                    .stream(inputStream, file.getSize(), -1)
                    .contentType(resolveContentType(file.getContentType()))
                    .build());
            return new StoredDocument(PREFIX + location.bucket() + "/" + location.objectName(), toHex(digest.digest()));
        } catch (IOException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IOException("Upload file to MinIO failed", ex);
        }
    }

    @Override
    public StoredDocumentSource openProcessingSource(String storagePath, String filename) throws IOException {
        StorageLocation location = parse(storagePath);
        String suffix = resolveTempSuffix(filename);
        Path tempFile = Files.createTempFile("doc-process-", suffix);
        try (GetObjectResponse object = getClient().getObject(GetObjectArgs.builder()
                .bucket(location.bucket())
                .object(location.objectName())
                .build())) {
            Files.copy(object, tempFile, StandardCopyOption.REPLACE_EXISTING);
            return new StoredDocumentSource(tempFile, true);
        } catch (Exception ex) {
            Files.deleteIfExists(tempFile);
            throw new IOException("Load file from MinIO failed", ex);
        }
    }

    @Override
    public DocumentPreviewResource openPreview(String storagePath, String filename) throws IOException {
        StorageLocation location = parse(storagePath);
        try {
            getClient().statObject(StatObjectArgs.builder()
                    .bucket(location.bucket())
                    .object(location.objectName())
                    .build());
            String url = getClient().getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(location.bucket())
                    .object(location.objectName())
                    .expiry(5, TimeUnit.MINUTES)
                    .build());
            return DocumentPreviewResource.forRedirect(url);
        } catch (Exception ex) {
            throw new IOException("Open preview file from MinIO failed", ex);
        }
    }

    @Override
    public void delete(String storagePath) throws IOException {
        StorageLocation location = parse(storagePath);
        try {
            getClient().removeObject(RemoveObjectArgs.builder()
                    .bucket(location.bucket())
                    .object(location.objectName())
                    .build());
        } catch (Exception ex) {
            throw new IOException("Delete file from MinIO failed", ex);
        }
    }

    private StorageLocation createLocation(UUID userId, String safeFilename) {
        String bucket = properties.getMinio().getBucket();
        String objectName = userId + "/" + UUID.randomUUID() + "_" + safeFilename.replaceAll("[\\\\/:*?\"<>|]", "_");
        return new StorageLocation(bucket, objectName);
    }

    private StorageLocation parse(String storagePath) {
        if (!supports(storagePath)) {
            throw new IllegalArgumentException("Unsupported MinIO storage path: " + storagePath);
        }
        String raw = storagePath.substring(PREFIX.length());
        int slashIndex = raw.indexOf('/');
        if (slashIndex <= 0 || slashIndex == raw.length() - 1) {
            throw new IllegalArgumentException("Invalid MinIO storage path: " + storagePath);
        }
        return new StorageLocation(raw.substring(0, slashIndex), raw.substring(slashIndex + 1));
    }

    private void ensureBucketExists(String bucket) throws Exception {
        if (getClient().bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            return;
        }
        if (!properties.getMinio().isAutoCreateBucket()) {
            throw new IllegalStateException("MinIO bucket does not exist: " + bucket);
        }
        getClient().makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
    }

    private MinioClient getClient() {
        MinioClient existing = client;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (client == null) {
                validateConfig();
                client = MinioClient.builder()
                        .endpoint(properties.getMinio().getEndpoint())
                        .credentials(properties.getMinio().getAccessKey(), properties.getMinio().getSecretKey())
                        .build();
            }
            return client;
        }
    }

    private void validateConfig() {
        if (!StringUtils.hasText(properties.getMinio().getEndpoint())
                || !StringUtils.hasText(properties.getMinio().getAccessKey())
                || !StringUtils.hasText(properties.getMinio().getSecretKey())
                || !StringUtils.hasText(properties.getMinio().getBucket())) {
            throw new IllegalStateException("MinIO storage is enabled but configuration is incomplete");
        }
    }

    private String resolveContentType(String contentType) {
        if (StringUtils.hasText(contentType)) {
            return contentType;
        }
        return "application/octet-stream";
    }

    private String resolveTempSuffix(String filename) {
        int dotIndex = filename == null ? -1 : filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            return ".tmp";
        }
        return filename.substring(dotIndex);
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

    private record StorageLocation(String bucket, String objectName) {
    }
}
