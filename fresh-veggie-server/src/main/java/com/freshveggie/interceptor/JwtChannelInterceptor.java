package com.freshveggie.interceptor;

import com.freshveggie.constant.JwtClaimsConstant;
import com.freshveggie.exception.BusinessException;
import com.freshveggie.properties.JwtProperties;
import com.freshveggie.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtChannelInterceptor implements ChannelInterceptor {
    private final JwtProperties jwtProperties;

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return null;
        }
        if (accessor.getCommand() != StompCommand.CONNECT) {
            return message;
        }
        Object authentication = accessor.getFirstNativeHeader(jwtProperties.getAdminTokenName());
        String token = null;
        if (authentication != null) {
            token = authentication.toString();
        }
        try {
            log.info("jwt校验:{}", token);
            Claims claims = JwtUtil.parseJWT(jwtProperties.getAdminSecretKey(), token);
            Long empId = Long.valueOf(claims.get(JwtClaimsConstant.EMP_ID).toString());
            accessor.setUser(new PreAuthenticatedAuthenticationToken(empId.toString(), null));
            log.info("当前员工id：{}", empId);
        } catch (ExpiredJwtException e) {
            throw new BusinessException("token已过期");
        } catch (Exception e){
            throw new BusinessException("token不合法");
        }
        return message;
    }
}
