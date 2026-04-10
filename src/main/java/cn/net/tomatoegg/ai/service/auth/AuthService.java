package cn.net.tomatoegg.ai.service.auth;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.lang.Validator;
import cn.hutool.crypto.digest.BCrypt;
import cn.net.tomatoegg.ai.common.ApiCode;
import cn.net.tomatoegg.ai.entity.AppUser;
import cn.net.tomatoegg.ai.entity.Conversation;
import cn.net.tomatoegg.ai.entity.KnowledgeBase;
import cn.net.tomatoegg.ai.entity.KnowledgeBaseDocument;
import cn.net.tomatoegg.ai.exception.BusinessException;
import cn.net.tomatoegg.ai.mapper.AppUserMapper;
import cn.net.tomatoegg.ai.mapper.ConversationMapper;
import cn.net.tomatoegg.ai.mapper.KnowledgeBaseDocumentMapper;
import cn.net.tomatoegg.ai.mapper.KnowledgeBaseMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int MAX_NICKNAME_LENGTH = 128;

    private final AppUserMapper appUserMapper;
    private final ConversationMapper conversationMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeBaseDocumentMapper knowledgeBaseDocumentMapper;
    private final AuthSessionCacheService authSessionCacheService;
    private final AuthCaptchaService authCaptchaService;

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> register(String email, String password, String nickname,
                                        String captchaId, String captchaCode) {
        String normalizedEmail = normalizeEmail(email);
        validateRegisterInput(normalizedEmail, password);
        authCaptchaService.verifyRegisterCaptcha(captchaId, captchaCode);

        if (findByEmail(normalizedEmail) != null) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "Email is already registered");
        }

        AppUser user = AppUser.builder()
                .id(UUID.randomUUID())
                .email(normalizedEmail)
                .phone(null)
                .passwordHash(BCrypt.hashpw(password))
                .nickname(resolveNickname(nickname, normalizedEmail))
                .build();
        appUserMapper.insert(user);
        log.info("Registered user successfully, userId={}, email={}", user.getId(), user.getEmail());

        try {
            StpUtil.login(user.getId().toString());
            adoptLegacyDataIfNeeded(user.getId());
            return cacheCurrentSession(user);
        } catch (Exception ex) {
            rollbackLoginSession(user.getId());
            log.error("Failed to initialize login session after register, userId={}", user.getId(), ex);
            throw new BusinessException(ApiCode.REGISTER_FAILED, "Failed to initialize session after register", ex);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> login(String email, String password, String captchaId, String captchaCode) {
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "Email and password are required");
        }

        String normalizedEmail = normalizeEmail(email);
        if (!Validator.isEmail(normalizedEmail)) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "Invalid email format");
        }

        authCaptchaService.verifyLoginCaptcha(captchaId, captchaCode);

        AppUser user = findByEmail(normalizedEmail);
        if (user == null || !BCrypt.checkpw(password, user.getPasswordHash())) {
            log.warn("Login failed, email={}", normalizedEmail);
            throw new BusinessException(ApiCode.UNAUTHORIZED, "Email or password is incorrect");
        }

        try {
            StpUtil.login(user.getId().toString());
            adoptLegacyDataIfNeeded(user.getId());
            log.info("Login successful, userId={}, email={}", user.getId(), normalizedEmail);
            return cacheCurrentSession(user);
        } catch (Exception ex) {
            rollbackLoginSession(user.getId());
            log.error("Failed to initialize login session after login, userId={}", user.getId(), ex);
            throw new BusinessException(ApiCode.LOGIN_FAILED, "Failed to initialize session after login", ex);
        }
    }

    public void logout() {
        if (StpUtil.isLogin()) {
            UUID userId = getCurrentUserId();
            String tokenValue = StpUtil.getTokenValue();
            try {
                StpUtil.logout();
                log.info("Logout successful, userId={}", userId);
            } finally {
                clearSessionQuietly(tokenValue, userId);
            }
        }
    }

    public Map<String, Object> getCurrentSession() {
        String tokenValue = StpUtil.getTokenValue();
        try {
            Map<String, Object> cachedSession = authSessionCacheService.getSession(tokenValue);
            if (cachedSession != null) {
                log.info("Session cache hit, token={}", tokenValue);
                return cachedSession;
            }
        } catch (Exception ex) {
            log.warn("Failed to read session cache, token={}", tokenValue, ex);
            authSessionCacheService.clearSession(tokenValue);
        }

        UUID userId = getCurrentUserId();
        AppUser user = appUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ApiCode.UNAUTHORIZED, "User does not exist");
        }

        log.info("Session cache missed, restoring session from database, userId={}", userId);
        return cacheCurrentSession(user);
    }

    public Map<String, Object> getRegisterCaptcha() {
        return authCaptchaService.generateRegisterCaptcha();
    }

    public Map<String, Object> getLoginCaptcha() {
        return authCaptchaService.generateLoginCaptcha();
    }

    public Map<String, Object> checkEmailAvailability(String email) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail == null || !Validator.isEmail(normalizedEmail)) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "Invalid email format");
        }

        boolean registered = findByEmail(normalizedEmail) != null;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("email", normalizedEmail);
        payload.put("registered", registered);
        payload.put("available", !registered);
        return payload;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> updateNickname(String nickname) {
        UUID userId = getCurrentUserId();
        String normalizedNickname = normalizeNickname(nickname);

        appUserMapper.update(null, new LambdaUpdateWrapper<AppUser>()
                .eq(AppUser::getId, userId)
                .set(AppUser::getNickname, normalizedNickname)
                .setSql("updated_at = NOW()"));

        AppUser updatedUser = appUserMapper.selectById(userId);
        if (updatedUser == null) {
            throw new BusinessException(ApiCode.UNAUTHORIZED, "User does not exist");
        }

        return cacheCurrentSession(updatedUser);
    }

    public UUID getCurrentUserId() {
        try {
            String loginId = StpUtil.getLoginIdAsString();
            if (loginId == null || loginId.isBlank()) {
                throw new BusinessException(ApiCode.UNAUTHORIZED, "Please login first");
            }
            return UUID.fromString(loginId);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ApiCode.UNAUTHORIZED, "Login session is invalid", ex);
        }
    }

    private void validateRegisterInput(String email, String password) {
        if (email == null || email.isBlank()) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "Email is required");
        }
        if (!Validator.isEmail(email)) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "Invalid email format");
        }
        if (password == null || password.length() < 6) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "Password must be at least 6 characters");
        }
    }

    private AppUser findByEmail(String email) {
        if (email == null) {
            return null;
        }
        return appUserMapper.selectOne(new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getEmail, email)
                .last("LIMIT 1"));
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase();
    }

    private String resolveNickname(String nickname, String email) {
        if (nickname != null && !nickname.isBlank()) {
            return nickname.trim();
        }
        return email;
    }

    private String normalizeNickname(String nickname) {
        if (nickname == null) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "Nickname is required");
        }

        String normalizedNickname = nickname.trim();
        if (normalizedNickname.isEmpty()) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "Nickname is required");
        }
        if (normalizedNickname.length() > MAX_NICKNAME_LENGTH) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "Nickname must be at most 128 characters");
        }
        return normalizedNickname;
    }

    private Map<String, Object> buildSessionPayload(AppUser user) {
        String tokenValue = resolveTokenValue(user.getId());

        Map<String, Object> userPayload = new LinkedHashMap<>();
        userPayload.put("id", user.getId().toString());
        userPayload.put("email", user.getEmail() == null ? "" : user.getEmail());
        userPayload.put("phone", user.getPhone() == null ? "" : user.getPhone());
        userPayload.put("nickname", user.getNickname());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("user", userPayload);
        payload.put("tokenName", StpUtil.getTokenName());
        payload.put("tokenValue", tokenValue == null ? "" : tokenValue);
        return payload;
    }

    private Map<String, Object> cacheCurrentSession(AppUser user) {
        Map<String, Object> payload = buildSessionPayload(user);
        String tokenValue = resolveTokenValue(user.getId());
        if (tokenValue != null && !tokenValue.isBlank()) {
            authSessionCacheService.cacheSession(tokenValue, payload, StpUtil.getTokenTimeout());
            log.info("Session cached successfully, userId={}, token={}", user.getId(), tokenValue);
        }
        return payload;
    }

    private String resolveTokenValue(UUID userId) {
        SaTokenInfo tokenInfo = StpUtil.getTokenInfo();
        if (tokenInfo != null && tokenInfo.getTokenValue() != null && !tokenInfo.getTokenValue().isBlank()) {
            return tokenInfo.getTokenValue();
        }
        return StpUtil.getTokenValueByLoginId(userId.toString());
    }

    private void adoptLegacyDataIfNeeded(UUID userId) {
        Long userCount = appUserMapper.selectCount(null);
        if (userCount == null || userCount != 1) {
            return;
        }

        log.info("Detected first user login, adopting legacy data, userId={}", userId);
        conversationMapper.update(null, new LambdaUpdateWrapper<Conversation>()
                .isNull(Conversation::getUserId)
                .set(Conversation::getUserId, userId));
        knowledgeBaseMapper.update(null, new LambdaUpdateWrapper<KnowledgeBase>()
                .isNull(KnowledgeBase::getCreatedBy)
                .set(KnowledgeBase::getCreatedBy, userId)
                .setSql("updated_at = NOW()"));
        knowledgeBaseDocumentMapper.update(null, new LambdaUpdateWrapper<KnowledgeBaseDocument>()
                .isNull(KnowledgeBaseDocument::getUserId)
                .set(KnowledgeBaseDocument::getUserId, userId)
                .setSql("updated_at = NOW()"));
    }

    private void rollbackLoginSession(UUID userId) {
        String tokenValue = null;
        try {
            tokenValue = resolveTokenValue(userId);
        } catch (Exception ex) {
            log.warn("Failed to resolve token when rolling back login, userId={}", userId, ex);
        }

        try {
            if (StpUtil.isLogin()) {
                StpUtil.logout();
            }
        } catch (Exception ex) {
            log.warn("Failed to rollback login session, userId={}", userId, ex);
        } finally {
            clearSessionQuietly(tokenValue, userId);
        }
    }

    private void clearSessionQuietly(String tokenValue, UUID userId) {
        if (tokenValue == null || tokenValue.isBlank()) {
            return;
        }
        try {
            authSessionCacheService.clearSession(tokenValue);
        } catch (Exception ex) {
            log.warn("Failed to clear session cache, userId={}, token={}", userId, tokenValue, ex);
        }
    }
}
