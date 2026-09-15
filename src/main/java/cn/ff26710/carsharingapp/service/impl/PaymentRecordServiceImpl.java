package cn.ff26710.carsharingapp.service.impl;

import cn.ff26710.carsharingapp.entity.Payment;
import cn.ff26710.carsharingapp.entity.RentalOrder;
import cn.ff26710.carsharingapp.entity.enums.MessageType;
import cn.ff26710.carsharingapp.entity.enums.PayMethod;
import cn.ff26710.carsharingapp.entity.enums.PayType;
import cn.ff26710.carsharingapp.entity.enums.PaymentStatus;
import cn.ff26710.carsharingapp.entity.enums.RentalStatus;
import cn.ff26710.carsharingapp.exception.BusinessException;
import cn.ff26710.carsharingapp.mapper.PaymentMapper;
import cn.ff26710.carsharingapp.mapper.RentalOrderMapper;
import cn.ff26710.carsharingapp.mq.event.RentalNoticeEvent;
import cn.ff26710.carsharingapp.mq.producer.RentalNoticeProducer;
import cn.ff26710.carsharingapp.service.PaymentRecordService;
import cn.ff26710.carsharingapp.utils.SnowflakeUtil;
import cn.ff26710.carsharingapp.vo.PayResultVO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PaymentRecordServiceImpl extends ServiceImpl<PaymentMapper, Payment>
        implements PaymentRecordService {

    private final RentalOrderMapper rentalOrderMapper;
    private final RentalNoticeProducer rentalNoticeProducer;
    private final SnowflakeUtil snowflakeUtil;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PreparedPayment preparePayment(Long orderId, PayType payType, Long userId) {
        RentalOrder order = rentalOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (userId != null && !order.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权支付该订单");
        }
        // 逾期订单同样允许补缴租金（定时任务可能已把状态置为 OVERDUE）
        RentalStatus status = order.getStatus();
        if (!RentalStatus.PENDING.equals(status)
                && !RentalStatus.RENTING.equals(status)
                && !RentalStatus.OVERDUE.equals(status)) {
            throw new BusinessException("该订单当前状态不可支付");
        }
        if (isPaid(orderId, payType)) {
            throw new BusinessException("该款项已支付，请勿重复支付");
        }

        Payment payment = getOrCreatePaymentRecord(order, payType);
        return new PreparedPayment(payment.getId(), payment.getPaymentNo(), payment.getAmount(),
                order.getOrderId(), order.getOrderNo(), order.getUserId(), payType);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PayResultVO markBalancePaid(PreparedPayment prepared) {
        LocalDateTime now = LocalDateTime.now();
        boolean updated = lambdaUpdate()
                .eq(Payment::getId, prepared.paymentId())
                .eq(Payment::getStatus, PaymentStatus.INIT)
                .set(Payment::getPayMethod, PayMethod.BALANCE)
                .set(Payment::getStatus, PaymentStatus.SUCCESS)
                .set(Payment::getCallbackTime, now)
                .set(Payment::getUpdateTime, now)
                .update();
        if (!updated) {
            throw new BusinessException("支付单状态已变更，请刷新后重试");
        }

        notifyPaid(prepared);
        return buildResult(prepared.paymentNo(), PaymentStatus.SUCCESS, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PayResultVO markWechatPrepayCreated(PreparedPayment prepared, Map<String, String> payParams) {
        LocalDateTime now = LocalDateTime.now();
        boolean updated = lambdaUpdate()
                .eq(Payment::getId, prepared.paymentId())
                .eq(Payment::getStatus, PaymentStatus.INIT)
                .set(Payment::getPayMethod, PayMethod.WECHAT)
                .set(Payment::getPrepayTime, now)
                .set(Payment::getUpdateTime, now)
                .update();
        if (!updated) {
            throw new BusinessException("支付单状态已变更，请刷新后重试");
        }

        return buildResult(prepared.paymentNo(), PaymentStatus.INIT, payParams);
    }

    private boolean isPaid(Long orderId, PayType payType) {
        return lambdaQuery()
                .eq(Payment::getOrderId, orderId)
                .eq(Payment::getPayType, payType)
                .eq(Payment::getStatus, PaymentStatus.SUCCESS)
                .exists();
    }

    /**
     * 查/建本次支付对应的 payment 行。
     *
     * <p>必须在事务内调用：依赖 {@code FOR UPDATE} 的悲观锁避免并发创建两条支付单，
     * 30 分钟内复用同一条预支付单，超过则换新的 paymentNo 重新发起。
     */
    private Payment getOrCreatePaymentRecord(RentalOrder order, PayType payType) {
        Payment payment = getOne(Wrappers.lambdaQuery(Payment.class)
                .eq(Payment::getOrderId, order.getOrderId())
                .eq(Payment::getPayType, payType)
                .last("FOR UPDATE"));
        LocalDateTime now = LocalDateTime.now();
        if (payment != null && PaymentStatus.INIT.equals(payment.getStatus())) {
            if (payment.getPrepayTime() == null) {
                return payment;
            }
            Duration duration = Duration.between(payment.getPrepayTime(), now);
            if (duration.compareTo(Duration.ofMinutes(30)) <= 0) {
                return payment;
            }
            payment.setPaymentNo(snowflakeUtil.generateNo("PAY"));
            payment.setUpdateTime(now);
            payment.setPrepayTime(null);
            payment.setThirdTradeNo(null);
            payment.setCallbackTime(null);
            updateById(payment);
            return payment;
        }

        payment = new Payment();
        payment.setOrderId(order.getOrderId());
        payment.setOrderNo(order.getOrderNo());
        payment.setPaymentNo(snowflakeUtil.generateNo("PAY"));
        payment.setPayType(payType);
        switch (payType) {
            case RENT_PAY ->
                    payment.setAmount(order.getRentAmount() == null ? BigDecimal.ZERO : order.getRentAmount());
            case DEPOSIT_FROZEN ->
                    payment.setAmount(order.getDeposit() == null ? BigDecimal.ZERO : order.getDeposit());
            default -> throw new BusinessException("未知支付类型");
        }
        payment.setStatus(PaymentStatus.INIT);
        payment.setCreateTime(now);
        payment.setUpdateTime(now);
        save(payment);
        return payment;
    }

    private void notifyPaid(PreparedPayment prepared) {
        switch (prepared.payType()) {
            case RENT_PAY -> rentalNoticeProducer.publish(RentalNoticeEvent.of(
                    prepared.userId(), prepared.orderId(), prepared.orderNo(),
                    MessageType.ORDER_PAID, "租车订单支付成功",
                    "订单【" + prepared.orderNo() + "】租金已支付。"));
            case DEPOSIT_FROZEN -> rentalNoticeProducer.publish(RentalNoticeEvent.of(
                    prepared.userId(), prepared.orderId(), prepared.orderNo(),
                    MessageType.DEPOSIT_FROZEN, "租车订单押金支付成功",
                    "订单【" + prepared.orderNo() + "】押金已支付。"));
        }
    }

    private PayResultVO buildResult(String paymentNo, PaymentStatus status, Map<String, String> payParams) {
        PayResultVO vo = new PayResultVO();
        vo.setPaymentNo(paymentNo);
        vo.setPaymentStatus(status);
        vo.setPayParamMap(payParams);
        return vo;
    }
}
