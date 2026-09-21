-- =============================================================================
-- refresh_token 安全改造迁移
-- 1) 清空历史明文 refresh token，所有客户端需要重新登录。
-- 2) 明确 token 列保存的是 SHA-256 哈希，而不是明文。
-- 适用：已存在旧数据、准备切换到哈希存储的环境。
-- =============================================================================

DELETE FROM `refresh_token`;

ALTER TABLE `refresh_token`
    MODIFY COLUMN `token` VARCHAR(64) NOT NULL COMMENT '刷新令牌(SHA-256 哈希)';
