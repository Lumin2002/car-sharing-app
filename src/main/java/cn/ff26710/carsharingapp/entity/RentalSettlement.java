package cn.ff26710.carsharingapp.entity;

import cn.ff26710.carsharingapp.entity.enums.SettlementStatus;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("rental_settlement")
public class RentalSettlement {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private String orderNo;

    private Integer actualMileage;
    private Integer preMileage;
    private Integer exceedMileage;
    private BigDecimal exceedMileageFee;

    private Long overtimeMinute;
    private BigDecimal overtimeFee;

    private BigDecimal otherFee;

    private BigDecimal rentAmount;
    private BigDecimal totalSettleAmount;

    private BigDecimal originalDeposit;
    private BigDecimal depositDeductAmount;
    private BigDecimal depositRefundAmount;

    private SettlementStatus status;

    private String remark;

    private LocalDateTime createTime;
    private LocalDateTime confirmTime;
}
