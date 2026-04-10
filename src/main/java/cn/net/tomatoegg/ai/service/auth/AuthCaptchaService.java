package cn.net.tomatoegg.ai.service.auth;

import cn.net.tomatoegg.ai.common.ApiCode;
import cn.net.tomatoegg.ai.exception.BusinessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class AuthCaptchaService {

    private static final Duration CAPTCHA_TTL = Duration.ofMinutes(5);
    private static final int CAPTCHA_LENGTH = 5;
    private static final String CAPTCHA_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final String[] CAPTCHA_COLORS = {
            "#0F4C81", "#155E75", "#1D4ED8", "#7C2D12", "#7C3AED", "#B45309"
    };

    private final RedisTemplate<String, Object> redisTemplate;

    public AuthCaptchaService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Map<String, Object> generateRegisterCaptcha() {
        return generateCaptcha("register");
    }

    public Map<String, Object> generateLoginCaptcha() {
        return generateCaptcha("login");
    }

    public void verifyRegisterCaptcha(String captchaId, String captchaCode) {
        verifyCaptcha("register", captchaId, captchaCode);
    }

    public void verifyLoginCaptcha(String captchaId, String captchaCode) {
        verifyCaptcha("login", captchaId, captchaCode);
    }

    private Map<String, Object> generateCaptcha(String scene) {
        String captchaId = UUID.randomUUID().toString();
        String captchaCode = randomCaptchaCode();
        redisTemplate.opsForValue().set(buildKey(scene, captchaId), captchaCode, CAPTCHA_TTL);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("captchaId", captchaId);
        payload.put("captchaImage", buildCaptchaImage(captchaCode));
        payload.put("expiresInSeconds", CAPTCHA_TTL.toSeconds());
        return payload;
    }

    private void verifyCaptcha(String scene, String captchaId, String captchaCode) {
        if (captchaId == null || captchaId.isBlank()) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "请输入验证码标识");
        }
        if (captchaCode == null || captchaCode.isBlank()) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "请输入验证码");
        }

        String key = buildKey(scene, captchaId.trim());
        Object cachedValue = redisTemplate.opsForValue().get(key);
        if (!(cachedValue instanceof String expectedCode) || expectedCode.isBlank()) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "验证码已过期，请刷新后重试");
        }

        redisTemplate.delete(key);
        if (!expectedCode.equalsIgnoreCase(captchaCode.trim())) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "验证码错误，请重新输入");
        }
    }

    private String randomCaptchaCode() {
        StringBuilder builder = new StringBuilder(CAPTCHA_LENGTH);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < CAPTCHA_LENGTH; i++) {
            builder.append(CAPTCHA_CHARS.charAt(random.nextInt(CAPTCHA_CHARS.length())));
        }
        return builder.toString();
    }

    private String buildCaptchaImage(String captchaCode) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        StringBuilder svg = new StringBuilder(1024);
        svg.append("<svg xmlns='http://www.w3.org/2000/svg' width='132' height='44' viewBox='0 0 132 44'>");
        svg.append("<defs><linearGradient id='bg' x1='0%' x2='100%' y1='0%' y2='100%'>");
        svg.append("<stop offset='0%' stop-color='#F8FBFF'/>");
        svg.append("<stop offset='100%' stop-color='#E7EEF9'/></linearGradient></defs>");
        svg.append("<rect width='132' height='44' rx='12' fill='url(#bg)'/>");

        for (int i = 0; i < 6; i++) {
            svg.append("<line x1='").append(random.nextInt(132))
                    .append("' y1='").append(random.nextInt(44))
                    .append("' x2='").append(random.nextInt(132))
                    .append("' y2='").append(random.nextInt(44))
                    .append("' stroke='rgba(59,130,246,0.18)' stroke-width='1.2'/>");
        }

        for (int i = 0; i < 10; i++) {
            svg.append("<circle cx='").append(random.nextInt(132))
                    .append("' cy='").append(random.nextInt(44))
                    .append("' r='").append(1 + random.nextInt(2))
                    .append("' fill='rgba(37,99,235,0.14)'/>");
        }

        int startX = 16;
        for (int i = 0; i < captchaCode.length(); i++) {
            int x = startX + i * 22 + random.nextInt(4);
            int y = 30 + random.nextInt(5);
            int rotate = random.nextInt(-18, 19);
            String color = CAPTCHA_COLORS[random.nextInt(CAPTCHA_COLORS.length)];
            svg.append("<text x='").append(x)
                    .append("' y='").append(y)
                    .append("' fill='").append(color)
                    .append("' font-family='Verdana, Arial, sans-serif' font-size='24' font-weight='700'")
                    .append(" transform='rotate(").append(rotate).append(' ').append(x).append(' ').append(y).append(")'>")
                    .append(captchaCode.charAt(i))
                    .append("</text>");
        }

        svg.append("</svg>");
        String encoded = Base64.getEncoder().encodeToString(svg.toString().getBytes(StandardCharsets.UTF_8));
        return "data:image/svg+xml;base64," + encoded;
    }

    private String buildKey(String scene, String captchaId) {
        return "auth:" + scene + ":captcha:" + captchaId;
    }
}
