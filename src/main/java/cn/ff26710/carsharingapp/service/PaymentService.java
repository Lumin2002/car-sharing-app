package cn.ff26710.carsharingapp.service;

import cn.ff26710.carsharingapp.dto.payment.PayOrderDTO;
import cn.ff26710.carsharingapp.entity.Payment;
import cn.ff26710.carsharingapp.entity.enums.PayType;
import cn.ff26710.carsharingapp.entity.enums.PaymentStatus;
import cn.ff26710.carsharingapp.vo.PayResultVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface PaymentService extends IService<Payment> {
    PayResultVO createPayment(PayOrderDTO dto);
    Payment getPaymentByPaymentNo(String paymentNo);
    List<Payment> listByOrder(Long orderId);
    IPage<Payment> pageMy(long pageNum, long pageSize, PaymentStatus status);
    boolean isPaid(Long orderId, PayType payType);
    void markRefunded(Long paymentId);

    void handleWxPayCallback(String paymentNo, String wxTradeNo, Integer amountCent, String rawCallback);
    void markPaymentFailed(Long paymentId, String tradeState);
}
