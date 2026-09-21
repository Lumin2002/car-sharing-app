-- =============================================================================
--  car-sharing-app 数据库初始化脚本
--  数据库: car_sharing_db (与 application.yml 的 jdbc url 保持一致)
--  字符集: utf8mb4
--  说明  : 字段采用下划线命名，与 mybatis-plus 的 map-underscore-to-camel-case 对应
--  执行  : mysql -u root -p < sql/schema.sql
-- =============================================================================

CREATE DATABASE IF NOT EXISTS `car_sharing_db`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE `car_sharing_db`;

-- -----------------------------------------------------------------------------
-- 用户表
-- 枚举落库为字符串（Java 枚举的 code 与 name 一致）：
--   role   : USER / SUPPLIER / OPERATIONS / ADMIN
--   status : ENABLED / DISABLED
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user`
(
    `user_id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `user_version`    INT          NOT NULL DEFAULT 1 COMMENT '令牌版本号，递增可使旧 access token 失效',
    `username`        VARCHAR(50)  NOT NULL COMMENT '用户名',
    `password`        VARCHAR(100) NOT NULL COMMENT '密码(BCrypt 加密后的密文)',
    `avatar`          VARCHAR(255)          DEFAULT NULL COMMENT '头像地址',
    `phone`           VARCHAR(20)  NOT NULL COMMENT '手机号(登录账号)',
    `role`            VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT '角色: USER/SUPPLIER/OPERATIONS/ADMIN',
    `status`          VARCHAR(20)  NOT NULL DEFAULT 'ENABLED' COMMENT '状态: ENABLED/DISABLED',
    `login_ip`        VARCHAR(64)           DEFAULT NULL COMMENT '最后登录IP',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `login_time`      DATETIME              DEFAULT NULL COMMENT '最后登录时间',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删除',
    PRIMARY KEY (`user_id`),
    UNIQUE KEY `uk_user_phone` (`phone`),
    KEY `idx_user_username` (`username`),
    KEY `idx_user_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='用户表';

-- -----------------------------------------------------------------------------
-- 刷新令牌表
-- 枚举落库为字符串：
--   revoked : VALID / REVOKED
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `refresh_token`
(
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     BIGINT      NOT NULL COMMENT '所属用户ID',
    `token`       VARCHAR(64) NOT NULL COMMENT '刷新令牌(SHA-256 哈希)',
    `expire_at`   DATETIME    NOT NULL COMMENT '过期时间',
    `revoked`     VARCHAR(20) NOT NULL DEFAULT 'VALID' COMMENT '状态: VALID/REVOKED',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_refresh_token` (`token`),
    KEY `idx_refresh_user_id` (`user_id`),
    KEY `idx_refresh_expire_at` (`expire_at`),
    CONSTRAINT `fk_refresh_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='刷新令牌表';

-- -----------------------------------------------------------------------------
-- 可选：初始化一个管理员账号，便于直接调用 /api/user/** 等需要 ADMIN 的接口
--   手机号: 13800000000   密码: admin123
--   (密码为 BCrypt 密文，已用项目同版本 spring-security-crypto 生成并校验)
-- -----------------------------------------------------------------------------
INSERT IGNORE INTO `user` (`user_version`, `username`, `password`, `phone`, `role`, `status`, `create_time`)
VALUES (1, 'admin', '$2a$10$03D/0QP1ZY9ZKq2fcoyIquMuy64bYjAaPnQ6HRjR3ugle.88dEkaC', '13800000000',
        'ADMIN', 'ENABLED', NOW());

-- -----------------------------------------------------------------------------
-- 门店表（持有经纬度；车辆通过 car.store_id 归属门店）
-- 枚举落库为字符串：
--   status : OPEN / CLOSED
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
    `deleted`        TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删除',
    PRIMARY KEY (`store_id`),
    KEY `idx_store_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='门店表';

