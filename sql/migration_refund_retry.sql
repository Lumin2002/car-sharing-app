-- =============================================================================
-- refund 增加失败重试与对账字段
-- 说明：为「微信退款事务外调用 + 对账重试」提供本地状态记录。
-- =============================================================================

ALTER TABLE `refund`
    ADD COLUMN `fail_reason` VARCHAR(255) DEFAULT NULL COMMENT '最近一次失败原因' AFTER `raw_callback`,
    ADD COLUMN `retry_count` INT NOT NULL DEFAULT 0 COMMENT '已重试次数' AFTER `fail_reason`,
    ADD COLUMN `next_retry_time` DATETIME DEFAULT NULL COMMENT '下次重试时间' AFTER `retry_count`,
    ADD KEY `idx_refund_retry` (`status`, `next_retry_time`);
