package com.freshveggie.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.MacAlgorithm;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

public class JwtUtil {
    /**
     * 生成jwt
     * 使用Hs256算法, 私匙使用固定秘钥
     *
     * @param secretKey jwt秘钥
     * @param ttlMillis jwt过期时间(毫秒)
     * @param claims    设置的信息
     * @return String  tokens字符串
     */
    public static String createJWT(String secretKey,long ttlMillis, Map<String, Object> claims) {
        // jwt 加密算法
        MacAlgorithm signatureAlgorithm = Jwts.SIG.HS256;
        // jwt 过期时间
        Date exp = new Date(System.currentTimeMillis() + ttlMillis);
        // 直接使用普通字符串
        SecretKey key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));

        JwtBuilder jwtBuilder = Jwts.builder()
                // head
                // payload
                .claims(claims)
                .expiration(exp)
                // sign
                .signWith(key, signatureAlgorithm);
        return jwtBuilder.compact();
    }


    /**
     * Token解密
     *
     * @param secretKey jwt秘钥 此秘钥一定要保留好在服务端, 不能暴露出去, 否则sign就可以被伪造, 如果对接多个客户端建议改造成多个
     * @param token     加密后的token
     * @return claims 负载信息
     */
    public static Claims parseJWT(String secretKey, String token) {
        // 得到DefaultJwtParser
        return Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
