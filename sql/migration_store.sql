-- =============================================================================
--  迁移脚本：新增门店表，把车辆坐标迁移为「车辆归属门店」，并删除车辆自身的经纬度
--  执行: mysql -u root -p123456 --default-character-set=utf8mb4 < sql/migration_store.sql
--  说明: 这是一次性迁移脚本（ALTER DROP COLUMN 只能执行一次）
-- =============================================================================

USE `car_sharing_db`;

-- -----------------------------------------------------------------------------
-- 门店表：门店持有经纬度，车辆通过 store_id 归属到门店
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `store`
(
    `store_id`       BIGINT        NOT NULL AUTO_INCREMENT COMMENT '门店ID',
    `name`           VARCHAR(64)   NOT NULL COMMENT '门店名称',
    `address`        VARCHAR(255)           DEFAULT NULL COMMENT '详细地址',
    `longitude`      DECIMAL(10, 6) NOT NULL COMMENT '经度',
    `latitude`       DECIMAL(10, 6) NOT NULL COMMENT '纬度',
    `phone`          VARCHAR(20)            DEFAULT NULL COMMENT '联系电话',
    `business_hours` VARCHAR(64)            DEFAULT NULL COMMENT '营业时间',
    `status`         VARCHAR(20)   NOT NULL DEFAULT 'OPEN' COMMENT '状态: OPEN/CLOSED',
    `remark`         VARCHAR(255)           DEFAULT NULL COMMENT '备注',
    `create_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`store_id`),
    KEY `idx_store_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='门店表';

-- -----------------------------------------------------------------------------
-- 广州门店（与车辆分布区域对应）
-- -----------------------------------------------------------------------------
INSERT IGNORE INTO `store` (`store_id`, `name`, `address`, `longitude`, `latitude`, `phone`, `business_hours`, `status`)
VALUES
(1, '天河体育中心店', '广州市天河区天河路299号',       113.361200, 23.124900, '020-88880001', '08:00-22:00', 'OPEN'),
(2, '越秀北京路店',   '广州市越秀区北京路288号',       113.266800, 23.128800, '020-88880002', '08:00-22:00', 'OPEN'),
(3, '海珠琶洲店',     '广州市海珠区阅江中路380号',     113.317200, 23.089700, '020-88880003', '08:00-21:00', 'OPEN'),
(4, '白云机场店',     '广州市白云区机场路1号',         113.273000, 23.201000, '020-88880004', '00:00-24:00', 'OPEN'),
(5, '荔湾上下九店',   '广州市荔湾区上下九步行街',       113.244200, 23.125900, '020-88880005', '09:00-22:00', 'OPEN'),
(6, '番禺万博店',     '广州市番禺区汉溪大道万博中心',   113.384000, 22.938000, '020-88880006', '08:00-21:00', 'OPEN'),
(7, '黄埔科学城店',   '广州市黄埔区科学大道',           113.459000, 23.107000, '020-88880007', '08:00-21:00', 'OPEN'),
(8, '南沙万达店',     '广州市南沙区蕉门河万达广场',     113.525000, 22.802000, '020-88880008', '09:00-21:00', 'OPEN');

-- -----------------------------------------------------------------------------
-- 按「就近原则」把每辆车分配到门店（依据车辆原有的经纬度）
-- -----------------------------------------------------------------------------
UPDATE `car` c
SET c.`store_id` = (SELECT s.`store_id`
                    FROM `store` s
                    ORDER BY ST_Distance_Sphere(POINT(s.`longitude`, s.`latitude`),
                                                POINT(c.`longitude`, c.`latitude`))
                    LIMIT 1)
WHERE c.`longitude` IS NOT NULL
  AND c.`latitude` IS NOT NULL;

-- -----------------------------------------------------------------------------
-- 删除车辆自身的经纬度（坐标从此只属于门店）
-- -----------------------------------------------------------------------------
ALTER TABLE `car`
    DROP COLUMN `longitude`,
    DROP COLUMN `latitude`;

-- 核对结果
SELECT s.store_id, s.name, COUNT(c.car_id) AS cars, SUM(c.status = 'FREE') AS rentable
FROM store s LEFT JOIN car c ON c.store_id = s.store_id
GROUP BY s.store_id, s.name ORDER BY s.store_id;
