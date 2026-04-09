package cn.net.tomatoegg.ai.service.document.storage;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;

public record DocumentPreviewResource(Resource resource,
                                      MediaType mediaType,
                                      long contentLength,
                                      String filename,
                                      String redirectUrl) {

    public static DocumentPreviewResource forStream(Resource resource,
                                                    MediaType mediaType,
                                                    long contentLength,
                                                    String filename) {
        return new DocumentPreviewResource(resource, mediaType, contentLength, filename, null);
    }

    public static DocumentPreviewResource forRedirect(String redirectUrl) {
        return new DocumentPreviewResource(null, null, -1, null, redirectUrl);
    }
}
