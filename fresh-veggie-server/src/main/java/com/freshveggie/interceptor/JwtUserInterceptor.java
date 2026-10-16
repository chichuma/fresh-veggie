package com.freshveggie.interceptor;

import com.freshveggie.context.BaseContext;
import com.freshveggie.exception.BusinessException;
import com.freshveggie.properties.JwtProperties;
import com.freshveggie.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;


@Component
@Slf4j
@RequiredArgsConstructor
public class JwtUserInterceptor implements HandlerInterceptor {

    private final JwtProperties jwtProperties;

    @Override
    @SneakyThrows()
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        String authentication = request.getHeader(jwtProperties.getUserTokenName());

        try {
            log.info("jwt校验:{}", authentication);
            Claims claims = JwtUtil.parseJWT(jwtProperties.getUserSecretKey(), authentication);
            Long userId = claims.get("userId", Long.class);
            String openid = claims.get("openid", String.class);
            log.info("当前小程序用户ID：{}, openid: {}", userId, openid);
            BaseContext.setCurrentId(userId);
            return true;
        } catch (ExpiredJwtException e) {
            throw new BusinessException("token已过期");
        } catch (Exception e){
            throw new BusinessException("token不合法");
        }
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler, Exception ex) {
        BaseContext.removeCurrentId();
    }
}
