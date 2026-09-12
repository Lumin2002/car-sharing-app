package cn.ff26710.carsharingapp.service;

import cn.ff26710.carsharingapp.dto.payment.PayOrderDTO;
import cn.ff26710.carsharingapp.entity.Payment;
import cn.ff26710.carsharingapp.entity.enums.PaymentStatus;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface PaymentService extends IService<Payment> {
    List<Payment> payOrder(PayOrderDTO dto);
    List<Payment> listByOrder(Long orderId);
    IPage<Payment> pageMy(long pageNum, long pageSize, PaymentStatus status);
    boolean isPaid(Long orderId);
    void markRefunded(Long paymentId);
}
