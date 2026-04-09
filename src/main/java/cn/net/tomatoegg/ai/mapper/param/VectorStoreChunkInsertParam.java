package cn.net.tomatoegg.ai.mapper.param;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class VectorStoreChunkInsertParam {

    private final UUID id;
    private final UUID userId;
    private final UUID kbId;
    private final UUID docId;
    private final Integer chunkIndex;
    private final String content;
    private final String metadataJson;
    private final String embedding;
}
