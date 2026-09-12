-- =============================================================================
--  迁移脚本：让数据库结构与实体类保持一致
--  执行: mysql -u root -p123456 --default-character-set=utf8mb4 < sql/migration_entity_sync.sql
--  内容:
--    1) user 表字段改名：last_login_ip -> login_ip、last_login_time -> login_time
--    2) car 表新增：supplier_id（供应商）、is_deleted（逻辑删除）
--    3) rental_order 表新增：pay_method（支付方式）
--    4) 新建三张表：payment（支付）、refund（退款/解冻）、rental_settlement（还车结算）
--  说明: 本脚本为一次性迁移（CHANGE/ADD COLUMN 重复执行会报错）
-- =============================================================================

USE `car_sharing_db`;

-- -----------------------------------------------------------------------------
-- 1) user：登录信息字段改名（与 User 实体的 loginIp / loginTime 对应）
-- -----------------------------------------------------------------------------
ALTER TABLE `user`
    CHANGE COLUMN `last_login_ip` `login_ip` VARCHAR(64) DEFAULT NULL COMMENT '最后登录IP',
    CHANGE COLUMN `last_login_time` `login_time` DATETIME DEFAULT NULL COMMENT '最后登录时间';

-- -----------------------------------------------------------------------------
-- 2) car：新增供应商归属与逻辑删除标记（与 Car 实体的 supplierId / is_deleted 对应）
-- -----------------------------------------------------------------------------
ALTER TABLE `car`
    ADD COLUMN `supplier_id` BIGINT DEFAULT NULL COMMENT '供应商ID' AFTER `store_id`,
    ADD COLUMN `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删除';

-- -----------------------------------------------------------------------------
-- 3) rental_order：新增支付方式（与 RentalOrder 实体的 payMethod 对应）
-- -----------------------------------------------------------------------------
ALTER TABLE `rental_order`
    ADD COLUMN `pay_method` VARCHAR(20) DEFAULT NULL COMMENT '支付方式: ALIPAY/WECHAT/BANK/CASH/BALANCE' AFTER `total_amount`;

-- -----------------------------------------------------------------------------
-- 4-1) 支付表（对应实体 Payment）
--      pay_type   : RENT_PAY / DEPOSIT_FROZEN
--      pay_method : ALIPAY / WECHAT / BANK / CASH / BALANCE
--      status     : INIT / SUCCESS / FAIL / REFUNDED
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `payment`
(
    `id`             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_id`       BIGINT        NOT NULL COMMENT '租赁订单ID',
    `order_no`       VARCHAR(32)            DEFAULT NULL COMMENT '订单编号',
    `payment_no`     VARCHAR(64)   NOT NULL COMMENT '支付单号',
    `third_trade_no` VARCHAR(64)            DEFAULT NULL COMMENT '第三方交易号',
    `pay_type`       VARCHAR(20)   NOT NULL COMMENT '支付类型: RENT_PAY/DEPOSIT_FROZEN',
    `pay_method`     VARCHAR(20)            DEFAULT NULL COMMENT '支付方式: ALIPAY/WECHAT/BANK/CASH/BALANCE',
    `amount`         DECIMAL(10, 2) NOT NULL DEFAULT 0.00 COMMENT '支付金额',
    `status`         VARCHAR(20)   NOT NULL DEFAULT 'INIT' COMMENT '状态: INIT/SUCCESS/FAIL/REFUNDED',
    `create_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `callback_time`  DATETIME               DEFAULT NULL COMMENT '回调时间',
    `raw_callback`   TEXT                   COMMENT '第三方回调原文',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_payment_no` (`payment_no`),
    KEY `idx_payment_order` (`order_id`),
    KEY `idx_payment_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='支付记录表';

-- -----------------------------------------------------------------------------
-- 4-2) 退款表（对应实体 Refund）
--      refund_type : RENT_REFUND / DEPOSIT_UNFREEZE / DEPOSIT_DEDUCT
--      status      : APPLY / SUCCESS / FAIL
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `refund`
(
    `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `payment_id`      BIGINT                 DEFAULT NULL COMMENT '原支付记录ID',
    `order_id`        BIGINT        NOT NULL COMMENT '租赁订单ID',
    `order_no`        VARCHAR(32)            DEFAULT NULL COMMENT '订单编号',
    `refund_no`       VARCHAR(64)   NOT NULL COMMENT '退款单号',
    `third_refund_no` VARCHAR(64)            DEFAULT NULL COMMENT '第三方退款单号',
    `refund_type`     VARCHAR(20)   NOT NULL COMMENT '退款类型: RENT_REFUND/DEPOSIT_UNFREEZE/DEPOSIT_DEDUCT',
    `amount`          DECIMAL(10, 2) NOT NULL DEFAULT 0.00 COMMENT '退款/解冻金额',
    `status`          VARCHAR(20)   NOT NULL DEFAULT 'APPLY' COMMENT '状态: APPLY/SUCCESS/FAIL',
    `reason`          VARCHAR(255)           DEFAULT NULL COMMENT '退款原因',
    `create_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `callback_time`   DATETIME               DEFAULT NULL COMMENT '回调时间',
    `raw_callback`    TEXT                   COMMENT '第三方回调原文',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_refund_no` (`refund_no`),
    KEY `idx_refund_payment` (`payment_id`),
    KEY `idx_refund_order` (`order_id`),
    KEY `idx_refund_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='退款/押金解冻表';

-- -----------------------------------------------------------------------------
-- 4-3) 还车结算表（对应实体 RentalSettlement）
--      status : PENDING / CONFIRMED / FINISHED
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `rental_settlement`
(
    `id`                    BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_id`              BIGINT        NOT NULL COMMENT '租赁订单ID',
    `order_no`              VARCHAR(32)            DEFAULT NULL COMMENT '订单编号',
    `pre_mileage`           INT                    DEFAULT NULL COMMENT '取车里程',
    `actual_mileage`        INT                    DEFAULT NULL COMMENT '还车里程',
    `exceed_mileage`        INT                    DEFAULT NULL COMMENT '超出里程(km)',
    `exceed_mileage_fee`    DECIMAL(10, 2)         DEFAULT 0.00 COMMENT '超里程费',
    `overtime_minute`       BIGINT                 DEFAULT NULL COMMENT '超时分钟数',
    `overtime_fee`          DECIMAL(10, 2)         DEFAULT 0.00 COMMENT '超时费',
    `other_fee`             DECIMAL(10, 2)         DEFAULT 0.00 COMMENT '其他费用',
    `rent_amount`           DECIMAL(10, 2)         DEFAULT 0.00 COMMENT '租金',
    `total_settle_amount`   DECIMAL(10, 2)         DEFAULT 0.00 COMMENT '结算总额',
    `original_deposit`      DECIMAL(10, 2)         DEFAULT 0.00 COMMENT '原始押金',
    `deposit_deduct_amount` DECIMAL(10, 2)         DEFAULT 0.00 COMMENT '押金扣罚',
    `deposit_refund_amount` DECIMAL(10, 2)         DEFAULT 0.00 COMMENT '押金退还',
    `status`                VARCHAR(20)   NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/CONFIRMED/FINISHED',
    `remark`                VARCHAR(255)           DEFAULT NULL COMMENT '备注',
    `create_time`           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `confirm_time`          DATETIME               DEFAULT NULL COMMENT '确认时间',
    PRIMARY KEY (`id`),
    KEY `idx_settlement_order` (`order_id`),
    KEY `idx_settlement_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='还车结算表';

-- 核对
SELECT TABLE_NAME, TABLE_COMMENT FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'car_sharing_db' ORDER BY TABLE_NAME;
