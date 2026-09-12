-- =============================================================================
--  管理员账号种子数据
--  执行: mysql -u root -p123456 --default-character-set=utf8mb4 < sql/seed_admin_user.sql
--  账号: 手机号 13800000000 / 密码 admin123（登录后请立即修改）
--  说明: 可重复执行（INSERT IGNORE，撞 uk_user_phone 唯一键会跳过）
--        密码列存的是 BCrypt 密文，无法手写明文，这里直接写入固定密文
-- =============================================================================

USE `car_sharing_db`;

INSERT IGNORE INTO `user`
(`user_version`, `username`, `password`, `phone`, `role`, `status`, `create_time`)
VALUES
(1, 'admin', '$2a$10$03D/0QP1ZY9ZKq2fcoyIquMuy64bYjAaPnQ6HRjR3ugle.88dEkaC',
 '13800000000', 'ADMIN', 'ENABLED', NOW());

SELECT user_id, username, phone, role, status FROM `user` WHERE phone = '13800000000';
