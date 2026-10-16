package com.freshveggie.config;

import com.freshveggie.interceptor.JwtAdminInterceptor;
import com.freshveggie.interceptor.JwtUserInterceptor;
import com.freshveggie.interceptor.TraceIdInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 配置类，注册web层相关组件
 */
@Configuration
@Slf4j
@RequiredArgsConstructor
public class WebMvcConfiguration implements WebMvcConfigurer {

    private final JwtAdminInterceptor jwtTokenAdminInterceptor;
    private final JwtUserInterceptor jwtTokenUserInterceptor;
    private final TraceIdInterceptor traceIdInterceptor;

    /**
     * 注册自定义拦截器
     *
     * @param registry Spring拦截器注册器
     */
    public void addInterceptors(InterceptorRegistry registry) {
        log.info("开始注册自定义拦截器...");
        // TraceId 拦截器放在最前面，所有请求都过
        registry.addInterceptor(traceIdInterceptor)
                .addPathPatterns("/**");

        registry.addInterceptor(jwtTokenAdminInterceptor)
                .addPathPatterns("/admin/**")
                .excludePathPatterns("/admin/staff/login");

        registry.addInterceptor(jwtTokenUserInterceptor)
                .addPathPatterns("/user/**")
                .excludePathPatterns("/user/user/login")
                .excludePathPatterns("/user/shop/status");
    }

    /**
     * 设置静态资源映射
     *
     * @param registry 静态资源注册器
     */
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/doc.html").addResourceLocations("classpath:/META-INF/resources/");
        registry.addResourceHandler("/webjars/**").addResourceLocations("classpath:/META-INF/resources/webjars/");
    }
}
