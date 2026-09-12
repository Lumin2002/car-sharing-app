-- =============================================================================
--  迁移脚本：payment / refund / rental_settlement 三张表补充唯一键兜底
--  执行: mysql -u root -p123456 --default-character-set=utf8mb4 < sql/migration_unique_keys.sql
--  背景:
--    支付、退款、结算三张表原来只有普通索引，应用层虽然做了"是否已支付/是否已结算"
--    的判断，但并发下仍可能插入重复记录。这里用唯一键在数据库层兜底：
--      payment            : 一个订单、一种支付类型只允许一条（防重复支付/重复冻结押金）
--      refund             : 一个订单、一种退款类型只允许一条（防重复退款）
--      rental_settlement  : 一个订单只允许一张结算单（防并发还车生成两张）
--  说明: 本脚本可重复执行；执行前请确认三张表无重复数据（见文末核对语句）
-- =============================================================================

USE `car_sharing_db`;

-- -----------------------------------------------------------------------------
-- 1) payment：uk_payment_order_type (order_id, pay_type)
-- -----------------------------------------------------------------------------
SET @has := (SELECT COUNT(*) FROM information_schema.STATISTICS
             WHERE TABLE_SCHEMA = DATABASE()
               AND TABLE_NAME = 'payment'
               AND INDEX_NAME = 'uk_payment_order_type');
SET @ddl := IF(@has = 0,
    'ALTER TABLE `payment` ADD UNIQUE KEY `uk_payment_order_type` (`order_id`, `pay_type`)',
    'SELECT ''payment.uk_payment_order_type 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- -----------------------------------------------------------------------------
-- 2) refund：uk_refund_order_type (order_id, refund_type)
-- -----------------------------------------------------------------------------
SET @has := (SELECT COUNT(*) FROM information_schema.STATISTICS
             WHERE TABLE_SCHEMA = DATABASE()
               AND TABLE_NAME = 'refund'
               AND INDEX_NAME = 'uk_refund_order_type');
SET @ddl := IF(@has = 0,
    'ALTER TABLE `refund` ADD UNIQUE KEY `uk_refund_order_type` (`order_id`, `refund_type`)',
    'SELECT ''refund.uk_refund_order_type 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- -----------------------------------------------------------------------------
-- 3) rental_settlement：uk_settlement_order (order_id)，一个订单一张结算单
-- -----------------------------------------------------------------------------
SET @has := (SELECT COUNT(*) FROM information_schema.STATISTICS
             WHERE TABLE_SCHEMA = DATABASE()
               AND TABLE_NAME = 'rental_settlement'
               AND INDEX_NAME = 'uk_settlement_order');
SET @ddl := IF(@has = 0,
    'ALTER TABLE `rental_settlement` ADD UNIQUE KEY `uk_settlement_order` (`order_id`)',
    'SELECT ''rental_settlement.uk_settlement_order 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- -----------------------------------------------------------------------------
-- 4) 清理被唯一键完全覆盖的冗余单列索引
--    uk_refund_order_type(order_id, refund_type)     覆盖 idx_refund_order(order_id)
--    uk_settlement_order(order_id)                   覆盖 idx_settlement_order(order_id)
-- -----------------------------------------------------------------------------
SET @has := (SELECT COUNT(*) FROM information_schema.STATISTICS
             WHERE TABLE_SCHEMA = DATABASE()
               AND TABLE_NAME = 'refund'
               AND INDEX_NAME = 'idx_refund_order');
SET @ddl := IF(@has > 0,
    'ALTER TABLE `refund` DROP INDEX `idx_refund_order`',
    'SELECT ''refund.idx_refund_order 已不存在，跳过'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.STATISTICS
             WHERE TABLE_SCHEMA = DATABASE()
               AND TABLE_NAME = 'rental_settlement'
               AND INDEX_NAME = 'idx_settlement_order');
SET @ddl := IF(@has > 0,
    'ALTER TABLE `rental_settlement` DROP INDEX `idx_settlement_order`',
    'SELECT ''rental_settlement.idx_settlement_order 已不存在，跳过'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- -----------------------------------------------------------------------------
-- 5) 执行结果核对
-- -----------------------------------------------------------------------------
SHOW INDEX FROM `payment`;
SHOW INDEX FROM `refund`;
SHOW INDEX FROM `rental_settlement`;

-- -----------------------------------------------------------------------------
-- 附：加唯一键前如果报 Duplicate entry，用下面的语句先排查重复数据
--   SELECT order_id, pay_type, COUNT(*) c FROM payment GROUP BY order_id, pay_type HAVING c > 1;
--   SELECT order_id, refund_type, COUNT(*) c FROM refund GROUP BY order_id, refund_type HAVING c > 1;
--   SELECT order_id, COUNT(*) c FROM rental_settlement GROUP BY order_id HAVING c > 1;
-- -----------------------------------------------------------------------------
