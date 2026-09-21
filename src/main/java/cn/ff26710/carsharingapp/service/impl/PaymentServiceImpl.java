package cn.ff26710.carsharingapp.service.impl;

import cn.ff26710.carsharingapp.dto.payment.PayOrderDTO;
import cn.ff26710.carsharingapp.entity.Payment;
import cn.ff26710.carsharingapp.entity.RentalOrder;
import cn.ff26710.carsharingapp.entity.User;
import cn.ff26710.carsharingapp.entity.enums.*;
import cn.ff26710.carsharingapp.exception.BusinessException;
import cn.ff26710.carsharingapp.mapper.PaymentMapper;
import cn.ff26710.carsharingapp.mapper.RentalOrderMapper;
import cn.ff26710.carsharingapp.mq.event.RentalNoticeEvent;
import cn.ff26710.carsharingapp.mq.producer.RentalNoticeProducer;
import cn.ff26710.carsharingapp.service.PaymentRecordService;
import cn.ff26710.carsharingapp.service.PaymentRecordService.PreparedPayment;
import cn.ff26710.carsharingapp.service.PaymentService;
import cn.ff26710.carsharingapp.service.WeChatOAuthService;
import cn.ff26710.carsharingapp.service.WeChatPayService;
import cn.ff26710.carsharingapp.utils.AmountUtil;
import cn.ff26710.carsharingapp.utils.SecurityUtil;
import cn.ff26710.carsharingapp.vo.PayResultVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl extends ServiceImpl<PaymentMapper, Payment> implements PaymentService {

    private final RentalOrderMapper rentalOrderMapper;
    private final RentalNoticeProducer rentalNoticeProducer;
    private final WeChatOAuthService weChatOAuthService;
    private final WeChatPayService weChatPayService;
    private final PaymentRecordService paymentRecordService;

    @Override
    public PayResultVO createPayment(PayOrderDTO dto) {
        User loginUser = SecurityUtil.getLoginUser();
        Long userId = loginUser == null ? null : loginUser.getUserId();

        // 事务一：校验订单并准备好 payment 行，方法返回即提交、行锁释放
        PreparedPayment prepared =
                paymentRecordService.preparePayment(dto.getOrderId(), dto.getPayType(), userId);

        switch (dto.getPayMethod()) {
            case BALANCE -> {
                return paymentRecordService.markBalancePaid(prepared);
            }
            case WECHAT -> {
                // 微信调用在事务外，避免 HTTP 期间持有 payment 行锁
                String openId = weChatOAuthService.getOpenId(dto.getCode());
                String description = dto.getPayType() == PayType.RENT_PAY ? "租车订单租金支付" : "租车订单押金支付";
                Map<String, String> payParams = weChatPayService.createJsapiPrepay(
                        prepared.paymentNo(), prepared.amount(), description, openId);
                // 事务二：只回写预下单结果
                return paymentRecordService.markWechatPrepayCreated(prepared, payParams);
            }
            default -> throw new BusinessException("未知的支付方式");
        }
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
            throw new BusinessException(401, "未登录");
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
        if (PaymentStatus.SUCCESS.equals(payment.getStatus())
                || PaymentStatus.REFUNDED.equals(payment.getStatus())) {
            return;
        }
        if (amountCent == null || payment.getAmount().compareTo(AmountUtil.fenToYuan(amountCent)) != 0) {
            throw new BusinessException("支付金额校验失败");
        }

        LocalDateTime now = LocalDateTime.now();
        boolean updated = lambdaUpdate()
                .eq(Payment::getId, payment.getId())
                .eq(Payment::getStatus, PaymentStatus.INIT)
                .set(Payment::getThirdTradeNo, wxTradeNo)
                .set(Payment::getStatus, PaymentStatus.SUCCESS)
                .set(Payment::getRawCallback, rawCallback)
                .set(Payment::getCallbackTime, now)
                .set(Payment::getUpdateTime, now)
                .update();
        if (!updated) {
            return;
        }

        RentalOrder order = rentalOrderMapper.selectById(payment.getOrderId());
        switch (payment.getPayType()) {
            case RENT_PAY -> {
                rentalOrderMapper.update(Wrappers.lambdaUpdate(RentalOrder.class)
                        .eq(RentalOrder::getOrderId, order.getOrderId())
                        .set(RentalOrder::getPaidRent, payment.getAmount()));
                rentalNoticeProducer.publish(RentalNoticeEvent.of(
                        order.getUserId(), payment.getOrderId(), payment.getOrderNo(),
                        MessageType.ORDER_PAID, "租车订单支付成功",
                        "订单【" + payment.getOrderNo() + "】租金已支付。"));
            }
            case DEPOSIT_FROZEN -> {
                rentalOrderMapper.update(Wrappers.lambdaUpdate(RentalOrder.class)
                        .eq(RentalOrder::getOrderId, order.getOrderId())
                        .set(RentalOrder::getPaidDeposit, payment.getAmount()));
                rentalNoticeProducer.publish(RentalNoticeEvent.of(
                        order.getUserId(), payment.getOrderId(), payment.getOrderNo(),
                        MessageType.DEPOSIT_FROZEN, "租车订单押金支付成功",
                        "订单【" + payment.getOrderNo() + "】押金已支付。"));
            }
        }
    }

    @Override
    public void markPaymentFailed(Long paymentId, String tradeState) {
        lambdaUpdate()
                .eq(Payment::getId, paymentId)
                .eq(Payment::getStatus, PaymentStatus.INIT)
                .set(Payment::getStatus, PaymentStatus.FAIL)
                .set(Payment::getRawCallback, "对账查询状态: " + tradeState)
                .set(Payment::getUpdateTime, LocalDateTime.now())
                .update();
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
