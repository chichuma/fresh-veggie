package com.freshveggie.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 商品分类。
 *
 * <h3>2026-10-01 两处修正</h3>
 * <ol>
 *   <li><b>时间字段由 {@code java.util.Date} 改为 {@link LocalDateTime}</b>，
 *       与其余 7 个实体统一，序列化格式一致（P2-10）。</li>
 *   <li><b>补 {@code @TableField(fill = ...)}</b>：MyBatis-Plus 只会对带 fill
 *       标注的字段调用 {@code MetaObjectHandler}，此前全工程零个 fill 标注，
 *       导致 {@code AuditMetaObjectHandler} 是死代码、{@code create_user/update_user}
 *       恒为 NULL（P2-1）。</li>
 * </ol>
 *
 * <p>写这些表的入口只有管理端 Service，登录态必然是 staff，
 * 因此填充 {@code create_user/update_user} 不会违反指向 {@code staff(id)} 的外键。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Category implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    //主键
    private Long id;

    //分类名称
    private String name;

    //顺序
    private Integer sort;

    //分类状态 0:禁用，1:启用
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
