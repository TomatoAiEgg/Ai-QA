package cn.net.tomatoegg.ai.service;

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
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AppUserMapper appUserMapper;
    private final ConversationMapper conversationMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeBaseDocumentMapper knowledgeBaseDocumentMapper;
    private final AuthSessionCacheService authSessionCacheService;

    public Map<String, Object> register(String email, String phone, String password, String nickname) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedPhone = normalizePhone(phone);
        validateRegisterInput(normalizedEmail, normalizedPhone, password);

        if (normalizedEmail != null && findByEmail(normalizedEmail) != null) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "该邮箱已注册");
        }
        if (normalizedPhone != null && findByPhone(normalizedPhone) != null) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "该手机号已注册");
        }

        AppUser user = AppUser.builder()
                .id(UUID.randomUUID())
                .email(normalizedEmail)
                .phone(normalizedPhone)
                .passwordHash(BCrypt.hashpw(password))
                .nickname(resolveNickname(nickname, normalizedEmail, normalizedPhone))
                .build();
        appUserMapper.insert(user);

        StpUtil.login(user.getId().toString());
        adoptLegacyDataIfNeeded(user.getId());
        return cacheCurrentSession(user);
    }

    public Map<String, Object> login(String account, String password) {
        if (account == null || account.isBlank() || password == null || password.isBlank()) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "账号和密码不能为空");
        }

        String trimmed = account.trim();
        AppUser user = trimmed.contains("@")
                ? findByEmail(normalizeEmail(trimmed))
                : findByPhone(normalizePhone(trimmed));
        if (user == null || !BCrypt.checkpw(password, user.getPasswordHash())) {
            throw new BusinessException(ApiCode.UNAUTHORIZED, "账号或密码错误");
        }

        StpUtil.login(user.getId().toString());
        adoptLegacyDataIfNeeded(user.getId());
        return cacheCurrentSession(user);
    }

    public void logout() {
        if (StpUtil.isLogin()) {
            authSessionCacheService.clearSession(StpUtil.getTokenValue());
            StpUtil.logout();
        }
    }

    public Map<String, Object> getCurrentSession() {
        String tokenValue = StpUtil.getTokenValue();
        Map<String, Object> cachedSession = authSessionCacheService.getSession(tokenValue);
        if (cachedSession != null) {
            return cachedSession;
        }
        UUID userId = getCurrentUserId();
        AppUser user = appUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ApiCode.UNAUTHORIZED, "用户不存在");
        }
        return cacheCurrentSession(user);
    }

    public UUID getCurrentUserId() {
        return UUID.fromString(StpUtil.getLoginIdAsString());
    }

    private void validateRegisterInput(String email, String phone, String password) {
        if ((email == null || email.isBlank()) && (phone == null || phone.isBlank())) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "邮箱或手机号至少填写一个");
        }
        if (password == null || password.length() < 6) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "密码长度不能少于 6 位");
        }
        if (email != null && !Validator.isEmail(email)) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "邮箱格式不正确");
        }
        if (phone != null && !Validator.isMobile(phone)) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "手机号格式不正确");
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

    private AppUser findByPhone(String phone) {
        if (phone == null) {
            return null;
        }
        return appUserMapper.selectOne(new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getPhone, phone)
                .last("LIMIT 1"));
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase();
    }

    private String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        return phone.trim();
    }

    private String resolveNickname(String nickname, String email, String phone) {
        if (nickname != null && !nickname.isBlank()) {
            return nickname.trim();
        }
        if (email != null) {
            return email;
        }
        return phone;
    }

    private Map<String, Object> buildSessionPayload(AppUser user) {
        return Map.of(
                "user", Map.of(
                        "id", user.getId().toString(),
                        "email", user.getEmail() == null ? "" : user.getEmail(),
                        "phone", user.getPhone() == null ? "" : user.getPhone(),
                        "nickname", user.getNickname()
                ),
                "tokenName", StpUtil.getTokenName(),
                "tokenValue", StpUtil.getTokenValue()
        );
    }

    private Map<String, Object> cacheCurrentSession(AppUser user) {
        Map<String, Object> payload = buildSessionPayload(user);
        authSessionCacheService.cacheSession(StpUtil.getTokenValue(), payload, StpUtil.getTokenTimeout());
        return payload;
    }

    private void adoptLegacyDataIfNeeded(UUID userId) {
        Long userCount = appUserMapper.selectCount(null);
        if (userCount == null || userCount != 1) {
            return;
        }
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
}
