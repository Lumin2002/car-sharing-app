-- =============================================================================
--  迁移脚本：站内消息表 + 逻辑删除字段对齐
--  执行: mysql -u root -p123456 --default-character-set=utf8mb4 < sql/migration_message_logic_delete.sql
--  背景:
--    1) User / Store / Car / CarAttributes / RentalOrder / Message 六个实体
--       统一改用 @TableLogic + deleted 字段做逻辑删除；
--       而 car 表原来叫 is_deleted，其余表还没有这一列。
--    2) 新增站内消息表 message（对应实体 Message）。
--  说明: 本脚本可重复执行；所有变更前都会先判断当前结构
-- =============================================================================

USE `car_sharing_db`;

SET @logic_col := 'TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''逻辑删除：0 正常 / 1 已删除''';

-- -----------------------------------------------------------------------------
-- 1) car：is_deleted 改名为 deleted（保留原有数据）
-- -----------------------------------------------------------------------------
SET @old_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'car' AND COLUMN_NAME = 'is_deleted');
SET @new_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'car' AND COLUMN_NAME = 'deleted');
SET @ddl := IF(@old_exists > 0 AND @new_exists = 0,
    CONCAT('ALTER TABLE `car` CHANGE COLUMN `is_deleted` `deleted` ', @logic_col),
    IF(@new_exists = 0,
       CONCAT('ALTER TABLE `car` ADD COLUMN `deleted` ', @logic_col),
       'SELECT ''car.deleted 已存在，跳过'' AS msg'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- -----------------------------------------------------------------------------
-- 2) 其余五张业务表：补 deleted 列
-- -----------------------------------------------------------------------------
SET @ddl := (
    SELECT IF(COUNT(*) = 0,
              CONCAT('ALTER TABLE `car_attributes` ADD COLUMN `deleted` ', @logic_col),
              'SELECT ''car_attributes.deleted 已存在，跳过'' AS msg')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'car_attributes' AND COLUMN_NAME = 'deleted');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
              CONCAT('ALTER TABLE `rental_order` ADD COLUMN `deleted` ', @logic_col),
              'SELECT ''rental_order.deleted 已存在，跳过'' AS msg')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rental_order' AND COLUMN_NAME = 'deleted');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
              CONCAT('ALTER TABLE `store` ADD COLUMN `deleted` ', @logic_col),
              'SELECT ''store.deleted 已存在，跳过'' AS msg')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'store' AND COLUMN_NAME = 'deleted');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
              CONCAT('ALTER TABLE `user` ADD COLUMN `deleted` ', @logic_col),
              'SELECT ''user.deleted 已存在，跳过'' AS msg')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'deleted');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- -----------------------------------------------------------------------------
-- 3) 站内消息表（对应实体 Message）
--    type        : MessageType 的 code 值
--                  101/102 系统类、201~207 订单类、301~305 资金类、401 车辆类
--    read_status : 0 未读 / 1 已读（MessageStatus 的 code 值）
--    deleted     : 逻辑删除，0 正常 / 1 已删除
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `message`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     BIGINT       NOT NULL COMMENT '接收用户ID',
    `order_id`    BIGINT                DEFAULT NULL COMMENT '关联租赁订单ID',
    `title`       VARCHAR(100) NOT NULL COMMENT '消息标题',
    `content`     VARCHAR(500) NOT NULL COMMENT '消息内容',
    `type`        INT          NOT NULL COMMENT '消息类型: MessageType.code',
    `read_status` TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '读取状态: 0 未读 / 1 已读',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME              DEFAULT NULL COMMENT '更新时间',
    `deleted`     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删除',
    PRIMARY KEY (`id`),
    KEY `idx_message_user` (`user_id`, `read_status`),
    KEY `idx_message_order` (`order_id`),
    KEY `idx_message_type` (`type`),
    KEY `idx_message_create` (`create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='站内消息表';

-- -----------------------------------------------------------------------------
-- 4) 执行结果核对
-- -----------------------------------------------------------------------------
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, COLUMN_DEFAULT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND COLUMN_NAME IN ('deleted', 'is_deleted')
ORDER BY TABLE_NAME;

SHOW INDEX FROM `message`;
