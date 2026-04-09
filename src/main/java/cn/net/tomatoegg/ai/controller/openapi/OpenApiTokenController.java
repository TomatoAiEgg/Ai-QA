package cn.net.tomatoegg.ai.controller.openapi;

import cn.net.tomatoegg.ai.common.ApiCode;
import cn.net.tomatoegg.ai.common.ResponseUtils;
import cn.net.tomatoegg.ai.exception.BusinessException;
import cn.net.tomatoegg.ai.service.auth.AuthService;
import cn.net.tomatoegg.ai.service.openapi.OpenApiTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/open-api-tokens")
@RequiredArgsConstructor
public class OpenApiTokenController {

    private final OpenApiTokenService openApiTokenService;
    private final AuthService authService;

    @PostMapping
    public Map<String, Object> create(@RequestBody(required = false) Map<String, String> request) {
        try {
            String name = request == null ? null : request.get("name");
            return ResponseUtils.success(openApiTokenService.createToken(authService.getCurrentUserId(), name));
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ApiCode.OPEN_API_TOKEN_CREATE_FAILED, ex);
        }
    }

    @GetMapping
    public Map<String, Object> list() {
        try {
            return ResponseUtils.success(openApiTokenService.listTokens(authService.getCurrentUserId()));
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ApiCode.OPEN_API_TOKEN_LIST_FAILED, ex);
        }
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> revoke(@PathVariable UUID id) {
        try {
            openApiTokenService.revokeToken(authService.getCurrentUserId(), id);
            return ResponseUtils.success();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ApiCode.OPEN_API_TOKEN_REVOKE_FAILED, ex);
        }
    }
}
