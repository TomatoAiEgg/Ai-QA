package cn.net.tomatoegg.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("conversation_messages")
public class ConversationMessage {

    @TableId(value = "id", type = IdType.INPUT)
    private UUID id;

    @TableField("conversation_id")
    private UUID conversationId;

    private String role;

    private String content;

    @TableField("created_at")
    private OffsetDateTime createdAt;
}
