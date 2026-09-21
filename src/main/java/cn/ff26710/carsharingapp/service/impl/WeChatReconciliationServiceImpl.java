package cn.ff26710.carsharingapp.service.impl;

import cn.ff26710.carsharingapp.entity.Payment;
import cn.ff26710.carsharingapp.entity.Refund;
import cn.ff26710.carsharingapp.entity.enums.PayMethod;
import cn.ff26710.carsharingapp.entity.enums.PaymentStatus;
import cn.ff26710.carsharingapp.entity.enums.RefundStatus;
import cn.ff26710.carsharingapp.mapper.PaymentMapper;
import cn.ff26710.carsharingapp.mapper.RefundMapper;
import cn.ff26710.carsharingapp.service.PaymentService;
import cn.ff26710.carsharingapp.service.RefundService;
import cn.ff26710.carsharingapp.service.WeChatPayService;
import cn.ff26710.carsharingapp.service.WeChatReconciliationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeChatReconciliationServiceImpl implements WeChatReconciliationService {

    private static final long RECONCILE_DELAY_MINUTES = 1;
    private static final int RECONCILE_BATCH_SIZE = 100;
    private static final int MAX_RETRY_COUNT = 5;

    private final PaymentMapper paymentMapper;
    private final RefundMapper refundMapper;
    private final PaymentService paymentService;
    private final RefundService refundService;
    private final WeChatPayService weChatPayService;

    @Override
    public void reconcilePayments() {
        LocalDateTime now = LocalDateTime.now();
        List<Payment> pendingPayments = paymentMapper.selectList(
                new LambdaQueryWrapper<Payment>()
                        .eq(Payment::getStatus, PaymentStatus.INIT)
                        .eq(Payment::getPayMethod, PayMethod.WECHAT)
                        .le(Payment::getCreateTime, now.minusMinutes(RECONCILE_DELAY_MINUTES))
                        .last("LIMIT " + RECONCILE_BATCH_SIZE));

        for (Payment payment : pendingPayments) {
            reconcilePayment(payment);
        }
    }

    @Override
    public void reconcileRefunds() {
        LocalDateTime now = LocalDateTime.now();

        List<Refund> applyingRefunds = refundMapper.selectList(
                new LambdaQueryWrapper<Refund>()
                        .eq(Refund::getStatus, RefundStatus.APPLY)
                        .le(Refund::getCreateTime, now.minusMinutes(RECONCILE_DELAY_MINUTES))
                        .last("LIMIT " + RECONCILE_BATCH_SIZE));
        for (Refund refund : applyingRefunds) {
            reconcileApplyingRefund(refund);
        }

        List<Refund> retryingRefunds = refundMapper.selectList(
                new LambdaQueryWrapper<Refund>()
                        .eq(Refund::getStatus, RefundStatus.FAIL)
                        .lt(Refund::getRetryCount, MAX_RETRY_COUNT)
                        .isNotNull(Refund::getNextRetryTime)
                        .le(Refund::getNextRetryTime, now)
                        .last("LIMIT " + RECONCILE_BATCH_SIZE));
        for (Refund refund : retryingRefunds) {
            retryFailedRefund(refund);
        }
    }

    private void reconcilePayment(Payment payment) {
        try {
            WeChatPayService.PaymentQueryResult result = weChatPayService.queryPayment(payment.getPaymentNo());
            if (result == null) {
                return;
            }
            if ("SUCCESS".equalsIgnoreCase(result.tradeState())) {
                paymentService.handleWxPayCallback(
                        payment.getPaymentNo(), result.wxTradeNo(), result.amountCent(), null);
            } else if ("CLOSED".equalsIgnoreCase(result.tradeState())
                    || "REVOKED".equalsIgnoreCase(result.tradeState())
                    || "PAYERROR".equalsIgnoreCase(result.tradeState())) {
                paymentService.markPaymentFailed(payment.getId(), result.tradeState());
            }
        } catch (Exception e) {
            log.warn("微信支付对账查询失败，paymentNo={}，等待下轮重试", payment.getPaymentNo(), e);
        }
    }

    private void reconcileApplyingRefund(Refund refund) {
        try {
            WeChatPayService.RefundQueryResult result = weChatPayService.queryRefund(refund.getRefundNo());
            if (result == null) {
                return;
            }
            if ("SUCCESS".equalsIgnoreCase(result.status())) {
                refundService.handleWxPayCallback(
                        refund.getRefundNo(), result.wxRefundNo(), result.amountCent(), null);
            } else if (isTerminalWeChatStatus(result.status())) {
                refundService.markTerminalFailed(refund.getId(), "微信退款查询状态: " + result.status());
            }
        } catch (Exception e) {
            log.warn("微信退款对账查询失败，refundNo={}，等待下轮重试", refund.getRefundNo(), e);
        }
    }

    private void retryFailedRefund(Refund refund) {
        Payment payment = paymentService.getById(refund.getPaymentId());
        if (payment == null) {
            refundService.markTerminalFailed(refund.getId(), "原支付单不存在");
            return;
        }

        try {
            WeChatPayService.RefundQueryResult result = weChatPayService.queryRefund(refund.getRefundNo());
            if (result != null) {
                if ("SUCCESS".equalsIgnoreCase(result.status())) {
                    refundService.handleWxPayCallback(
                            refund.getRefundNo(), result.wxRefundNo(), result.amountCent(), null);
                    return;
                }
                if ("PROCESSING".equalsIgnoreCase(result.status())) {
                    refundService.markRefundProcessing(refund.getId());
                    return;
                }
                if (isTerminalWeChatStatus(result.status())) {
                    refundService.markTerminalFailed(refund.getId(), "微信退款查询状态: " + result.status());
                    return;
                }
            }
        } catch (Exception e) {
            log.warn("微信退款重试前查询失败，refundNo={}，尝试重新提交", refund.getRefundNo(), e);
        }

        try {
            weChatPayService.refund(
                    payment.getPaymentNo(),
                    refund.getRefundNo(),
                    refund.getReason(),
                    refund.getAmount(),
                    payment.getAmount());
            refundService.markRefundProcessing(refund.getId());
        } catch (Exception e) {
            log.error("微信退款重试提交失败，refundNo={}", refund.getRefundNo(), e);
            refundService.markRefundFailed(refund.getId(), e.getMessage());
        }
    }

    private boolean isTerminalWeChatStatus(String status) {
        return "CLOSED".equalsIgnoreCase(status) || "ABNORMAL".equalsIgnoreCase(status);
    }
}
