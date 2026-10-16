package com.freshveggie.controller.user;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.freshveggie.dto.UserLoginDTO;
import com.freshveggie.result.Result;
import com.freshveggie.service.UserService;
import com.freshveggie.vo.UserLoginVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
@Tag(name = "小程序用户相关接口")
@RequestMapping("/user/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/login")
    @Operation(description = "微信用户登录")
    public Result<UserLoginVO> login(@RequestBody @Valid UserLoginDTO userLoginDTO) throws JsonProcessingException {
        log.info("微信用户登录：{}", userLoginDTO.getCode());
        UserLoginVO userLoginVO = userService.login(userLoginDTO.getCode());
        return Result.success(userLoginVO);
    }
}
