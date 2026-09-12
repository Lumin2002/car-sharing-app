package cn.ff26710.carsharingapp.vo;

import cn.ff26710.carsharingapp.entity.enums.RentalStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 租赁订单（对外）。
 * 相比 RentalOrder 实体只去掉了继承自 BaseLogicEntity 的 deleted 存储标记。
 * userId 保留：运维端订单列表需要按用户筛选，用户在「我的订单」里看到的也是自己的 ID。
 */
@Data
public class RentalOrderVO {
    private Long orderId;
    private String orderNo;

    private Long userId;
    private Long carId;
    private Long storeId;
    private Long returnStoreId;

    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime actualReturnTime;

    private BigDecimal dailyPrice;
    private BigDecimal deposit;
    private Integer rentDays;
    private BigDecimal rentAmount;
    private BigDecimal totalAmount;

    private RentalStatus status;

    private Integer mileageBefore;
    private Integer mileageAfter;
    private String remark;
    private String cancelReason;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
