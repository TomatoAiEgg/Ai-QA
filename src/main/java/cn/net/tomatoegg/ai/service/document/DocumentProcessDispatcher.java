package cn.net.tomatoegg.ai.service.document;

import java.util.UUID;

public interface DocumentProcessDispatcher {

    String dispatch(UUID docId);
}
