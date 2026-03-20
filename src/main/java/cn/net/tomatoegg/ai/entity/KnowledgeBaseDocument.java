package cn.net.tomatoegg.ai.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 知识库文档关联实体
 *
 * @author 苏三
 * @date 2026/3/17
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeBaseDocument {

    private UUID id;
    private UUID kbId;
    private String filename;
    private String fileHash;
    private Long fileSize;
    private Integer chunkCount;
    private String status;
    private String errorMessage;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
