-- =============================================================================
--  迁移脚本：payment 表补 update_time / prepay_time，唯一键统一为 (order_id, pay_type)
--  执行: mysql -uroot -p123456 --default-character-set=utf8mb4 < sql/migration_payment_prepay_and_unique_key.sql
--  背景:
--    1) Payment 实体新增 update_time（更新时间）和 prepay_time（微信预支付下单时间），
--       缺列时支付相关的查询/写入会报 Unknown column 'update_time' / 'prepay_time'。
--    2) 唯一键统一为 uk_payment_order_type(order_id, pay_type)；
--       若库里存在中间版本的 uk_payment_order_no_type(order_no, pay_type) 会先删掉。
--  说明: 本脚本可重复执行，已存在的列/索引会自动跳过。
-- =============================================================================

USE `car_sharing_db`;

-- -----------------------------------------------------------------------------
-- 1) 补 update_time
-- -----------------------------------------------------------------------------
SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE()
               AND TABLE_NAME = 'payment'
               AND COLUMN_NAME = 'update_time');
SET @ddl := IF(@has = 0,
    'ALTER TABLE `payment` ADD COLUMN `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间'' AFTER `create_time`',
    'SELECT ''payment.update_time 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- -----------------------------------------------------------------------------
-- 2) 补 prepay_time
-- -----------------------------------------------------------------------------
SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE()
               AND TABLE_NAME = 'payment'
               AND COLUMN_NAME = 'prepay_time');
SET @ddl := IF(@has = 0,
    'ALTER TABLE `payment` ADD COLUMN `prepay_time` DATETIME DEFAULT NULL COMMENT ''微信预支付下单时间'' AFTER `update_time`',
    'SELECT ''payment.prepay_time 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- -----------------------------------------------------------------------------
-- 3) 删除中间版本的唯一键 uk_payment_order_no_type(order_no, pay_type)
-- -----------------------------------------------------------------------------
SET @has := (SELECT COUNT(*) FROM information_schema.STATISTICS
             WHERE TABLE_SCHEMA = DATABASE()
               AND TABLE_NAME = 'payment'
               AND INDEX_NAME = 'uk_payment_order_no_type');
SET @ddl := IF(@has > 0,
    'ALTER TABLE `payment` DROP INDEX `uk_payment_order_no_type`',
    'SELECT ''payment.uk_payment_order_no_type 已不存在，跳过'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- -----------------------------------------------------------------------------
-- 4) 新增唯一键 uk_payment_order_type(order_id, pay_type)
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
-- 5) 执行结果核对
-- -----------------------------------------------------------------------------
SHOW INDEX FROM `payment`;

SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'payment'
ORDER BY ORDINAL_POSITION;

-- -----------------------------------------------------------------------------
-- 附：加唯一键前如果报 Duplicate entry，用下面的语句先排查重复数据
--   SELECT order_id, pay_type, COUNT(*) c FROM payment GROUP BY order_id, pay_type HAVING c > 1;
-- -----------------------------------------------------------------------------
