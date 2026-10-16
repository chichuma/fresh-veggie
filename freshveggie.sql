-- 鲜蔬到家 FreshVeggie 数据库初始化脚本
-- 设计文档: docs/SQL-design/SQL.md
-- 适用 MySQL 8.0+,utf8mb4 字符集
--
-- =====================================================================
-- 最后同步: 2026-10-01
-- 同步基准: 真实库 fresh_veggie_db 的 SHOW CREATE TABLE 全量导出
--           （快照存于 tmp/_live_schema_dump.txt，共 10 张表）
--
-- ⚠️ 本脚本此前停留在 2026-09-15 的结构，落后于 2026-09-28 的数据库重构。
--    照旧版本建库会让订单/地址/购物车三条主链路直接报错。本次已修正：
--      · orders          : delivery_slot_id + slot_name → start_time + end_time
--      · orders          : 删 2 个热点 FK，索引重建 + 加 2 个 CHECK
--      · cart_item       : 删 6 个商品快照列（name/image/spec/unit/unit_price/amount）
--      · delivery_address: 新增 longitude / latitude（百度 bd09ll 坐标）
--      · order_item      : 删热点 FK fk_order_item_product 及其索引
--      · product         : 删 idx_product_sales，加 2 个 CHECK
--      · delivery_slot   : 加 CHECK chk_slot_time
--      · 新增 bench_stock_snapshot（压测专用，应用零访问）
--    迁移过程与实测数据见 bench/sql/refactor/EXECUTION-LOG.md。
--
-- 说明: 本脚本只建结构，不含业务数据。压测数据由 bench/sql/ 下的脚本生成。
-- =====================================================================

DROP DATABASE IF EXISTS `fresh_veggie_db`;
CREATE DATABASE `fresh_veggie_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `fresh_veggie_db`;

DROP TABLE IF EXISTS `bench_stock_snapshot`;
DROP TABLE IF EXISTS `order_item`;
DROP TABLE IF EXISTS `orders`;
DROP TABLE IF EXISTS `cart_item`;
DROP TABLE IF EXISTS `delivery_slot`;
DROP TABLE IF EXISTS `delivery_address`;
DROP TABLE IF EXISTS `product`;
DROP TABLE IF EXISTS `category`;
DROP TABLE IF EXISTS `user`;
DROP TABLE IF EXISTS `staff`;

-- =============================
-- 1. 后台员工(管理员/运营/分拣员,不含骑手)
-- =============================
CREATE TABLE `staff` (
  `id`          bigint      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username`    varchar(32) NOT NULL COMMENT '登录名',
  `password`    varchar(64) NOT NULL COMMENT '密码(BCrypt)',
  `name`        varchar(32) NOT NULL COMMENT '姓名',
  `phone`       varchar(11) NOT NULL COMMENT '手机号',
  `sex`         tinyint     NOT NULL DEFAULT 0 COMMENT '性别 0男 1女',
  `id_number`   varchar(18) NOT NULL COMMENT '身份证号',
  `role`        tinyint     NOT NULL DEFAULT 1 COMMENT '角色 1管理员 2运营 3分拣员',
  `status`      tinyint     NOT NULL DEFAULT 1 COMMENT '状态 0禁用 1启用',
  `create_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_user` bigint      DEFAULT NULL COMMENT '创建人',
  `update_user` bigint      DEFAULT NULL COMMENT '修改人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_staff_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='后台员工';

