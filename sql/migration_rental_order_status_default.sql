-- =============================================================================
-- rental_order.status 默认值修正
-- 默认状态应为 PENDING（待支付/待取车），而不是 RENTING（已取车）。
-- 只修改新插入行的默认值，不改写存量订单状态。
-- =============================================================================

ALTER TABLE `rental_order`
    MODIFY COLUMN `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        COMMENT '状态: PENDING/RENTING/OVERDUE/RETURNED/CANCELLED';
