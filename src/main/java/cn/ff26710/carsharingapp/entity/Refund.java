package cn.ff26710.carsharingapp.entity;

import cn.ff26710.carsharingapp.entity.enums.RefundStatus;
import cn.ff26710.carsharingapp.entity.enums.RefundType;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("refund")
public class Refund {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long paymentId;
    private Long orderId;
    private String orderNo;

    private String refundNo;
    private String thirdRefundNo;

    private RefundType refundType;

    private BigDecimal amount;

    private RefundStatus status;

    private String reason;

    private LocalDateTime createTime;
    private LocalDateTime callbackTime;

    private String rawCallback;

    private String failReason;
    private Integer retryCount;
    private LocalDateTime nextRetryTime;
}
