package cn.net.tomatoegg.ai.controller.auth;

import cn.dev33.satoken.exception.SaTokenException;
import cn.net.tomatoegg.ai.common.ApiCode;
import cn.net.tomatoegg.ai.common.ResponseUtils;
import cn.net.tomatoegg.ai.exception.BusinessException;
import cn.net.tomatoegg.ai.service.auth.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody Map<String, String> request) {
        try {
            return ResponseUtils.success(authService.register(
                    request.get("email"),
                    request.get("password"),
                    request.get("nickname"),
                    request.get("captchaId"),
                    request.get("captchaCode")
            ));
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ApiCode.REGISTER_FAILED, ex);
        }
    }

    @GetMapping("/register-captcha")
    public Map<String, Object> registerCaptcha() {
        try {
            return ResponseUtils.success(authService.getRegisterCaptcha());
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ApiCode.SERVER_ERROR, "Failed to get register captcha", ex);
        }
    }

    @GetMapping("/login-captcha")
    public Map<String, Object> loginCaptcha() {
        try {
            return ResponseUtils.success(authService.getLoginCaptcha());
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ApiCode.SERVER_ERROR, "Failed to get login captcha", ex);
        }
    }

    @GetMapping("/email-availability")
    public Map<String, Object> emailAvailability(@RequestParam("email") String email) {
        try {
            return ResponseUtils.success(authService.checkEmailAvailability(email));
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ApiCode.SERVER_ERROR, "Failed to check email availability", ex);
        }
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> request) {
        try {
            return ResponseUtils.success(authService.login(
                    request.get("email"),
                    request.get("password"),
                    request.get("captchaId"),
                    request.get("captchaCode")
            ));
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ApiCode.LOGIN_FAILED, ex);
        }
    }

    @PostMapping("/nickname")
    public Map<String, Object> updateNickname(@RequestBody Map<String, String> request) {
        try {
            return ResponseUtils.success(authService.updateNickname(request.get("nickname")));
        } catch (SaTokenException ex) {
            throw ex;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ApiCode.SERVER_ERROR, "Failed to update nickname", ex);
        }
    }

    @PostMapping("/logout")
    public Map<String, Object> logout() {
        try {
            authService.logout();
            return ResponseUtils.success();
        } catch (SaTokenException ex) {
            throw ex;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ApiCode.LOGOUT_FAILED, ex);
        }
    }

    @GetMapping("/session")
    public Map<String, Object> session() {
        try {
            return ResponseUtils.success(authService.getCurrentSession());
        } catch (SaTokenException ex) {
            throw ex;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ApiCode.SESSION_QUERY_FAILED, ex);
        }
    }
}
