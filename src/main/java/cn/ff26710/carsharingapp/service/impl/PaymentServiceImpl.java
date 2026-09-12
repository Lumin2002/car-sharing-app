package cn.ff26710.carsharingapp.service.impl;

import cn.ff26710.carsharingapp.dto.payment.PayOrderDTO;
import cn.ff26710.carsharingapp.entity.Payment;
import cn.ff26710.carsharingapp.entity.RentalOrder;
import cn.ff26710.carsharingapp.entity.User;
import cn.ff26710.carsharingapp.entity.enums.PayType;
import cn.ff26710.carsharingapp.entity.enums.PaymentStatus;
import cn.ff26710.carsharingapp.entity.enums.MessageType;
import cn.ff26710.carsharingapp.entity.enums.RentalStatus;
import cn.ff26710.carsharingapp.entity.enums.UserRole;
import cn.ff26710.carsharingapp.exception.BusinessException;
import cn.ff26710.carsharingapp.mapper.PaymentMapper;
import cn.ff26710.carsharingapp.mapper.RentalOrderMapper;
import cn.ff26710.carsharingapp.mq.producer.RentalNoticeProducer;
import cn.ff26710.carsharingapp.mq.event.RentalNoticeEvent;
import cn.ff26710.carsharingapp.service.PaymentService;
import cn.ff26710.carsharingapp.utils.SecurityUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl extends ServiceImpl<PaymentMapper, Payment> implements PaymentService {

    private final RentalOrderMapper rentalOrderMapper;
    private final RentalNoticeProducer rentalNoticeProducer;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Payment> payOrder(PayOrderDTO dto) {
        User loginUser = SecurityUtil.getLoginUser();
        RentalOrder order = rentalOrderMapper.selectById(dto.getOrderId());
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (!order.getUserId().equals(loginUser.getUserId())) {
            throw new BusinessException(403, "无权支付该订单");
        }
        // 逾期订单同样允许补缴租金（定时任务可能已把状态置为 OVERDUE）
        if (order.getStatus() != RentalStatus.RENTING && order.getStatus() != RentalStatus.OVERDUE) {
            throw new BusinessException("该订单当前状态不可支付");
        }
        if (isPaid(order.getOrderId())) {
            throw new BusinessException("该订单已支付，请勿重复支付");
        }

        LocalDateTime now = LocalDateTime.now();
        List<Payment> result = new ArrayList<>();

        Payment rentPay = buildPayment(order, PayType.RENT_PAY, order.getRentAmount(), dto.getPayMethod(), now);
        save(rentPay);
        result.add(rentPay);

        BigDecimal deposit = order.getDeposit() == null ? BigDecimal.ZERO : order.getDeposit();
        if (deposit.compareTo(BigDecimal.ZERO) > 0) {
            Payment depositPay = buildPayment(order, PayType.DEPOSIT_FROZEN, deposit, dto.getPayMethod(), now);
            save(depositPay);
            result.add(depositPay);
        }

        rentalNoticeProducer.publish(RentalNoticeEvent.of(
                order.getUserId(), order.getOrderId(), order.getOrderNo(),
                MessageType.ORDER_PAID, "订单支付成功",
                "订单【" + order.getOrderNo() + "】租金 " + order.getRentAmount()
                        + " 元已支付成功，祝您用车愉快。"));

        if (deposit.compareTo(BigDecimal.ZERO) > 0) {
            rentalNoticeProducer.publish(RentalNoticeEvent.of(
                    order.getUserId(), order.getOrderId(), order.getOrderNo(),
                    MessageType.DEPOSIT_FROZEN, "押金冻结通知",
                    "订单【" + order.getOrderNo() + "】已冻结押金 " + deposit
                            + " 元，还车结算后按实际情况退还或扣罚。"));
        }

        return result;
    }

    @Override
    public List<Payment> listByOrder(Long orderId) {
        RentalOrder order = rentalOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        checkOwnerOrAdmin(order);
        return lambdaQuery()
                .eq(Payment::getOrderId, orderId)
                .orderByAsc(Payment::getId)
                .list();
    }

    @Override
    public IPage<Payment> pageMy(long pageNum, long pageSize, PaymentStatus status) {
        Long userId = SecurityUtil.getUserId();
        // 先取我的订单ID
        List<RentalOrder> orders = rentalOrderMapper.selectList(
                new LambdaQueryWrapper<RentalOrder>()
                        .eq(RentalOrder::getUserId, userId)
                        .select(RentalOrder::getOrderId));
        List<Long> orderIds = orders.stream().map(RentalOrder::getOrderId).toList();

        LambdaQueryWrapper<Payment> wrapper = new LambdaQueryWrapper<>();
        if (orderIds.isEmpty()) {
            wrapper.eq(Payment::getOrderId, -1L);
        } else {
            wrapper.in(Payment::getOrderId, orderIds);
        }
        wrapper.eq(status != null, Payment::getStatus, status).orderByDesc(Payment::getId);
        return page(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public boolean isPaid(Long orderId) {
        return lambdaQuery()
                .eq(Payment::getOrderId, orderId)
                .eq(Payment::getPayType, PayType.RENT_PAY)
                .eq(Payment::getStatus, PaymentStatus.SUCCESS)
                .exists();
    }

    @Override
    public void markRefunded(Long paymentId) {
        Payment payment = getById(paymentId);
        if (payment == null) {
            return;
        }
        lambdaUpdate()
                .eq(Payment::getId, paymentId)
                .set(Payment::getStatus, PaymentStatus.REFUNDED)
                .update();
    }

    private Payment buildPayment(RentalOrder order, PayType payType, BigDecimal amount,
                                 cn.ff26710.carsharingapp.entity.enums.PayMethod payMethod, LocalDateTime now) {
        Payment payment = new Payment();
        payment.setOrderId(order.getOrderId());
        payment.setOrderNo(order.getOrderNo());
        payment.setPaymentNo(generateNo("PAY"));
        payment.setPayType(payType);
        payment.setPayMethod(payMethod);
        payment.setAmount(amount == null ? BigDecimal.ZERO : amount);
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setCreateTime(now);
        payment.setCallbackTime(now);
        return payment;
    }

    private void checkOwnerOrAdmin(RentalOrder order) {
        User loginUser = SecurityUtil.getLoginUser();
        if (loginUser.getRole() == UserRole.ADMIN) {
            return;
        }
        if (!order.getUserId().equals(loginUser.getUserId())) {
            throw new BusinessException(403, "无权查看该订单的支付记录");
        }
    }

    private String generateNo(String prefix) {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return prefix + time + ThreadLocalRandom.current().nextInt(1000, 10000);
    }
}
