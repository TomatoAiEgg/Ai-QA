package cn.net.tomatoegg.ai.config;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

@Configuration
public class SaTokenConfig extends WebMvcConfig {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
                    @Override
                    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                        if (request.getDispatcherType() == DispatcherType.ASYNC) {
                            return true;
                        }

                        String uri = request.getRequestURI();
                        if (isExcludedPath(uri) || !requiresLogin(uri)) {
                            return true;
                        }

                        if (!StpUtil.isLogin()) {
                            throw new NotLoginException("请先登录", null, null);
                        }
                        return true;
                    }
                })
                .addPathPatterns("/**");
    }

    private boolean requiresLogin(String uri) {
        return uri.startsWith("/ai/") || uri.startsWith("/api/");
    }

    private boolean isExcludedPath(String uri) {
        return uri.startsWith("/api/auth/") || uri.startsWith("/openapi/");
    }
}
