package cn.net.tomatoegg.ai.service.document.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class StoredDocumentSource implements AutoCloseable {

    private final Path path;
    private final boolean temporary;

    public StoredDocumentSource(Path path, boolean temporary) {
        this.path = path;
        this.temporary = temporary;
    }

    public Path path() {
        return path;
    }

    @Override
    public void close() throws IOException {
        if (temporary) {
            Files.deleteIfExists(path);
        }
    }
}
