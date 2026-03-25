package cn.net.tomatoegg.ai.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.SaTokenInfo;
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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
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

        try {
            StpUtil.login(user.getId().toString());
            adoptLegacyDataIfNeeded(user.getId());
            return cacheCurrentSession(user);
        } catch (Exception ex) {
            log.error("注册后初始化登录态失败, userId={}", user.getId(), ex);
            throw ex;
        }
    }

    public Map<String, Object> login(String account, String password) {
        if (account == null || account.isBlank() || password == null || password.isBlank()) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "账号和密码不能为空");
        }

        String trimmed = account.trim();
        if (trimmed.contains("@")) {
            if (!Validator.isEmail(trimmed)) {
                throw new BusinessException(ApiCode.BAD_REQUEST, "邮箱格式不正确");
            }
        } else if (!Validator.isMobile(trimmed)) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "手机号格式不正确");
        }
        AppUser user = trimmed.contains("@")
                ? findByEmail(normalizeEmail(trimmed))
                : findByPhone(normalizePhone(trimmed));
        if (user == null || !BCrypt.checkpw(password, user.getPasswordHash())) {
            throw new BusinessException(ApiCode.UNAUTHORIZED, "账号或密码错误");
        }

        try {
            StpUtil.login(user.getId().toString());
            adoptLegacyDataIfNeeded(user.getId());
            return cacheCurrentSession(user);
        } catch (Exception ex) {
            log.error("登录后初始化登录态失败, userId={}", user.getId(), ex);
            throw ex;
        }
    }

    public void logout() {
        if (StpUtil.isLogin()) {
            authSessionCacheService.clearSession(StpUtil.getTokenValue());
            StpUtil.logout();
        }
    }

    public Map<String, Object> getCurrentSession() {
        String tokenValue = StpUtil.getTokenValue();
        try {
            Map<String, Object> cachedSession = authSessionCacheService.getSession(tokenValue);
            if (cachedSession != null) {
                return cachedSession;
            }
        } catch (Exception ex) {
            log.warn("读取登录态缓存失败, token={}", tokenValue, ex);
            authSessionCacheService.clearSession(tokenValue);
        }
        UUID userId = getCurrentUserId();
        AppUser user = appUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ApiCode.UNAUTHORIZED, "用户不存在");
        }
        return cacheCurrentSession(user);
    }

    public UUID getCurrentUserId() {
        try {
            String loginId = StpUtil.getLoginIdAsString();
            if (loginId == null || loginId.isBlank()) {
                throw new BusinessException(ApiCode.UNAUTHORIZED, "请先登录");
            }
            return UUID.fromString(loginId);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ApiCode.UNAUTHORIZED, "登录态已失效", ex);
        }
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
