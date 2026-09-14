package cn.ff26710.carsharingapp.service.impl;

import cn.ff26710.carsharingapp.dto.payment.PayOrderDTO;
import cn.ff26710.carsharingapp.entity.Payment;
import cn.ff26710.carsharingapp.entity.RentalOrder;
import cn.ff26710.carsharingapp.entity.User;
import cn.ff26710.carsharingapp.entity.enums.*;
import cn.ff26710.carsharingapp.exception.BusinessException;
import cn.ff26710.carsharingapp.mapper.PaymentMapper;
import cn.ff26710.carsharingapp.mapper.RentalOrderMapper;
import cn.ff26710.carsharingapp.mq.producer.RentalNoticeProducer;
import cn.ff26710.carsharingapp.mq.event.RentalNoticeEvent;
import cn.ff26710.carsharingapp.service.PaymentService;
import cn.ff26710.carsharingapp.service.WxPayService;
import cn.ff26710.carsharingapp.utils.AmountUtil;
import cn.ff26710.carsharingapp.utils.SecurityUtil;
import cn.ff26710.carsharingapp.utils.SnowflakeUtil;
import cn.ff26710.carsharingapp.vo.PayResultVO;
import cn.hutool.core.lang.Snowflake;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
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
    private final SnowflakeUtil snowflakeUtil;
    private final WxPayService wxPayService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PayResultVO createPayment(PayOrderDTO dto) {
        User loginUser = SecurityUtil.getLoginUser();
        RentalOrder order = rentalOrderMapper.selectById(dto.getOrderId());
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (loginUser != null && !order.getUserId().equals(loginUser.getUserId())) {
            throw new BusinessException(403, "无权支付该订单");
        }
        // 逾期订单同样允许补缴租金（定时任务可能已把状态置为 OVERDUE）
        RentalStatus status = order.getStatus();
        if (!RentalStatus.PENDING.equals(status)
                && !RentalStatus.RENTING.equals(status)
                && !RentalStatus.OVERDUE.equals(status)) {
            throw new BusinessException("该订单当前状态不可支付");
        }
        if (isPaid(order.getOrderId(), dto.getPayType())) {
            throw new BusinessException("该款项已支付，请勿重复支付");
        }
        Payment payment = buildPayment(order, dto.getPayType());
        PayResultVO vo = new PayResultVO();
        vo.setPaymentNo(payment.getPaymentNo());
        switch (dto.getPayMethod()) {
            case BALANCE -> {
                payment.setPayMethod(PayMethod.BALANCE);
                payment.setStatus(PaymentStatus.SUCCESS);
                payment.setUpdateTime(LocalDateTime.now());
                vo.setPaymentStatus(PaymentStatus.SUCCESS);
                vo.setCodeUrl(null);
                payment.setCallbackTime(LocalDateTime.now());
                if (loginUser != null) {
                    notifyResult(loginUser.getUserId(), order, dto.getPayType());
                }
            }
            case WECHAT -> {
                String description = dto.getPayType() == PayType.RENT_PAY ? "租车订单租金支付" : "租车订单押金支付";
                String codeUrl = wxPayService.createNativePay(payment.getPaymentNo(), payment.getAmount(), description);
                payment.setPayMethod(PayMethod.WECHAT);
                payment.setPrepayTime(LocalDateTime.now());
                payment.setUpdateTime(LocalDateTime.now());
                vo.setPaymentStatus(PaymentStatus.INIT);
                vo.setCodeUrl(codeUrl);
            }
            default -> throw new BusinessException("未知的支付方式");
        }
        updateById(payment);
        return vo;
    }

    @Override
    public Payment getPaymentByPaymentNo(String paymentNo) {
        return lambdaQuery().eq(Payment::getPaymentNo, paymentNo).one();
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
        User loginUser = SecurityUtil.getLoginUser();
        if (loginUser == null) {
            throw new BusinessException(401,"未登录");
        }
        List<RentalOrder> orders = rentalOrderMapper.selectList(
                new LambdaQueryWrapper<RentalOrder>()
                        .eq(RentalOrder::getUserId, loginUser.getUserId())
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
    public boolean isPaid(Long orderId, PayType payType) {
        return lambdaQuery()
                .eq(Payment::getOrderId, orderId)
                .eq(Payment::getPayType, payType)
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleWxPayCallback(String paymentNo, String wxTradeNo, Integer amountCent, String rawCallback) {
        Payment payment = getPaymentByPaymentNo(paymentNo);
        if (payment == null) {
            throw new BusinessException("支付单不存在");
        }
        if (amountCent == null || payment.getAmount().compareTo(AmountUtil.fenToYuan(amountCent)) != 0) {
            throw new BusinessException("支付金额校验失败");
        }
        payment.setThirdTradeNo(wxTradeNo);
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setRawCallback(rawCallback);
        payment.setCallbackTime(LocalDateTime.now());
        payment.setUpdateTime(LocalDateTime.now());
        updateById(payment);
        RentalOrder order = rentalOrderMapper.selectById(payment.getOrderId());
        switch (payment.getPayType()) {
            case RENT_PAY -> rentalNoticeProducer.publish(RentalNoticeEvent.of(
                    order.getUserId(), payment.getOrderId(), payment.getOrderNo(),
                    MessageType.ORDER_PAID, "租车订单支付成功",
                    "订单【" + payment.getOrderNo() + "】租金已支付。"));
            case DEPOSIT_FROZEN -> rentalNoticeProducer.publish(RentalNoticeEvent.of(
                    order.getUserId(), payment.getOrderId(), payment.getOrderNo(),
                    MessageType.DEPOSIT_FROZEN, "租车订单押金支付成功",
                    "订单【" + payment.getOrderNo() + "】押金已支付。"));
        }
    }

    private Payment buildPayment(RentalOrder order, PayType payType) {
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
            } else {
                payment.setPaymentNo(snowflakeUtil.generateNo("PAY"));
                payment.setUpdateTime(now);
                payment.setPrepayTime(null);
                payment.setThirdTradeNo(null);
                payment.setCallbackTime(null);
                updateById(payment);
                return payment;
            }
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

    private void notifyResult(Long userId, RentalOrder order, PayType payType) {
        switch (payType) {
            case RENT_PAY -> rentalNoticeProducer.publish(RentalNoticeEvent.of(
                    userId, order.getOrderId(), order.getOrderNo(),
                    MessageType.ORDER_PAID, "租车订单支付成功",
                    "订单【" + order.getOrderNo() + "】租金已支付。"));
            case DEPOSIT_FROZEN -> rentalNoticeProducer.publish(RentalNoticeEvent.of(
                    userId, order.getOrderId(), order.getOrderNo(),
                    MessageType.DEPOSIT_FROZEN, "租车订单押金支付成功",
                    "订单【" + order.getOrderNo() + "】押金已支付。"));
        }
    }

    private void checkOwnerOrAdmin(RentalOrder order) {
        User loginUser = SecurityUtil.getLoginUser();
        if (loginUser != null && loginUser.getRole() == UserRole.ADMIN) {
            return;
        }
        if (loginUser != null && !order.getUserId().equals(loginUser.getUserId())) {
            throw new BusinessException(403, "无权查看该订单的支付记录");
        }
    }
}
