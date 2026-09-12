-- =============================================================================
--  迁移脚本：user 表补充 update_time 列
--  执行: mysql -u root -p123456 --default-character-set=utf8mb4 < sql/migration_user_update_time.sql
--  背景:
--    User 实体改为继承 BaseLogicEntity 后，会连带使用基类的 createTime / updateTime / deleted。
--    user 表已有 create_time 和 deleted，但缺少 update_time，
--    不加这一列的话 MyBatis-Plus 拼出来的 SQL 会因为列不存在而报错。
--    顺带也补上了「用户记录最后修改时间」这个本来就缺的信息。
--  说明: 本脚本可重复执行
-- =============================================================================

USE `car_sharing_db`;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE()
               AND TABLE_NAME = 'user'
               AND COLUMN_NAME = 'update_time');

SET @ddl := IF(@has = 0,
    'ALTER TABLE `user` ADD COLUMN `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间''',
    'SELECT ''user.update_time 已存在，跳过'' AS msg');

PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 核对
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, EXTRA
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user'
ORDER BY ORDINAL_POSITION;
