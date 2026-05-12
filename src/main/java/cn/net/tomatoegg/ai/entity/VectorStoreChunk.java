package cn.net.tomatoegg.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.UUID;

@Data
@TableName("vector_store_1024_v2")
public class VectorStoreChunk {

    @TableId(value = "id", type = IdType.INPUT)
    private UUID id;

    @TableField("user_id")
    private UUID userId;

    @TableField("kb_id")
    private UUID kbId;

    @TableField("doc_id")
    private UUID docId;

    @TableField("chunk_index")
    private Integer chunkIndex;

    private String content;

    @TableField("metadata")
    private String metadataJson;

    @TableField(exist = false)
    private Double score;
}
