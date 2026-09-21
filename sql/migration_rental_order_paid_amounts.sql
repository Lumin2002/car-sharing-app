-- =============================================================================
-- rental_order 增加实际已支付金额字段
-- 用于记录租金、押金实际支付成功金额，最终结算时计算差额补收。
-- =============================================================================

ALTER TABLE `rental_order`
    ADD COLUMN `paid_rent` DECIMAL(10, 2) NOT NULL DEFAULT 0.00
        COMMENT '已支付租金' AFTER `rent_amount`,
    ADD COLUMN `paid_deposit` DECIMAL(10, 2) NOT NULL DEFAULT 0.00
        COMMENT '已支付押金' AFTER `paid_rent`;
