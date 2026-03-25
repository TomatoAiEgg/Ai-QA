package cn.net.tomatoegg.ai.service;

import cn.net.tomatoegg.ai.common.ApiCode;
import cn.net.tomatoegg.ai.entity.OpenApiToken;
import cn.net.tomatoegg.ai.exception.BusinessException;
import cn.net.tomatoegg.ai.mapper.OpenApiTokenMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OpenApiTokenService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_REVOKED = "REVOKED";

    private final OpenApiTokenMapper openApiTokenMapper;

    public Map<String, Object> createToken(UUID userId, String name) {
        String resolvedName = name == null || name.isBlank() ? "默认开放接口令牌" : name.trim();
        String rawToken = "aiqa_" + UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");

        OpenApiToken entity = OpenApiToken.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .name(resolvedName)
                .token(rawToken)
                .status(STATUS_ACTIVE)
                .build();
        openApiTokenMapper.insert(entity);

        return Map.of(
                "id", entity.getId().toString(),
                "name", entity.getName(),
                "token", rawToken,
                "status", entity.getStatus()
        );
    }

    public List<Map<String, Object>> listTokens(UUID userId) {
        return openApiTokenMapper.selectList(new LambdaQueryWrapper<OpenApiToken>()
                        .eq(OpenApiToken::getUserId, userId)
                        .orderByDesc(OpenApiToken::getCreatedAt))
                .stream()
                .map(item -> Map.<String, Object>of(
                        "id", item.getId().toString(),
                        "name", item.getName(),
                        "token", maskToken(item.getToken()),
                        "status", item.getStatus(),
                        "createdAt", item.getCreatedAt(),
                        "updatedAt", item.getUpdatedAt()
                ))
                .toList();
    }

    public void revokeToken(UUID userId, UUID tokenId) {
        OpenApiToken token = openApiTokenMapper.selectOne(new LambdaQueryWrapper<OpenApiToken>()
                .eq(OpenApiToken::getId, tokenId)
                .eq(OpenApiToken::getUserId, userId)
                .last("LIMIT 1"));
        if (token == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "开放接口令牌不存在");
        }

        openApiTokenMapper.update(null, new LambdaUpdateWrapper<OpenApiToken>()
                .eq(OpenApiToken::getId, tokenId)
                .eq(OpenApiToken::getUserId, userId)
                .set(OpenApiToken::getStatus, STATUS_REVOKED)
                .setSql("updated_at = NOW()"));
    }

    public UUID authenticate(String tokenValue) {
        if (tokenValue == null || tokenValue.isBlank()) {
            throw new BusinessException(ApiCode.UNAUTHORIZED, "缺少开放接口令牌");
        }

        OpenApiToken token = openApiTokenMapper.selectOne(new LambdaQueryWrapper<OpenApiToken>()
                .eq(OpenApiToken::getToken, tokenValue)
                .eq(OpenApiToken::getStatus, STATUS_ACTIVE)
                .last("LIMIT 1"));
        if (token == null) {
            throw new BusinessException(ApiCode.UNAUTHORIZED, "开放接口令牌无效");
        }
        return token.getUserId();
    }

    private String maskToken(String token) {
        if (token == null || token.length() <= 12) {
            return token == null ? "" : token;
        }
        return token.substring(0, 8) + "..." + token.substring(token.length() - 4);
    }
}
