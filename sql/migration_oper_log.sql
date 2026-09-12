-- =============================================================================
--  迁移脚本：新增「操作日志」表 oper_log
--  执行: mysql -u root -p123456 --default-character-set=utf8mb4 < sql/migration_oper_log.sql
--  说明: 对应实体 cn.ff26710.carsharingapp.entity.OperLog
--        results 存枚举 LogResults 的 @EnumValue（0 成功 / 1 失败）
-- =============================================================================

USE `car_sharing_db`;

CREATE TABLE IF NOT EXISTS `oper_log`
(
    `id`        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `oper_time` DATETIME     NOT NULL COMMENT '操作时间',
    `results`   TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '结果: 0 成功 / 1 失败',
    `msg`       VARCHAR(512)          DEFAULT NULL COMMENT '结果描述或异常信息',
    `user_id`   BIGINT                DEFAULT NULL COMMENT '操作人ID',
    `oper_type` VARCHAR(32)  NOT NULL COMMENT '操作类型，如 CAR / USER / RENTAL',
    `oper_desc` VARCHAR(128) NOT NULL COMMENT '操作描述',
    `ip`        VARCHAR(64)           DEFAULT NULL COMMENT '操作IP',
    PRIMARY KEY (`id`),
    KEY `idx_oper_log_time` (`oper_time`),
    KEY `idx_oper_log_user` (`user_id`),
    KEY `idx_oper_log_type` (`oper_type`),
    KEY `idx_oper_log_results` (`results`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='操作日志表';

SELECT TABLE_NAME, TABLE_COMMENT, TABLE_ROWS FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'car_sharing_db' AND TABLE_NAME = 'oper_log';
