package com.freshveggie.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.freshveggie.entity.CartItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 购物车 Mapper, 批量插入保留在 CartItemMapper.xml。
 *
 * <p>2026-10-01（P2-13）：给集合参数补上 {@code @Param}。
 * 单个 List 参数此前依赖 MyBatis 3.5.6+ 的
 * {@code wrapToMapIfCollection} 自动包装（把集合同时注册成
 * {@code collection} / {@code list} / 实际类名），而那又依赖父 POM
 * 继承自 spring-boot 的 {@code -parameters} 编译参数 ——
 * 一旦有人关掉该参数，运行期就会报参数找不到。显式命名去掉这条隐式耦合。
 */
@Mapper
public interface CartItemMapper extends BaseMapper<CartItem> {

    /** 批量新增购物车条目 */
    void saveItemBatch(@Param("cartItemList") List<CartItem> cartItemList);

    /**
     * 加购：存在则数量累加，不存在则插入。由 uk_cart_item 保证原子性。
     *
     * @return 受影响行数（插入=1；命中重复键并累加=2，MySQL 的 ON DUPLICATE KEY 语义）
     */
    int addOrIncrement(@Param("userId") Long userId,
                       @Param("productId") Long productId,
                       @Param("number") Integer number);
}
