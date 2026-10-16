package com.freshveggie.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freshveggie.constant.MessageConstant;
import com.freshveggie.constant.WeChatConstant;
import com.freshveggie.entity.User;
import com.freshveggie.exception.LoginFailedException;
import com.freshveggie.mapper.UserMapper;
import com.freshveggie.properties.JwtProperties;
import com.freshveggie.properties.WeChatProperties;
import com.freshveggie.service.UserService;
import com.freshveggie.utils.HttpClientUtil;
import com.freshveggie.utils.JwtUtil;
import com.freshveggie.vo.UserLoginVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final WeChatProperties weChatProperties;
    private final JwtProperties jwtProperties;
    private final UserMapper userMapper;

    @Override
    public UserLoginVO login(String code) {
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put(WeChatConstant.PARAM_APPID, weChatProperties.getAppid());
        queryParams.put(WeChatConstant.PARAM_SECRET, weChatProperties.getSecret());
        queryParams.put(WeChatConstant.PARAM_JS_CODE, code);
        queryParams.put(WeChatConstant.PARAM_GRANT_TYPE, WeChatConstant.GRANT_TYPE_AUTHORIZATION_CODE);
        String response = HttpClientUtil.doGet(WeChatConstant.WECHAT_SERVER_LOGIN_URL, queryParams);
        JsonNode jsonObject;
        try {
            jsonObject = OBJECT_MAPPER.readTree(response);
        } catch (JsonProcessingException exception) {
            jsonObject = null;
            log.error("用户登录视图转JSON异常: ", exception);
        }
        JsonNode openidNode = jsonObject == null ? null : jsonObject.get("openid");
        String openid = openidNode == null ? null : openidNode.asText();
        if (openid == null) {
            throw new LoginFailedException(MessageConstant.LOGIN_FAILED);
        }

        User user = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getOpenid, openid));
        if (user == null) {
            user = User.builder()
                    .openid(openid)
                    .createTime(LocalDateTime.now())
                    .build();
            userMapper.insert(user);
        }

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("openid", openid);
        String token = JwtUtil.createJWT(jwtProperties.getUserSecretKey(), jwtProperties.getUserTtl(), claims);

        return UserLoginVO.builder()
                .id(user.getId())
                .openid(openid)
                .token(token)
                .build();
    }
}
