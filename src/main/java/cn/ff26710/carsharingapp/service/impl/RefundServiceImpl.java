package cn.ff26710.carsharingapp.service.impl;

import cn.ff26710.carsharingapp.entity.Payment;
import cn.ff26710.carsharingapp.entity.Refund;
import cn.ff26710.carsharingapp.entity.RentalOrder;
import cn.ff26710.carsharingapp.entity.User;
import cn.ff26710.carsharingapp.entity.enums.*;
import cn.ff26710.carsharingapp.exception.BusinessException;
import cn.ff26710.carsharingapp.mapper.RefundMapper;
import cn.ff26710.carsharingapp.mapper.RentalOrderMapper;
import cn.ff26710.carsharingapp.service.PaymentService;
import cn.ff26710.carsharingapp.service.RefundService;
import cn.ff26710.carsharingapp.service.WeChatPayService;
import cn.ff26710.carsharingapp.utils.AmountUtil;
import cn.ff26710.carsharingapp.utils.SecurityUtil;
import cn.ff26710.carsharingapp.utils.SnowflakeUtil;
import cn.ff26710.carsharingapp.utils.TransactionUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefundServiceImpl extends ServiceImpl<RefundMapper, Refund> implements RefundService {

    private static final int MAX_RETRY_COUNT = 5;

    private final RentalOrderMapper rentalOrderMapper;
    private final PaymentService paymentService;
    private final WeChatPayService weChatPayService;
    private final SnowflakeUtil snowflakeUtil;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Refund createRefund(Payment payment, RentalOrder order,
                               RefundType refundType, BigDecimal amount, String reason) {
        if (payment == null) {
            throw new BusinessException("支付表不能为null");
        }
        if (amount == null) {
            throw new BusinessException("退款金额不能为空");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("退款金额不能为0或负数");
        }
        if (amount.compareTo(payment.getAmount()) > 0) {
            throw new BusinessException("退款金额不能大于支付单的金额");
        }
        if (isRefunded(order.getOrderId(), refundType)) {
            throw new BusinessException("该款项已退款，请勿重复发起退款请求");
        }

        Refund refund = getOne(Wrappers.lambdaQuery(Refund.class)
                .eq(Refund::getOrderId, order.getOrderId())
                .eq(Refund::getRefundType, refundType)
                .last("FOR UPDATE"));
        if (refund != null && RefundStatus.APPLY.equals(refund.getStatus())) {
            return refund;
        }

        LocalDateTime now = LocalDateTime.now();
        refund = new Refund();
        refund.setPaymentId(payment.getId());
        refund.setOrderId(order.getOrderId());
        refund.setOrderNo(order.getOrderNo());
        refund.setRefundNo(snowflakeUtil.generateNo("REF"));
        refund.setRefundType(refundType);
        refund.setAmount(amount);
        refund.setReason(reason);
        refund.setCreateTime(now);
        refund.setStatus(RefundStatus.APPLY);
        refund.setRetryCount(0);
        save(refund);

        switch (payment.getPayMethod()) {
            case BALANCE -> {
                refund.setStatus(RefundStatus.SUCCESS);
                refund.setCallbackTime(now);
                refund.setRawCallback(null);
                refund.setThirdRefundNo(null);
                paymentService.markRefunded(refund.getPaymentId());
                updateById(refund);
            }
            case WECHAT -> scheduleWeChatRefund(refund.getId(), payment.getId());
            default -> throw new BusinessException("未知支付方式");
        }
        return refund;
    }

    @Override
    public Refund getRefundByRefundNo(String refundNo) {
        return lambdaQuery().eq(Refund::getRefundNo, refundNo).one();
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

    @Override
    public boolean isRefunded(Long orderId, RefundType refundType) {
        return lambdaQuery()
                .eq(Refund::getOrderId, orderId)
                .eq(Refund::getRefundType, refundType)
                .eq(Refund::getStatus, RefundStatus.SUCCESS)
                .exists();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleWxPayCallback(String refundNo, String wxRefundNo, Long amountCent, String rawCallback) {
        Refund refund = getRefundByRefundNo(refundNo);
        if (refund == null) {
            throw new BusinessException("退款单不存在");
        }
        if (RefundStatus.SUCCESS.equals(refund.getStatus())) {
            return;
        }
        if (amountCent == null || refund.getAmount().compareTo(AmountUtil.fenLongToYuan(amountCent)) != 0) {
            throw new BusinessException("退款金额校验失败");
        }
        refund.setStatus(RefundStatus.SUCCESS);
        refund.setThirdRefundNo(wxRefundNo);
        refund.setCallbackTime(LocalDateTime.now());
        refund.setRawCallback(rawCallback);
        refund.setFailReason(null);
        refund.setNextRetryTime(null);
        paymentService.markRefunded(refund.getPaymentId());
        updateById(refund);
    }

    private void scheduleWeChatRefund(Long refundId, Long paymentId) {
        // 外部微信调用放到事务提交后执行，避免占用数据库事务。
        TransactionUtil.afterCommitAsync(() -> submitWeChatRefund(refundId, paymentId));
    }

    private void submitWeChatRefund(Long refundId, Long paymentId) {
        Refund refund = getById(refundId);
        Payment payment = paymentService.getById(paymentId);
        if (refund == null || payment == null || !RefundStatus.APPLY.equals(refund.getStatus())) {
            return;
        }
        try {
            weChatPayService.refund(
                    payment.getPaymentNo(),
                    refund.getRefundNo(),
                    refund.getReason(),
                    refund.getAmount(),
                    payment.getAmount());
        } catch (Exception e) {
            log.error("微信退款提交失败，refundNo={}", refund.getRefundNo(), e);
            markRefundFailed(refund.getId(), e.getMessage());
        }
    }

    @Override
    public void markRefundProcessing(Long refundId) {
        lambdaUpdate()
                .eq(Refund::getId, refundId)
                .set(Refund::getStatus, RefundStatus.APPLY)
                .set(Refund::getFailReason, null)
                .set(Refund::getNextRetryTime, null)
                .update();
    }

    @Override
    public void markRefundFailed(Long refundId, String reason) {
        Refund refund = getById(refundId);
        if (refund == null) {
            return;
        }
        int currentRetry = refund.getRetryCount() == null ? 0 : refund.getRetryCount();
        int nextRetry = currentRetry + 1;
        LocalDateTime nextRetryTime = null;
        if (nextRetry < MAX_RETRY_COUNT) {
            long delayMinutes = Math.min(60, 1L << Math.max(0, nextRetry - 1));
            nextRetryTime = LocalDateTime.now().plusMinutes(delayMinutes);
        }
        lambdaUpdate()
                .eq(Refund::getId, refundId)
                .set(Refund::getStatus, RefundStatus.FAIL)
                .set(Refund::getFailReason, truncate(reason))
                .set(Refund::getRetryCount, nextRetry)
                .set(Refund::getNextRetryTime, nextRetryTime)
                .update();
    }

    @Override
    public void markTerminalFailed(Long refundId, String reason) {
        lambdaUpdate()
                .eq(Refund::getId, refundId)
                .set(Refund::getStatus, RefundStatus.FAIL)
                .set(Refund::getFailReason, truncate(reason))
                .set(Refund::getRetryCount, MAX_RETRY_COUNT)
                .set(Refund::getNextRetryTime, null)
                .update();
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 255 ? value : value.substring(0, 255);
    }

    private void checkOwnerOrAdmin(RentalOrder order) {
        User loginUser = SecurityUtil.getLoginUser();
        if (loginUser != null && loginUser.getRole() == UserRole.ADMIN) {
            return;
        }
        if (loginUser != null && !order.getUserId().equals(loginUser.getUserId())) {
            throw new BusinessException(403, "无权查看该订单的退款记录");
        }
    }
}
