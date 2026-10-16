package com.freshveggie.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.freshveggie.context.BaseContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 审计字段自动填充。
 *
 * <h3>2026-10-01 修复：这个类此前是死代码</h3>
 * MyBatis-Plus **只会对带 {@code @TableField(fill = ...)} 的字段**调用
 * {@code MetaObjectHandler}。此前全工程零个 fill 标注（grep 实测），
 * 所以本类从未被触发过 —— {@code create_user / update_user} 恒为 NULL，
 * 审计链路形同虚设。
 *
 * <p>现在 {@code Category / Product / Staff / DeliverySlot} 四个实体的
 * {@code createTime / updateTime / createUser / updateUser} 都已标注 fill，
 * 填充链路才真正接通。
 *
 * <h3>为什么用"setter 存在性"判断而不是硬编码字段名</h3>
 * 不同实体的审计字段不同：{@code user} 表只有 {@code create_time}；
 * {@code orders} 有 {@code create_time/update_time} 但没有审计人。
 * 按 setter 存在性判断可以一套代码兼容全部实体，不必为每张表写填充器。
 *
 * <h3>外键安全</h3>
 * {@code create_user / update_user} 上有指向 {@code staff(id)} 的外键
 * （{@code fk_category_cuser} 等）。经核对，写这四张表的入口只有管理端
 * Service（{@code CategoryServiceImpl} / {@code ProductServiceImpl} /
 * {@code StaffServiceImpl} / {@code DeliverySlotServiceImpl}），
 * 登录态必然是 staff，因此不会写入无效的 staff id。
 * C 端路径（下单 / 加购等）不触碰这四张表。
 */
@Slf4j
@Component
public class AuditMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        fillIfPresent(metaObject, "createTime", now);
        fillIfPresent(metaObject, "updateTime", now);
        // 未登录（如定时任务）时 getCurrentId() 为 null，此时跳过审计人填充，
        // 让数据库默认值（NULL）生效，而不是写入 0 之类的哨兵值。
        Long currentId = BaseContext.getCurrentId();
        if (currentId != null) {
            fillIfPresent(metaObject, "createUser", currentId);
            fillIfPresent(metaObject, "updateUser", currentId);
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        fillIfPresent(metaObject, "updateTime", LocalDateTime.now());
        Long currentId = BaseContext.getCurrentId();
        if (currentId != null) {
            fillIfPresent(metaObject, "updateUser", currentId);
        }
    }

    /**
     * 字段有 setter、类型匹配、且**当前值为 null** 时才填充。
     *
     * <p>加"当前值为 null"判断是为了不覆盖调用方显式设置的值
     * （例如业务代码手工指定 createTime 做数据订正）。
     */
    private void fillIfPresent(MetaObject metaObject, String field, Object value) {
        if (value == null || !metaObject.hasSetter(field)) {
            return;
        }
        if (metaObject.hasGetter(field) && metaObject.getValue(field) != null) {
            return;
        }
        // 类型不匹配时跳过（例如字段是 java.util.Date 而填充值是 LocalDateTime），
        // 交由数据库默认值处理，避免抛异常中断整个 insert
        Class<?> setterType = metaObject.getSetterType(field);
        if (setterType != null && setterType.isInstance(value)) {
            metaObject.setValue(field, value);
        }
    }
}
