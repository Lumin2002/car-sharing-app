package cn.ff26710.carsharingapp.service.impl;

import cn.ff26710.carsharingapp.entity.Refund;
import cn.ff26710.carsharingapp.entity.RentalOrder;
import cn.ff26710.carsharingapp.entity.User;
import cn.ff26710.carsharingapp.entity.enums.RefundStatus;
import cn.ff26710.carsharingapp.entity.enums.RefundType;
import cn.ff26710.carsharingapp.entity.enums.UserRole;
import cn.ff26710.carsharingapp.exception.BusinessException;
import cn.ff26710.carsharingapp.mapper.RefundMapper;
import cn.ff26710.carsharingapp.mapper.RentalOrderMapper;
import cn.ff26710.carsharingapp.service.RefundService;
import cn.ff26710.carsharingapp.utils.SecurityUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class RefundServiceImpl extends ServiceImpl<RefundMapper, Refund> implements RefundService {

    private final RentalOrderMapper rentalOrderMapper;

    @Override
    public Refund createRefund(Long paymentId, Long orderId, String orderNo,
                               RefundType refundType, BigDecimal amount, String reason) {
        LocalDateTime now = LocalDateTime.now();
        Refund refund = new Refund();
        refund.setPaymentId(paymentId);
        refund.setOrderId(orderId);
        refund.setOrderNo(orderNo);
        refund.setRefundNo(generateNo());
        refund.setRefundType(refundType);
        refund.setAmount(amount == null ? BigDecimal.ZERO : amount);
        refund.setStatus(RefundStatus.SUCCESS);
        refund.setReason(reason);
        refund.setCreateTime(now);
        refund.setCallbackTime(now);
        save(refund);
        return refund;
    }

    @Override
    public List<Refund> listByOrder(Long orderId) {
        RentalOrder order = rentalOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        checkOwnerOrAdmin(order);
        return lambdaQuery().eq(Refund::getOrderId, orderId).orderByAsc(Refund::getId).list();
    }

    @Override
    public IPage<Refund> pageMy(long pageNum, long pageSize) {
        Long userId = SecurityUtil.getUserId();
        List<RentalOrder> orders = rentalOrderMapper.selectList(
                new LambdaQueryWrapper<RentalOrder>()
                        .eq(RentalOrder::getUserId, userId)
                        .select(RentalOrder::getOrderId));
        List<Long> orderIds = orders.stream().map(RentalOrder::getOrderId).toList();

        LambdaQueryWrapper<Refund> wrapper = new LambdaQueryWrapper<>();
        if (orderIds.isEmpty()) {
            wrapper.eq(Refund::getOrderId, -1L);
        } else {
            wrapper.in(Refund::getOrderId, orderIds);
        }
        wrapper.orderByDesc(Refund::getId);
        return page(new Page<>(pageNum, pageSize), wrapper);
    }

    private void checkOwnerOrAdmin(RentalOrder order) {
        User loginUser = SecurityUtil.getLoginUser();
        if (loginUser.getRole() == UserRole.ADMIN) {
            return;
        }
        if (!order.getUserId().equals(loginUser.getUserId())) {
            throw new BusinessException(403, "无权查看该订单的退款记录");
        }
    }

    private String generateNo() {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return "REF" + time + ThreadLocalRandom.current().nextInt(1000, 10000);
    }
}
