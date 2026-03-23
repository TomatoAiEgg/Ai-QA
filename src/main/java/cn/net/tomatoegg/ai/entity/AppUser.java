package cn.net.tomatoegg.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
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
@TableName("app_users")
public class AppUser {

    @TableId(value = "id", type = IdType.INPUT)
    private UUID id;

    private String email;

    private String phone;

    @JsonIgnore
    @TableField("password_hash")
    private String passwordHash;

    private String nickname;

    @TableField("created_at")
    private OffsetDateTime createdAt;

    @TableField("updated_at")
    private OffsetDateTime updatedAt;
}
