package cn.ff26710.carsharingapp.service;

import cn.ff26710.carsharingapp.entity.Refund;
import cn.ff26710.carsharingapp.entity.enums.RefundType;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

import java.math.BigDecimal;
import java.util.List;

public interface RefundService extends IService<Refund> {
    Refund createRefund(Long paymentId, Long orderId, String orderNo,
                        RefundType refundType, BigDecimal amount, String reason);
    List<Refund> listByOrder(Long orderId);
    IPage<Refund> pageMy(long pageNum, long pageSize);
}
