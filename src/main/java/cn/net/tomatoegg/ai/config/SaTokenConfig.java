package cn.net.tomatoegg.ai.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

@Configuration
public class SaTokenConfig extends WebMvcConfig {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handler ->
                        SaRouter.match("/ai/**", "/api/**")
                                .notMatch("/api/auth/**")
                                .check(r -> StpUtil.checkLogin())))
                .addPathPatterns("/**");
    }
}
