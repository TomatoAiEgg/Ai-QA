package cn.net.tomatoegg.ai.service;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

@Service
public class AuthSessionCacheService {

    private static final String SESSION_CACHE_PREFIX = "auth:session:token:";

    private final RedisTemplate<String, Object> redisTemplate;

    public AuthSessionCacheService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getSession(String tokenValue) {
        if (tokenValue == null || tokenValue.isBlank()) {
            return null;
        }
        Object value = redisTemplate.opsForValue().get(buildKey(tokenValue));
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return null;
    }

    public void cacheSession(String tokenValue, Map<String, Object> payload, long timeoutSeconds) {
        if (tokenValue == null || tokenValue.isBlank() || payload == null) {
            return;
        }
        Duration ttl = timeoutSeconds > 0 ? Duration.ofSeconds(timeoutSeconds) : Duration.ofDays(30);
        redisTemplate.opsForValue().set(buildKey(tokenValue), payload, ttl);
    }

    public void clearSession(String tokenValue) {
        if (tokenValue == null || tokenValue.isBlank()) {
            return;
        }
        redisTemplate.delete(buildKey(tokenValue));
    }

    private String buildKey(String tokenValue) {
        return SESSION_CACHE_PREFIX + tokenValue;
    }
}
