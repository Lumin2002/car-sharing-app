package cn.ff26710.carsharingapp.entity;

import cn.ff26710.carsharingapp.entity.enums.PayMethod;
import cn.ff26710.carsharingapp.entity.enums.PayType;
import cn.ff26710.carsharingapp.entity.enums.PaymentStatus;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("payment")
public class Payment {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;
    private String orderNo;

    private String paymentNo;
    private String thirdTradeNo;

    private PayType payType;
    private PayMethod payMethod;

    private BigDecimal amount;

    private PaymentStatus status;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private LocalDateTime prepayTime;
    private LocalDateTime callbackTime;

    private String rawCallback;
}
