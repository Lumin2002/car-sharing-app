-- =============================================================================
--  车辆属性种子数据
--  执行: mysql -u root -p123456 --default-character-set=utf8mb4 < sql/seed_car_attributes.sql
--  说明: 按能源类型/车型批量生成，覆盖全部车辆；
--        可重复执行（car_attributes.car_id 有唯一键，INSERT IGNORE 会跳过已有记录）
--  背景: 没有属性记录时，用户端「车辆详情」看不到「车辆配置」区块
-- =============================================================================

USE `car_sharing_db`;

-- -----------------------------------------------------------------------------
-- 纯电：有电池与续航，多数带前备箱
-- -----------------------------------------------------------------------------
INSERT IGNORE INTO `car_attributes`
(`car_id`, `battery_capacity`, `fast_charge`, `max_range`,
 `reverse_camera`, `radar`, `bluetooth`, `air_condition`, `cruise_control`,
 `sunroof`, `leather_seat`, `front_trunk_volume`, `trunk_volume`,
 `create_time`, `update_time`)
SELECT car_id, 60, 1, 420,
       1, 1, 1, 1, 1,
       CASE WHEN type = 'PREMIUM' THEN 1 ELSE 0 END,
       CASE WHEN type = 'ECONOMY' THEN 0 ELSE 1 END,
       60, 400,
       NOW(), NOW()
FROM `car`
WHERE fuel_type = 'ELECTRIC';

-- -----------------------------------------------------------------------------
-- 混动：小电池 + 长综合续航，无快充
-- -----------------------------------------------------------------------------
INSERT IGNORE INTO `car_attributes`
(`car_id`, `battery_capacity`, `fast_charge`, `max_range`,
 `reverse_camera`, `radar`, `bluetooth`, `air_condition`, `cruise_control`,
 `sunroof`, `leather_seat`, `front_trunk_volume`, `trunk_volume`,
 `create_time`, `update_time`)
SELECT car_id, 18, 0, 900,
       1, 1, 1, 1, 1,
       CASE WHEN type = 'PREMIUM' THEN 1 ELSE 0 END,
       CASE WHEN type = 'ECONOMY' THEN 0 ELSE 1 END,
       NULL, 500,
       NOW(), NOW()
FROM `car`
WHERE fuel_type = 'HYBRID';

-- -----------------------------------------------------------------------------
-- 燃油：没有电池/续航指标，只录配置项
-- -----------------------------------------------------------------------------
INSERT IGNORE INTO `car_attributes`
(`car_id`, `battery_capacity`, `fast_charge`, `max_range`,
 `reverse_camera`, `radar`, `bluetooth`, `air_condition`, `cruise_control`,
 `sunroof`, `leather_seat`, `front_trunk_volume`, `trunk_volume`,
 `create_time`, `update_time`)
SELECT car_id, NULL, NULL, NULL,
       1, 1, 1, 1,
       CASE WHEN type IN ('COMFORT', 'PREMIUM') THEN 1 ELSE 0 END,
       CASE WHEN type = 'PREMIUM' THEN 1 ELSE 0 END,
       CASE WHEN type = 'PREMIUM' THEN 1 ELSE 0 END,
       NULL, 450,
       NOW(), NOW()
FROM `car`
WHERE fuel_type IN ('GASOLINE', 'DIESEL');

-- 核对：车辆总数 vs 已录属性数
SELECT
    (SELECT COUNT(*) FROM `car`) AS car_total,
    (SELECT COUNT(*) FROM `car_attributes`) AS attr_total;
