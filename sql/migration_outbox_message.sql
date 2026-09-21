-- =============================================================================
-- 本地消息表（Outbox）
-- =============================================================================

CREATE TABLE IF NOT EXISTS `outbox_message`
(
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `msg_id`       VARCHAR(64)  NOT NULL COMMENT '消息ID，用于 CorrelationData',
    `exchange`     VARCHAR(100) NOT NULL COMMENT '目标交换机',
    `routing_key`  VARCHAR(100) NOT NULL COMMENT '路由键',
    `payload`      TEXT         NOT NULL COMMENT 'JSON 消息体',
    `status`       VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/SENT',
    `retry_count`  INT          NOT NULL DEFAULT 0 COMMENT '重试次数',
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `sent_at`      DATETIME              DEFAULT NULL COMMENT '发送时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_outbox_msg_id` (`msg_id`),
    KEY `idx_outbox_status` (`status`),
    KEY `idx_outbox_created` (`created_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='本地消息表';
