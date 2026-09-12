-- =============================================================================
--  迁移脚本：新增「实名认证」与「驾照认证」两张表
--  执行: mysql -u root -p123456 --default-character-set=utf8mb4 < sql/migration_verification.sql
--  说明: 一个用户各一条记录（user_id 唯一）；审核状态 PENDING/APPROVED/REJECTED
-- =============================================================================

USE `car_sharing_db`;

-- -----------------------------------------------------------------------------
-- 实名认证
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user_realname_auth`
(
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`       BIGINT       NOT NULL COMMENT '用户ID',
    `real_name`     VARCHAR(32)  NOT NULL COMMENT '真实姓名',
    `id_card_no`    VARCHAR(24)  NOT NULL COMMENT '身份证号',
    `id_card_front` VARCHAR(255)          DEFAULT NULL COMMENT '身份证正面照URL',
    `id_card_back`  VARCHAR(255)          DEFAULT NULL COMMENT '身份证反面照URL',
    `status`        VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/APPROVED/REJECTED',
    `reject_reason` VARCHAR(255)          DEFAULT NULL COMMENT '驳回原因',
    `submit_time`   DATETIME              DEFAULT NULL COMMENT '提交时间',
    `audit_time`    DATETIME              DEFAULT NULL COMMENT '审核时间',
    `auditor_id`    BIGINT                DEFAULT NULL COMMENT '审核人ID',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_realname_user` (`user_id`),
    KEY `idx_realname_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='用户实名认证表';

-- -----------------------------------------------------------------------------
-- 驾照认证
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user_driver_license`
(
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`       BIGINT       NOT NULL COMMENT '用户ID',
    `license_no`    VARCHAR(32)  NOT NULL COMMENT '驾驶证号',
    `license_class` VARCHAR(10)           DEFAULT NULL COMMENT '准驾车型，如 C1',
    `license_front` VARCHAR(255)          DEFAULT NULL COMMENT '驾照正页URL',
    `license_back`  VARCHAR(255)          DEFAULT NULL COMMENT '驾照副页URL',
    `issue_date`    DATE                  DEFAULT NULL COMMENT '初次领证日期',
    `expire_date`   DATE                  DEFAULT NULL COMMENT '有效期至',
    `status`        VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/APPROVED/REJECTED',
    `reject_reason` VARCHAR(255)          DEFAULT NULL COMMENT '驳回原因',
    `submit_time`   DATETIME              DEFAULT NULL COMMENT '提交时间',
    `audit_time`    DATETIME              DEFAULT NULL COMMENT '审核时间',
    `auditor_id`    BIGINT                DEFAULT NULL COMMENT '审核人ID',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_license_user` (`user_id`),
    KEY `idx_license_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='用户驾照认证表';

SELECT TABLE_NAME, TABLE_COMMENT FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'car_sharing_db' AND TABLE_NAME LIKE 'user_%auth%' OR TABLE_NAME = 'user_driver_license';
