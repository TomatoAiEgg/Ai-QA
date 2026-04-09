package cn.net.tomatoegg.ai.service.openapi;

import cn.net.tomatoegg.ai.common.ApiCode;
import cn.net.tomatoegg.ai.entity.OpenApiToken;
import cn.net.tomatoegg.ai.exception.BusinessException;
import cn.net.tomatoegg.ai.mapper.OpenApiTokenMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenApiTokenService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_REVOKED = "REVOKED";
    private static final String TOKEN_HASH_PREFIX = "sha256:";
    private static final String TOKEN_REDACTED_TEXT = "仅创建时展示完整令牌";
    private static final SecureRandom TOKEN_RANDOM = new SecureRandom();

    private final OpenApiTokenMapper openApiTokenMapper;

    public Map<String, Object> createToken(UUID userId, String name) {
        String resolvedName = name == null || name.isBlank() ? "默认开放接口令牌" : name.trim();
        String rawToken = generateRawToken();

        OpenApiToken entity = OpenApiToken.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .name(resolvedName)
                .token(hashToken(rawToken))
                .status(STATUS_ACTIVE)
                .build();
        openApiTokenMapper.insert(entity);
        log.info("创建开放接口令牌成功, userId={}, tokenId={}, name={}", userId, entity.getId(), entity.getName());

        return Map.of(
                "id", entity.getId().toString(),
                "name", entity.getName(),
                "token", rawToken,
                "status", entity.getStatus(),
                "tokenVisibleOnce", true
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
                        "token", describeTokenForList(item.getToken()),
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
        log.info("废弃开放接口令牌成功, userId={}, tokenId={}", userId, tokenId);
    }

    public UUID authenticate(String tokenValue) {
        String normalizedToken = normalizeToken(tokenValue);
        if (normalizedToken == null) {
            throw new BusinessException(ApiCode.UNAUTHORIZED, "缺少开放接口令牌");
        }

        OpenApiToken token = findActiveTokenByStoredValue(hashToken(normalizedToken));
        if (token == null) {
            OpenApiToken legacyToken = findActiveTokenByStoredValue(normalizedToken);
            if (legacyToken == null) {
                log.warn("开放接口令牌认证失败, token={}", maskToken(normalizedToken));
                throw new BusinessException(ApiCode.UNAUTHORIZED, "开放接口令牌无效");
            }

            upgradeLegacyToken(legacyToken, normalizedToken);
            token = legacyToken;
        }
        log.info("开放接口令牌认证成功, userId={}, tokenId={}", token.getUserId(), token.getId());
        return token.getUserId();
    }

    private OpenApiToken findActiveTokenByStoredValue(String storedTokenValue) {
        return openApiTokenMapper.selectOne(new LambdaQueryWrapper<OpenApiToken>()
                .eq(OpenApiToken::getToken, storedTokenValue)
                .eq(OpenApiToken::getStatus, STATUS_ACTIVE)
                .last("LIMIT 1"));
    }

    private void upgradeLegacyToken(OpenApiToken token, String rawToken) {
        openApiTokenMapper.update(null, new LambdaUpdateWrapper<OpenApiToken>()
                .eq(OpenApiToken::getId, token.getId())
                .eq(OpenApiToken::getToken, rawToken)
                .set(OpenApiToken::getToken, hashToken(rawToken))
                .setSql("updated_at = NOW()"));
        log.info("已将开放接口令牌升级为摘要存储, tokenId={}", token.getId());
    }

    private String describeTokenForList(String storedToken) {
        if (storedToken == null || storedToken.isBlank()) {
            return "";
        }
        if (isHashedToken(storedToken)) {
            return TOKEN_REDACTED_TEXT;
        }
        return maskToken(storedToken);
    }

    private String maskToken(String token) {
        if (token == null || token.length() <= 12) {
            return token == null ? "" : token;
        }
        return token.substring(0, 8) + "..." + token.substring(token.length() - 4);
    }

    private String normalizeToken(String tokenValue) {
        if (tokenValue == null || tokenValue.isBlank()) {
            return null;
        }
        return tokenValue.trim();
    }

    private String generateRawToken() {
        byte[] randomBytes = new byte[32];
        TOKEN_RANDOM.nextBytes(randomBytes);
        return "aiqa_" + HexFormat.of().formatHex(randomBytes);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return TOKEN_HASH_PREFIX + HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new BusinessException(ApiCode.SERVER_ERROR, "生成开放接口令牌摘要失败", ex);
        }
    }

    private boolean isHashedToken(String storedToken) {
        return storedToken != null && storedToken.startsWith(TOKEN_HASH_PREFIX);
    }
}
