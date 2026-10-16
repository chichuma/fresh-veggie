package com.freshveggie.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    //主键
    private Long id;

    //微信用户唯一标识
    private String openid;

    //昵称
    private String nickname;

    //手机号
    private String phone;

    //头像
    private String avatar;

    private LocalDateTime createTime;
}