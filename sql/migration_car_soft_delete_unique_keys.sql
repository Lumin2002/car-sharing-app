-- =============================================================================
-- 车辆与车辆属性软删除唯一键改造
-- 旧唯一键直接建在业务字段上，逻辑删除后旧行仍会占用唯一键，
-- 导致同一 VIN / 车牌无法重新登记。
-- 新方案使用“仅活跃数据才参与唯一约束”的生成列，已删除行该列值为 NULL，
-- 不会阻塞重新登记。
-- =============================================================================

ALTER TABLE `car`
    DROP INDEX `uk_car_vin`,
    DROP INDEX `uk_car_plate_no`,
    ADD COLUMN `active_vin` VARCHAR(32)
        GENERATED ALWAYS AS (CASE WHEN `deleted` = 0 THEN `vin` ELSE NULL END) STORED
        COMMENT '活跃VIN，用于唯一约束',
    ADD COLUMN `active_plate_no` VARCHAR(16)
        GENERATED ALWAYS AS (CASE WHEN `deleted` = 0 THEN `plate_no` ELSE NULL END) STORED
        COMMENT '活跃车牌，用于唯一约束',
    ADD UNIQUE KEY `uk_car_active_vin` (`active_vin`),
    ADD UNIQUE KEY `uk_car_active_plate_no` (`active_plate_no`);

ALTER TABLE `car_attributes`
    DROP INDEX `uk_car_attributes_car`,
    ADD COLUMN `active_car_id` BIGINT
        GENERATED ALWAYS AS (CASE WHEN `deleted` = 0 THEN `car_id` ELSE NULL END) STORED
        COMMENT '活跃车辆ID，用于唯一约束',
    ADD UNIQUE KEY `uk_car_attributes_active_car` (`active_car_id`);
