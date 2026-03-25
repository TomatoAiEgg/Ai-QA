package cn.net.tomatoegg.ai.entity;

import lombok.Data;

@Data
public class VectorStoreChunk {

    private String content;

    private String metadataJson;

    private Double score;
}
