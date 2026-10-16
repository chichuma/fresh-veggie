package com.freshveggie.interceptor;

import com.freshveggie.constant.JwtClaimsConstant;
import com.freshveggie.context.BaseContext;
import com.freshveggie.exception.BusinessException;
import com.freshveggie.properties.JwtProperties;
import com.freshveggie.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;


/**
 * jwt令牌校验的拦截器
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class JwtAdminInterceptor implements HandlerInterceptor {

    private final JwtProperties jwtProperties;

    /**
     * 校验jwt
     *
     * @param request  HTTP请求
     * @param response HTTP响应
     * @param handler  当前被拦截到的处理器（Controller方法或其他资源）
     * @return true表示放行，false表示拦截
     * @throws Exception 当JWT解析失败或业务校验不通过时抛出
     */
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
        //判断当前拦截到的是Controller的方法还是其他资源
        if (!(handler instanceof HandlerMethod)) {
            //当前拦截到的不是动态方法，直接放行
            return true;
        }

        //1、从请求头中获取令牌
        String token = request.getHeader(jwtProperties.getAdminTokenName());

        //2、校验令牌
        try {
            log.info("jwt校验:{}", token);
            Claims claims = JwtUtil.parseJWT(jwtProperties.getAdminSecretKey(), token);
            Long empId = Long.valueOf(claims.get(JwtClaimsConstant.EMP_ID).toString());
            BaseContext.setCurrentId(empId);  // 保存当前登录的员工id到threadLocal中
            log.info("当前员工id：{}", empId);
            //3、通过，放行
            return true;
        } catch (ExpiredJwtException e) {
            throw new BusinessException("token已过期");
        } catch (Exception e){
            throw new BusinessException("token不合法");
        }
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler, Exception ex) {
        log.info("清除线程: {} 中的threadLocal变量", Thread.currentThread().getId());
        BaseContext.removeCurrentId(); // 清除避免内存溢出
    }
}
