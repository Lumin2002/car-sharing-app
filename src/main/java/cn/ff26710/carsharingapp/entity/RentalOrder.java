package cn.ff26710.carsharingapp.entity;

import cn.ff26710.carsharingapp.entity.enums.RentalStatus;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("rental_order")
public class RentalOrder extends BaseLogicEntity {
    @TableId(type = IdType.AUTO)
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

    private BigDecimal paidRent;
    private BigDecimal paidDeposit;

    private BigDecimal rentAmount;
    private BigDecimal totalAmount;
    private RentalStatus status;

    private Integer mileageBefore;
    private Integer mileageAfter;

    private String remark;
    private String cancelReason;
}
