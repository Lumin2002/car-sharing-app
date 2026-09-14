package cn.ff26710.carsharingapp.service;

import cn.ff26710.carsharingapp.entity.Payment;
import cn.ff26710.carsharingapp.entity.Refund;
import cn.ff26710.carsharingapp.entity.RentalOrder;
import cn.ff26710.carsharingapp.entity.enums.PayType;
import cn.ff26710.carsharingapp.entity.enums.RefundType;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

import java.math.BigDecimal;
import java.util.List;

public interface RefundService extends IService<Refund> {
    Refund createRefund(Payment payment, RentalOrder order,
                        RefundType refundType, BigDecimal amount, String reason);
    Refund getRefundByRefundNo(String refundNo);
    List<Refund> listByOrder(Long orderId);
    IPage<Refund> pageMy(long pageNum, long pageSize);
    boolean isRefunded(Long orderId, RefundType refundType);

    void handleWxPayCallback(String paymentNo, String wxTradeNo, Long amountCent, String rawCallback);
}
