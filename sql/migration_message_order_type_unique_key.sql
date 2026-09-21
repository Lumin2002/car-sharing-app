-- =============================================================================
-- 站内消息幂等唯一键：(order_id, type)
-- 已有环境可能存在重复消息，先保留最小 id 去重，再补唯一键。
-- =============================================================================

DELETE m1
FROM `message` m1
         JOIN `message` m2
              ON m1.`order_id` = m2.`order_id`
                  AND m1.`type` = m2.`type`
                  AND m1.`id` > m2.`id`;

ALTER TABLE `message`
    ADD UNIQUE KEY `uk_message_order_type` (`order_id`, `type`);