-- -----------------------------------------------------------------------------
-- 车辆表
-- 枚举落库为字符串：
--   fuel_type : GASOLINE / DIESEL / ELECTRIC / HYBRID
--   type      : ECONOMY / COMFORT / PREMIUM
--   status    : FREE / RENTED / BOOKED / MAINTENANCE / DISABLED
-- 说明：车辆本身不存坐标，通过 store_id 归属到门店，位置取门店的经纬度
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `car`
(
    `car_id`            BIGINT         NOT NULL AUTO_INCREMENT COMMENT '车辆ID',
    `vin`               VARCHAR(32)    NOT NULL COMMENT 'VIN码',
    `plate_no`          VARCHAR(16)    NOT NULL COMMENT '车牌号',
    `brand`             VARCHAR(50)    NOT NULL COMMENT '品牌',
    `model`             VARCHAR(50)             DEFAULT NULL COMMENT '型号',
    `color`             VARCHAR(20)             DEFAULT NULL COMMENT '颜色',
    `cover_img`         VARCHAR(255)            DEFAULT NULL COMMENT '封面图',
    `seat_num`          INT                     DEFAULT NULL COMMENT '座位数',
    `door_num`          INT                     DEFAULT NULL COMMENT '车门数',
    `fuel_type`         VARCHAR(20)             DEFAULT NULL COMMENT '燃料类型',
    `automatic_gear`    TINYINT(1)              DEFAULT NULL COMMENT '是否自动挡',
    `type`              VARCHAR(20)             DEFAULT NULL COMMENT '车型',
    `store_id`          BIGINT                  DEFAULT NULL COMMENT '所属门店ID',
    `supplier_id`       BIGINT                  DEFAULT NULL COMMENT '供应商ID',
    `daily_price`       DECIMAL(10, 2) NOT NULL COMMENT '日租金',
    `deposit`           DECIMAL(10, 2)          DEFAULT 0.00 COMMENT '押金',
    `status`            VARCHAR(20)    NOT NULL DEFAULT 'FREE' COMMENT '状态',
    `current_tenant_id` BIGINT                  DEFAULT NULL COMMENT '当前承租人ID',
    `mileage`           INT                     DEFAULT 0 COMMENT '里程数(km)',
    `insurance_expire`  DATETIME                DEFAULT NULL COMMENT '保险到期时间',
    `deleted`           TINYINT(1)     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删除',
    `active_vin`        VARCHAR(32)    GENERATED ALWAYS AS (CASE WHEN `deleted` = 0 THEN `vin` ELSE NULL END) STORED COMMENT '活跃VIN，用于唯一约束',
    `active_plate_no`   VARCHAR(16)    GENERATED ALWAYS AS (CASE WHEN `deleted` = 0 THEN `plate_no` ELSE NULL END) STORED COMMENT '活跃车牌，用于唯一约束',
    `create_time`       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`car_id`),
    UNIQUE KEY `uk_car_active_vin` (`active_vin`),
    UNIQUE KEY `uk_car_active_plate_no` (`active_plate_no`),
    KEY `idx_car_status` (`status`),
    KEY `idx_car_store` (`store_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='车辆表';

-- -----------------------------------------------------------------------------
-- 车辆属性表（与 car 一对一）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `car_attributes`
(
    `attr_id`             BIGINT   NOT NULL AUTO_INCREMENT COMMENT '属性ID',
    `car_id`              BIGINT   NOT NULL COMMENT '车辆ID',
    `battery_capacity`    INT               DEFAULT NULL COMMENT '电池容量(kWh)',
    `fast_charge`         TINYINT(1)        DEFAULT NULL COMMENT '是否支持快充',
    `max_range`           INT               DEFAULT NULL COMMENT '最大续航(km)',
    `reverse_camera`      TINYINT(1)        DEFAULT NULL COMMENT '倒车影像',
    `radar`               TINYINT(1)        DEFAULT NULL COMMENT '倒车雷达',
    `bluetooth`           TINYINT(1)        DEFAULT NULL COMMENT '蓝牙',
    `air_condition`       TINYINT(1)        DEFAULT NULL COMMENT '空调',
    `cruise_control`      TINYINT(1)        DEFAULT NULL COMMENT '定速巡航',
    `sunroof`             TINYINT(1)        DEFAULT NULL COMMENT '天窗',
    `leather_seat`        TINYINT(1)        DEFAULT NULL COMMENT '真皮座椅',
    `front_trunk_volume`  INT               DEFAULT NULL COMMENT '前备箱容积(L)',
    `trunk_volume`        INT               DEFAULT NULL COMMENT '后备箱容积(L)',
    `create_time`         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`             TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删除',
    `active_car_id`       BIGINT GENERATED ALWAYS AS (CASE WHEN `deleted` = 0 THEN `car_id` ELSE NULL END) STORED COMMENT '活跃车辆ID，用于唯一约束',
    PRIMARY KEY (`attr_id`),
    UNIQUE KEY `uk_car_attributes_active_car` (`active_car_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='车辆属性表';

-- -----------------------------------------------------------------------------
-- 租赁订单表
-- 业务口径：先到先得 —— 只能租当前空闲(FREE)的车，不支持预约未来时段；
--          下单即占用车辆，订单进入 RENTING，车辆变为 RENTED。
-- 枚举落库为字符串：
--   status : RENTING / RETURNED / CANCELLED
-- 说明：daily_price / deposit 是下单时的价格快照，避免日后改价影响历史订单。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `rental_order`
(
    `order_id`           BIGINT         NOT NULL AUTO_INCREMENT COMMENT '订单ID',
    `order_no`           VARCHAR(32)    NOT NULL COMMENT '订单编号',
    `user_id`            BIGINT         NOT NULL COMMENT '承租人ID',
    `car_id`             BIGINT         NOT NULL COMMENT '车辆ID',
    `store_id`           BIGINT                  DEFAULT NULL COMMENT '取车网点ID',
    `return_store_id`    BIGINT                  DEFAULT NULL COMMENT '还车网点ID',
    `start_time`         DATETIME       NOT NULL COMMENT '租期开始时间',
    `end_time`           DATETIME       NOT NULL COMMENT '预计还车时间',
    `actual_return_time` DATETIME                DEFAULT NULL COMMENT '实际还车时间',
    `daily_price`        DECIMAL(10, 2) NOT NULL COMMENT '日租金(下单快照)',
    `deposit`            DECIMAL(10, 2)          DEFAULT 0.00 COMMENT '押金(下单快照)',
    `rent_days`          INT                     DEFAULT 1 COMMENT '租用天数',
    `rent_amount`        DECIMAL(10, 2)          DEFAULT 0.00 COMMENT '租金',
    `paid_rent`          DECIMAL(10, 2)          DEFAULT 0.00 COMMENT '已支付租金',
    `paid_deposit`       DECIMAL(10, 2)          DEFAULT 0.00 COMMENT '已支付押金',
    `total_amount`       DECIMAL(10, 2)          DEFAULT 0.00 COMMENT '合计金额',
    `status`             VARCHAR(20)    NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/RENTING/OVERDUE/RETURNED/CANCELLED',
    `mileage_before`     INT                     DEFAULT NULL COMMENT '取车里程',
    `mileage_after`      INT                     DEFAULT NULL COMMENT '还车里程',
    `remark`             VARCHAR(255)            DEFAULT NULL COMMENT '备注',
    `cancel_reason`      VARCHAR(255)            DEFAULT NULL COMMENT '取消原因',
    `create_time`        DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`        DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`            TINYINT(1)     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删除',
    PRIMARY KEY (`order_id`),
    UNIQUE KEY `uk_rental_order_no` (`order_no`),
    KEY `idx_rental_user` (`user_id`),
    KEY `idx_rental_car` (`car_id`),
    KEY `idx_rental_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='租赁订单表';

-- -----------------------------------------------------------------------------
-- 用户实名认证表（一个用户一条记录）
-- 枚举落库为字符串：status : PENDING / APPROVED / REJECTED
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user_realname_auth`
(
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`       BIGINT       NOT NULL COMMENT '用户ID',
    `real_name`     VARCHAR(32)  NOT NULL COMMENT '真实姓名',
    `id_card_no`    VARCHAR(24)  NOT NULL COMMENT '身份证号',
    `id_card_front` VARCHAR(255)          DEFAULT NULL COMMENT '身份证正面照URL',
    `id_card_back`  VARCHAR(255)          DEFAULT NULL COMMENT '身份证反面照URL',
    `status`        VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/APPROVED/REJECTED',
    `reject_reason` VARCHAR(255)          DEFAULT NULL COMMENT '驳回原因',
    `submit_time`   DATETIME              DEFAULT NULL COMMENT '提交时间',
    `audit_time`    DATETIME              DEFAULT NULL COMMENT '审核时间',
    `auditor_id`    BIGINT                DEFAULT NULL COMMENT '审核人ID',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_realname_user` (`user_id`),
    KEY `idx_realname_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='用户实名认证表';

-- -----------------------------------------------------------------------------
-- 用户驾照认证表（一个用户一条记录）
-- 枚举落库为字符串：status : PENDING / APPROVED / REJECTED
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user_driver_license`
(
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`       BIGINT       NOT NULL COMMENT '用户ID',
    `license_no`    VARCHAR(32)  NOT NULL COMMENT '驾驶证号',
    `license_class` VARCHAR(10)           DEFAULT NULL COMMENT '准驾车型，如 C1',
    `license_front` VARCHAR(255)          DEFAULT NULL COMMENT '驾照正页URL',
    `license_back`  VARCHAR(255)          DEFAULT NULL COMMENT '驾照副页URL',
    `issue_date`    DATE                  DEFAULT NULL COMMENT '初次领证日期',
    `expire_date`   DATE                  DEFAULT NULL COMMENT '有效期至',
    `status`        VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/APPROVED/REJECTED',
    `reject_reason` VARCHAR(255)          DEFAULT NULL COMMENT '驳回原因',
    `submit_time`   DATETIME              DEFAULT NULL COMMENT '提交时间',
    `audit_time`    DATETIME              DEFAULT NULL COMMENT '审核时间',
    `auditor_id`    BIGINT                DEFAULT NULL COMMENT '审核人ID',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_license_user` (`user_id`),
    KEY `idx_license_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='用户驾照认证表';

-- -----------------------------------------------------------------------------
-- 操作日志表
-- 说明：由 OperLogAspect 通过 @OperLogAnnotation 异步写入
--       results 存 LogResults 的 @EnumValue：0 成功 / 1 失败
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `oper_log`
(
    `id`        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `oper_time` DATETIME     NOT NULL COMMENT '操作时间',
    `results`   TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '结果: 0 成功 / 1 失败',
    `msg`       VARCHAR(512)          DEFAULT NULL COMMENT '结果描述或异常信息',
    `user_id`   BIGINT                DEFAULT NULL COMMENT '操作人ID',
    `oper_type` VARCHAR(32)  NOT NULL COMMENT '操作类型，如 CAR / USER / RENTAL',
    `oper_desc` VARCHAR(128) NOT NULL COMMENT '操作描述',
    `ip`        VARCHAR(64)           DEFAULT NULL COMMENT '操作IP',
    PRIMARY KEY (`id`),
    KEY `idx_oper_log_time` (`oper_time`),
    KEY `idx_oper_log_user` (`user_id`),
    KEY `idx_oper_log_type` (`oper_type`),
    KEY `idx_oper_log_results` (`results`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='操作日志表';

-- -----------------------------------------------------------------------------
-- 支付记录表
-- 枚举落库为字符串：
--   pay_type   : RENT_PAY / DEPOSIT_FROZEN
--   pay_method : ALIPAY / WECHAT / BANK / CASH / BALANCE
--   status     : INIT / SUCCESS / FAIL / EXPIRED / REFUNDED
-- 唯一键：uk_payment_order_type (order_id, pay_type)，一个订单的一种款项只允许一条支付单
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `payment`
(
    `id`             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_id`       BIGINT        NOT NULL COMMENT '租赁订单ID',
    `order_no`       VARCHAR(32)            DEFAULT NULL COMMENT '订单编号',
    `payment_no`     VARCHAR(64)   NOT NULL COMMENT '支付单号',
    `third_trade_no` VARCHAR(64)            DEFAULT NULL COMMENT '第三方交易号',
    `pay_type`       VARCHAR(20)   NOT NULL COMMENT '支付类型: RENT_PAY/DEPOSIT_FROZEN',
    `pay_method`     VARCHAR(20)            DEFAULT NULL COMMENT '支付方式: ALIPAY/WECHAT/BANK/CASH/BALANCE',
    `amount`         DECIMAL(10, 2) NOT NULL DEFAULT 0.00 COMMENT '支付金额',
    `status`         VARCHAR(20)   NOT NULL DEFAULT 'INIT' COMMENT '状态: INIT/SUCCESS/FAIL/EXPIRED/REFUNDED',
    `create_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `prepay_time`    DATETIME               DEFAULT NULL COMMENT '微信预支付下单时间',
    `callback_time`  DATETIME               DEFAULT NULL COMMENT '回调时间',
    `raw_callback`   TEXT                   COMMENT '第三方回调原文',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_payment_no` (`payment_no`),
    UNIQUE KEY `uk_payment_order_type` (`order_id`, `pay_type`),
    KEY `idx_payment_order` (`order_id`),
    KEY `idx_payment_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='支付记录表';

-- -----------------------------------------------------------------------------
-- 退款/押金解冻表
-- 枚举落库为字符串：
--   refund_type : RENT_REFUND / DEPOSIT_UNFREEZE / DEPOSIT_DEDUCT
--   status      : APPLY / SUCCESS / FAIL
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `refund`
(
    `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `payment_id`      BIGINT                 DEFAULT NULL COMMENT '原支付记录ID',
    `order_id`        BIGINT        NOT NULL COMMENT '租赁订单ID',
    `order_no`        VARCHAR(32)            DEFAULT NULL COMMENT '订单编号',
    `refund_no`       VARCHAR(64)   NOT NULL COMMENT '退款单号',
    `third_refund_no` VARCHAR(64)            DEFAULT NULL COMMENT '第三方退款单号',
    `refund_type`     VARCHAR(20)   NOT NULL COMMENT '退款类型: RENT_REFUND/DEPOSIT_UNFREEZE/DEPOSIT_DEDUCT',
    `amount`          DECIMAL(10, 2) NOT NULL DEFAULT 0.00 COMMENT '退款/解冻金额',
    `status`          VARCHAR(20)   NOT NULL DEFAULT 'APPLY' COMMENT '状态: APPLY/SUCCESS/FAIL',
    `reason`          VARCHAR(255)           DEFAULT NULL COMMENT '退款原因',
    `create_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `callback_time`   DATETIME               DEFAULT NULL COMMENT '回调时间',
    `raw_callback`    TEXT                   COMMENT '第三方回调原文',
    `fail_reason`     VARCHAR(255)           DEFAULT NULL COMMENT '最近一次失败原因',
    `retry_count`     INT           NOT NULL DEFAULT 0 COMMENT '已重试次数',
    `next_retry_time` DATETIME               DEFAULT NULL COMMENT '下次重试时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_refund_no` (`refund_no`),
    UNIQUE KEY `uk_refund_order_type` (`order_id`, `refund_type`),
    KEY `idx_refund_payment` (`payment_id`),
    KEY `idx_refund_status` (`status`),
    KEY `idx_refund_retry` (`status`, `next_retry_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='退款/押金解冻表';

-- -----------------------------------------------------------------------------
-- 还车结算表
-- 枚举落库为字符串：status : PENDING / CONFIRMED / FINISHED
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `rental_settlement`
(
    `id`                    BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_id`              BIGINT        NOT NULL COMMENT '租赁订单ID',
    `order_no`              VARCHAR(32)            DEFAULT NULL COMMENT '订单编号',
    `pre_mileage`           INT                    DEFAULT NULL COMMENT '取车里程',
    `actual_mileage`        INT                    DEFAULT NULL COMMENT '还车里程',
    `exceed_mileage`        INT                    DEFAULT NULL COMMENT '超出里程(km)',
    `exceed_mileage_fee`    DECIMAL(10, 2)         DEFAULT 0.00 COMMENT '超里程费',
    `overtime_minute`       BIGINT                 DEFAULT NULL COMMENT '超时分钟数',
    `overtime_fee`          DECIMAL(10, 2)         DEFAULT 0.00 COMMENT '超时费',
    `other_fee`             DECIMAL(10, 2)         DEFAULT 0.00 COMMENT '其他费用',
    `rent_amount`           DECIMAL(10, 2)         DEFAULT 0.00 COMMENT '租金',
    `total_settle_amount`   DECIMAL(10, 2)         DEFAULT 0.00 COMMENT '结算总额',
    `original_deposit`      DECIMAL(10, 2)         DEFAULT 0.00 COMMENT '原始押金',
    `deposit_deduct_amount` DECIMAL(10, 2)         DEFAULT 0.00 COMMENT '押金扣罚',
    `deposit_refund_amount` DECIMAL(10, 2)         DEFAULT 0.00 COMMENT '押金退还',
    `status`                VARCHAR(20)   NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/CONFIRMED/FINISHED',
    `remark`                VARCHAR(255)           DEFAULT NULL COMMENT '备注',
    `create_time`           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `confirm_time`          DATETIME               DEFAULT NULL COMMENT '确认时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_settlement_order` (`order_id`),
    KEY `idx_settlement_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='还车结算表';

-- -----------------------------------------------------------------------------
-- 本地消息表（Outbox）
-- 事务内先写这里，事务提交后由定时任务投递到 RabbitMQ。
-- -----------------------------------------------------------------------------
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

-- -----------------------------------------------------------------------------
-- 站内消息表
-- 枚举落库为数字编码（Java 枚举带 @EnumValue）：
--   type        : 101/102 系统类、201~207 订单类、301~305 资金类、401 车辆类
--   read_status : 0 未读 / 1 已读
--   deleted     : 逻辑删除，0 正常 / 1 已删除
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `message`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     BIGINT       NOT NULL COMMENT '接收用户ID',
    `order_id`    BIGINT                DEFAULT NULL COMMENT '关联租赁订单ID',
    `title`       VARCHAR(100) NOT NULL COMMENT '消息标题',
    `content`     VARCHAR(500) NOT NULL COMMENT '消息内容',
    `type`        INT          NOT NULL COMMENT '消息类型: MessageType.code',
    `read_status` TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '读取状态: 0 未读 / 1 已读',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME              DEFAULT NULL COMMENT '更新时间',
    `deleted`     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_message_order_type` (`order_id`, `type`),
    KEY `idx_message_user` (`user_id`, `read_status`),
    KEY `idx_message_order` (`order_id`),
    KEY `idx_message_type` (`type`),
    KEY `idx_message_create` (`create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='站内消息表';
