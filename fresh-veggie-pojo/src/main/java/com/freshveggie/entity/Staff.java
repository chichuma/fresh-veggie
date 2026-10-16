package com.freshveggie.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 后台员工。
 *
 * <p>2026-10-01：时间字段由 {@code java.util.Date} 改为 {@link LocalDateTime}
 * 与其余实体统一（P2-10），并补 {@code @TableField(fill = ...)} 让审计填充生效（P2-1）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Staff implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    //主键
    private Long id;

    //姓名
    private String name;

    //用户名
    private String username;

    //密码（Bcrypt）。只用于服务端校验，绝不出现在任何 HTTP 响应里：
    // 原先 GET /admin/staff/{id} 与 /admin/staff/page 会把哈希直接吐给前端。
    @JsonIgnore
    private String password;

    //手机号
    private String phone;

    //性别 0男 1女
    private Integer sex;

    //身份证号
    private String idNumber;

    //角色 1管理员 2运营 3分拣员
    private Integer role;

    //状态 0:禁用，1:启用
    private Integer status;

    //创建时间
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    //更新时间
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    //创建人
    @TableField(fill = FieldFill.INSERT)
    private Long createUser;

    //修改人
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateUser;
}
