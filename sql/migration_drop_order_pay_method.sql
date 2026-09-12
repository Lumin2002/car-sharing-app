-- =============================================================================
--  迁移脚本：删除 rental_order.pay_method 列
--  执行: mysql -u root -p123456 --default-character-set=utf8mb4 < sql/migration_drop_order_pay_method.sql
--  背景:
--    支付方式是「支付记录」的属性，已经落在 payment.pay_method 上，
--    订单表再存一份属于冗余：一个订单可能有租金支付 + 押金冻结两条记录，
--    甚至不同记录用不同方式支付，订单级的单一字段表达不了。
--    该列自加入以来从未写入有效值（全为 NULL），实体字段与写入逻辑已一并移除。
--  说明: 本脚本可重复执行
--  注意: DROP COLUMN 会丢数据，执行前请确认该列没有需要保留的内容
-- =============================================================================

USE `car_sharing_db`;

-- 执行前自查：确认这一列确实没有被使用
SELECT COUNT(*) AS pay_method_not_null_count
FROM `rental_order`
WHERE `pay_method` IS NOT NULL;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE()
               AND TABLE_NAME = 'rental_order'
               AND COLUMN_NAME = 'pay_method');

SET @ddl := IF(@has > 0,
    'ALTER TABLE `rental_order` DROP COLUMN `pay_method`',
    'SELECT ''rental_order.pay_method 已不存在，跳过'' AS msg');

PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 核对
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rental_order'
ORDER BY ORDINAL_POSITION;
