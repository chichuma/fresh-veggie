-- 鲜蔬到家 FreshVeggie 最小种子数据
-- =====================================================================
-- 用途: freshveggie.sql 只建结构、不含数据。本脚本补齐【应用能启动并跑通
--       主链路】所必需的最小数据。建库后按需执行：
--         mysql -uroot < freshveggie.sql
--         mysql -uroot fresh_veggie_db < freshveggie-seed.sql
--
-- 包含:
--   · staff          3 个后台账号（管理员/运营/分拣员）—— 没有它无法登录后台
--   · category       8 个商品分类 —— 商品必须挂在分类下
--   · delivery_slot  5 个配送时段 —— 下单必须选时段
--
-- 不含:
--   · product —— 由 bench/sql/baseline/seed-50-users.sql 或管理端创建。
--                注意：product.create_user / update_user 是 fk_product_cuser /
--                fk_product_uuser 指向 staff(id)，所以必须有 staff 行才能插商品。
--   · user / delivery_address / cart_item / orders —— 压测数据，由 bench/sql/ 生成。
--   · bench_stock_snapshot —— 压测快照表，由压测脚本自行填充。
--
-- ⚠️ 三个账号的密码是同一个 BCrypt 哈希，对应明文仅用于本地开发。
--    生产环境必须替换，不要把这个哈希带上线。
--
-- ⚠️ 本文件是 UTF-8 编码，含中文数据。导入时【必须】指定字符集：
--      mysql --default-character-set=utf8mb4 -uroot fresh_veggie_db < freshveggie-seed.sql
--    否则 Windows 下 mysql 客户端会按 GBK 解读，中文变长导致
--    `Data too long for column 'name'`。下面这行是同样的保险。
-- =====================================================================

SET NAMES utf8mb4;
USE `fresh_veggie_db`;

-- ---------------------------------------------------------------------
-- 1. 后台员工
-- ---------------------------------------------------------------------
INSERT INTO `staff` (`id`, `username`, `password`, `name`, `phone`, `sex`, `id_number`, `role`, `status`) VALUES
(1, 'admin',    '$2a$10$jfQAd9g77twrXJQpsrg4DuOCPZZk9oRB04yWKmIOqCmZgya3z4hMi', '管理员',   '13800000001', 0, '110101199001011234', 1, 1),
(2, 'operator', '$2a$10$jfQAd9g77twrXJQpsrg4DuOCPZZk9oRB04yWKmIOqCmZgya3z4hMi', '运营小李', '13800000002', 1, '110101199202022345', 2, 1),
(3, 'picker',   '$2a$10$jfQAd9g77twrXJQpsrg4DuOCPZZk9oRB04yWKmIOqCmZgya3z4hMi', '分拣小王', '13800000003', 0, '110101199303033456', 3, 1);

-- ---------------------------------------------------------------------
-- 2. 商品分类
-- ---------------------------------------------------------------------
INSERT INTO `category` (`id`, `name`, `sort`, `status`) VALUES
(1, '时令蔬菜',   1, 1),
(2, '新鲜水果',   2, 1),
(3, '菌菇干货',   3, 1),
(4, '肉禽蛋品',   4, 1),
(5, '水产海鲜',   5, 1),
(6, '粮油调味',   6, 1),
(7, '净菜半成品', 7, 1),
(8, '乳品烘焙',   8, 1);

-- ---------------------------------------------------------------------
-- 3. 配送时段
--    chk_slot_time 要求 end_time > start_time，下面的数据都满足。
-- ---------------------------------------------------------------------
INSERT INTO `delivery_slot` (`id`, `slot_name`, `start_time`, `end_time`, `sort`, `status`) VALUES
(1, '上午 09:00-11:00', '09:00:00', '11:00:00', 1, 1),
(2, '中午 11:00-13:00', '11:00:00', '13:00:00', 2, 1),
(3, '下午 14:00-16:00', '14:00:00', '16:00:00', 3, 1),
(4, '傍晚 16:00-18:00', '16:00:00', '18:00:00', 4, 1),
(5, '晚间 18:00-20:00', '18:00:00', '20:00:00', 5, 1);

-- ---------------------------------------------------------------------
-- 校验
-- ---------------------------------------------------------------------
SELECT 'staff' AS tbl, COUNT(*) AS n FROM `staff`
UNION ALL SELECT 'category', COUNT(*) FROM `category`
UNION ALL SELECT 'delivery_slot', COUNT(*) FROM `delivery_slot`;
-- 期望: staff=3, category=8, delivery_slot=5