-- =============================
-- 2. C端用户
-- =============================
CREATE TABLE `user` (
  `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `openid`      varchar(64)  NOT NULL COMMENT '微信openid',
  `nickname`    varchar(32)  DEFAULT NULL COMMENT '昵称',
  `phone`       varchar(11)  DEFAULT NULL COMMENT '手机号',
  `avatar`      varchar(255) DEFAULT NULL COMMENT '头像',
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_openid` (`openid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='C端用户';

-- =============================
-- 3. 商品分类
-- =============================
CREATE TABLE `category` (
  `id`          bigint      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name`        varchar(32) NOT NULL COMMENT '分类名',
  `sort`        int         NOT NULL DEFAULT 0 COMMENT '排序',
  `status`      tinyint     NOT NULL DEFAULT 1 COMMENT '状态 0禁用 1启用',
  `create_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_user` bigint      DEFAULT NULL,
  `update_user` bigint      DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_category_name` (`name`),
  KEY `fk_category_cuser` (`create_user`),
  KEY `fk_category_uuser` (`update_user`),
  CONSTRAINT `fk_category_cuser` FOREIGN KEY (`create_user`) REFERENCES `staff`(`id`),
  CONSTRAINT `fk_category_uuser` FOREIGN KEY (`update_user`) REFERENCES `staff`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品分类';

-- =============================
-- 4. 生鲜商品
-- =============================
-- 注: idx_product_sales 已于 2026-09-28 移除（sales_count 零维护、该索引无用）。
--     chk_product_price / chk_product_stock 为同期加入的负向约束。
CREATE TABLE `product` (
  `id`              bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `category_id`     bigint        NOT NULL COMMENT '分类ID',
  `name`            varchar(64)   NOT NULL COMMENT '商品名',
  `image`           varchar(255)  DEFAULT NULL COMMENT '图片',
  `description`     varchar(500)  DEFAULT NULL COMMENT '描述',
  `price`           decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '售价',
  `original_price`  decimal(10,2) DEFAULT NULL COMMENT '划线原价(促销展示)',
  `unit`            varchar(16)   DEFAULT NULL COMMENT '单位 斤/个/盒/袋',
  `spec`            varchar(64)   DEFAULT NULL COMMENT '净含量/规格 如:500g/盒',
  `origin`          varchar(64)   DEFAULT NULL COMMENT '产地',
  `shelf_life_days` int           DEFAULT NULL COMMENT '保质期(天)',
  `storage_type`    tinyint       NOT NULL DEFAULT 2 COMMENT '存储条件 1冷藏 2常温 3冷冻',
  `is_presale`      tinyint       NOT NULL DEFAULT 0 COMMENT '是否预售 0否 1是',
  `stock`           int           NOT NULL DEFAULT 0 COMMENT '可售库存',
  `sales_count`     int           NOT NULL DEFAULT 0 COMMENT '销量(冗余,来自order_item聚合)',
  `status`          tinyint       NOT NULL DEFAULT 1 COMMENT '状态 0下架 1上架',
  `create_time`     datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`     datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_user`     bigint        DEFAULT NULL,
  `update_user`     bigint        DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_name` (`name`),
  KEY `idx_product_category` (`category_id`),
  KEY `idx_product_status` (`status`),
  KEY `fk_product_cuser` (`create_user`),
  KEY `fk_product_uuser` (`update_user`),
  CONSTRAINT `fk_product_category` FOREIGN KEY (`category_id`) REFERENCES `category`(`id`),
  CONSTRAINT `fk_product_cuser` FOREIGN KEY (`create_user`) REFERENCES `staff`(`id`),
  CONSTRAINT `fk_product_uuser` FOREIGN KEY (`update_user`) REFERENCES `staff`(`id`),
  CONSTRAINT `chk_product_price` CHECK (`price` >= 0),
  CONSTRAINT `chk_product_stock` CHECK (`stock` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='生鲜商品';

-- =============================
-- 5. 收货地址
-- =============================
-- 注: longitude / latitude 于 2026-09-28 新增（百度地理编码 bd09ll 坐标系）。
--     下单时优先用库中坐标做本地 Haversine 计算，缺失才调百度并回写（lazy backfill），
--     把地理编码从下单热路径上摘掉。见 OrderServiceImpl#verifiedDistance。
CREATE TABLE `delivery_address` (
  `id`            bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`       bigint        NOT NULL COMMENT '用户ID',
  `consignee`     varchar(50)   NOT NULL COMMENT '收货人',
  `phone`         varchar(11)   NOT NULL COMMENT '手机号',
  `province_code` varchar(12)   DEFAULT NULL COMMENT '省级区划编号',
  `province_name` varchar(32)   DEFAULT NULL COMMENT '省级名称',
  `city_code`     varchar(12)   DEFAULT NULL COMMENT '市级区划编号',
  `city_name`     varchar(32)   DEFAULT NULL COMMENT '市级名称',
  `district_code` varchar(12)   DEFAULT NULL COMMENT '区级区划编号',
  `district_name` varchar(32)   DEFAULT NULL COMMENT '区级名称',
  `detail`        varchar(200)  NOT NULL COMMENT '详细地址',
  `label`         varchar(32)   DEFAULT NULL COMMENT '标签 家/公司/学校',
  `is_default`    tinyint       NOT NULL DEFAULT 0 COMMENT '默认地址 0否 1是',
  `create_time`   datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`   datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `longitude`     decimal(10,6) DEFAULT NULL COMMENT '经度(bd09ll,百度地理编码坐标系)',
  `latitude`      decimal(10,6) DEFAULT NULL COMMENT '纬度(bd09ll,百度地理编码坐标系)',
  PRIMARY KEY (`id`),
  KEY `idx_delivery_address_user` (`user_id`),
  CONSTRAINT `fk_delivery_address_user` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收货地址';

-- =============================
-- 6. 配送时段
-- =============================
-- 注: chk_slot_time 于 2026-09-28 加入，保证 end_time > start_time。
CREATE TABLE `delivery_slot` (
  `id`          bigint      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `slot_name`   varchar(32) NOT NULL COMMENT '时段名 如:上午 9:00-11:00',
  `start_time`  time        NOT NULL COMMENT '开始时间',
  `end_time`    time        NOT NULL COMMENT '结束时间',
  `sort`        int         NOT NULL DEFAULT 0 COMMENT '展示顺序',
  `status`      tinyint     NOT NULL DEFAULT 1 COMMENT '状态 0停用 1启用',
  `create_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_user` bigint      DEFAULT NULL,
  `update_user` bigint      DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_delivery_slot_name` (`slot_name`),
  KEY `fk_slot_cuser` (`create_user`),
  KEY `fk_slot_uuser` (`update_user`),
  CONSTRAINT `fk_slot_cuser` FOREIGN KEY (`create_user`) REFERENCES `staff`(`id`),
  CONSTRAINT `fk_slot_uuser` FOREIGN KEY (`update_user`) REFERENCES `staff`(`id`),
  CONSTRAINT `chk_slot_time` CHECK (`end_time` > `start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='配送时段';

-- =============================
-- 7. 购物车项
-- =============================
-- 注: 2026-09-28 瘦身 —— 删掉 6 个商品快照列
--     (name / image / spec / unit / unit_price / amount)。
--     原因是快照价会让「商品改价后订单金额仍按过期价计算」。
--     现在单价一律实时读 product.price，展示时由 CartItemVO 回填商品信息；
--     快照职责归还给 order_item（下单那一刻定格）。
--     同时删掉冗余索引 idx_cart_item_user（uk_cart_item 的最左前缀已覆盖）。
CREATE TABLE `cart_item` (
  `id`          bigint   NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`     bigint   NOT NULL COMMENT '用户ID',
  `product_id`  bigint   NOT NULL COMMENT '商品ID',
  `number`      int      NOT NULL DEFAULT 1 COMMENT '数量',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cart_item` (`user_id`, `product_id`),
  KEY `fk_cart_product` (`product_id`),
  CONSTRAINT `fk_cart_user` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_cart_product` FOREIGN KEY (`product_id`) REFERENCES `product`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='购物车项';

-- =============================
-- 8. 订单
-- =============================
-- 注: 2026-09-28 的改动
--     · delivery_slot_id + slot_name → start_time + end_time
--       订单是快照，存时段起止时间后不再依赖 delivery_slot 行是否被改名/删除。
--     · 删热点 FK: fk_orders_address、fk_orders_slot
--       （插入子行会对父行加 S 锁并持有到提交，是 S→X 死锁的来源之一）
--     · 索引重建为 idx_orders_user_create / idx_orders_status_create / idx_orders_date_slot
--     · 加 CHECK: status∈[1,5]、pay_status∈[0,2]
CREATE TABLE `orders` (
  `id`                  bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_no`            varchar(32)   NOT NULL COMMENT '订单号',
  `user_id`             bigint        NOT NULL COMMENT '下单用户',
  `status`              tinyint       NOT NULL DEFAULT 1 COMMENT '状态 1待支付 2备货中 3配送中 4已完成 5已取消',
  `pay_method`          tinyint       NOT NULL DEFAULT 1 COMMENT '支付方式 1微信',
  `pay_status`          tinyint       NOT NULL DEFAULT 0 COMMENT '支付状态 0未支付 1已支付 2退款',
  `delivery_address_id` bigint        DEFAULT NULL COMMENT '收货地址ID(删址后置空,保留快照)',
  `consignee`           varchar(50)   DEFAULT NULL COMMENT '收货人快照',
  `phone`               varchar(11)   DEFAULT NULL COMMENT '手机号快照',
  `address`             varchar(255)  DEFAULT NULL COMMENT '地址快照(省市区+详址)',
  `delivery_date`       date          DEFAULT NULL COMMENT '配送日期(次日达)',
  `goods_amount`        decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '商品总额',
  `delivery_fee`        decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '配送费',
  `total_amount`        decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '应付总额',
  `is_presale`          tinyint       NOT NULL DEFAULT 0 COMMENT '是否预售单 0否 1是',
  `remark`              varchar(255)  DEFAULT NULL COMMENT '备注',
  `cancel_reason`       varchar(255)  DEFAULT NULL COMMENT '取消/拒单原因',
  `cancel_time`         datetime      DEFAULT NULL COMMENT '取消时间',
  `paid_time`           datetime      DEFAULT NULL COMMENT '支付时间',
  `delivered_time`      datetime      DEFAULT NULL COMMENT '送达时间',
  `completed_time`      datetime      DEFAULT NULL COMMENT '完成时间',
  `create_time`         datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
  `update_time`         datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `start_time`          time          DEFAULT NULL COMMENT '配送时段开始(HH:mm:ss，下单时的快照)',
  `end_time`            time          DEFAULT NULL COMMENT '配送时段结束(HH:mm:ss，下单时的快照)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_orders_order_no` (`order_no`),
  KEY `idx_orders_user_create` (`user_id`, `create_time`),
  KEY `idx_orders_status_create` (`status`, `create_time`),
  KEY `idx_orders_date_slot` (`delivery_date`, `start_time`),
  CONSTRAINT `fk_orders_user` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`),
  CONSTRAINT `chk_orders_status` CHECK (`status` BETWEEN 1 AND 5),
  CONSTRAINT `chk_orders_paystatus` CHECK (`pay_status` BETWEEN 0 AND 2)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单';

-- =============================
-- 9. 订单明细
-- =============================
-- 注: 2026-09-28 删除 fk_order_item_product 及 idx_order_item_product。
--     该外键是「扣减库存后移」方案产生 4190 次死锁的根因：
--     插入子行时 InnoDB 会对被引用的父行（product 热行）加 S 锁并持有到提交，
--     与「先插明细拿 S 锁 → 再扣减要 X 锁」一起构成 S→X 锁升级成环。
--     删掉后插入明细不再在 product 上留锁，扣减后移才得以安全实施。
--     这里的 name/unit_price/amount 等快照列【保留】—— 快照职责归 order_item。
CREATE TABLE `order_item` (
  `id`         bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_id`   bigint        NOT NULL COMMENT '订单ID',
  `product_id` bigint        NOT NULL COMMENT '商品ID',
  `name`       varchar(64)   NOT NULL COMMENT '商品名快照',
  `image`      varchar(255)  DEFAULT NULL COMMENT '图片快照',
  `spec`       varchar(64)   DEFAULT NULL COMMENT '规格快照',
  `unit`       varchar(16)   DEFAULT NULL COMMENT '单位快照',
  `origin`     varchar(64)   DEFAULT NULL COMMENT '产地快照',
  `unit_price` decimal(10,2) NOT NULL COMMENT '单价快照',
  `number`     int           NOT NULL DEFAULT 1 COMMENT '数量',
  `amount`     decimal(10,2) NOT NULL COMMENT '小计',
  PRIMARY KEY (`id`),
  KEY `idx_order_item_order` (`order_id`),
  CONSTRAINT `fk_order_item_order` FOREIGN KEY (`order_id`) REFERENCES `orders`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单明细';

-- =============================
-- 10. 压测用商品库存快照
-- =============================
-- ⚠️ 这张表【仅供 bench/sql/ 下的压测脚本使用，应用 Java 代码零访问】。
--    product.stock 是压测中唯一会被破坏的业务数据，脚本先在此存基线，
--    每轮压测前用它把 stock/sales_count 复位，保证跨轮次可比。
--    之所以写进建库脚本：多个 bench 脚本会读写它，缺表则复位脚本全部失败。
CREATE TABLE `bench_stock_snapshot` (
  `product_id`  bigint   NOT NULL,
  `orig_stock`  int      NOT NULL,
  `orig_sales`  int      NOT NULL,
  `snapshot_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='压测前商品库存/销量快照，用于复原';
