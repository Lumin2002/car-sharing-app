package cn.ff26710.carsharingapp.service.impl;

import cn.ff26710.carsharingapp.entity.Payment;
import cn.ff26710.carsharingapp.entity.RentalOrder;
import cn.ff26710.carsharingapp.entity.RentalSettlement;
import cn.ff26710.carsharingapp.entity.User;
import cn.ff26710.carsharingapp.entity.enums.PayType;
import cn.ff26710.carsharingapp.entity.enums.PaymentStatus;
import cn.ff26710.carsharingapp.entity.enums.RefundType;
import cn.ff26710.carsharingapp.entity.enums.MessageType;
import cn.ff26710.carsharingapp.entity.enums.SettlementStatus;
import cn.ff26710.carsharingapp.entity.enums.UserRole;
import cn.ff26710.carsharingapp.exception.BusinessException;
import cn.ff26710.carsharingapp.mapper.RentalOrderMapper;
import cn.ff26710.carsharingapp.mapper.RentalSettlementMapper;
import cn.ff26710.carsharingapp.mq.producer.RentalNoticeProducer;
import cn.ff26710.carsharingapp.mq.event.RentalNoticeEvent;
import cn.ff26710.carsharingapp.service.PaymentService;
import cn.ff26710.carsharingapp.service.RefundService;
import cn.ff26710.carsharingapp.service.RentalSettlementService;
import cn.ff26710.carsharingapp.utils.SecurityUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RentalSettlementServiceImpl extends ServiceImpl<RentalSettlementMapper, RentalSettlement>
        implements RentalSettlementService {

    private final RentalOrderMapper rentalOrderMapper;
    private final PaymentService paymentService;
    private final RefundService refundService;
    private final RentalNoticeProducer rentalNoticeProducer;

    @Value("${app.rental.free-mileage-per-day:200}")
    private int freeMileagePerDay;
    @Value("${app.rental.exceed-mileage-fee:1.50}")
    private BigDecimal exceedMileageFee;
    @Value("${app.rental.overtime-fee-per-hour:30}")
    private BigDecimal overtimeFeePerHour;
    @Value("${app.rental.overtime-grace-minutes:30}")
    private long overtimeGraceMinutes;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RentalSettlement createForOrder(RentalOrder order) {
        RentalSettlement exist = getByOrderId(order.getOrderId());
        if (exist != null) {
            return exist;
        }

        int preMileage = order.getMileageBefore() == null ? 0 : order.getMileageBefore();
        int actualMileage = order.getMileageAfter() == null ? preMileage : order.getMileageAfter();
        int rentDays = order.getRentDays() == null || order.getRentDays() < 1 ? 1 : order.getRentDays();

        int freeMileage = freeMileagePerDay * rentDays;
        int exceedMileage = Math.max(0, actualMileage - preMileage - freeMileage);
        BigDecimal exceedFee = exceedMileageFee.multiply(BigDecimal.valueOf(exceedMileage))
                .setScale(2, RoundingMode.HALF_UP);

        long overtimeMinutes = 0;
        if (order.getActualReturnTime() != null && order.getEndTime() != null) {
            long minutes = Duration.between(order.getEndTime(), order.getActualReturnTime()).toMinutes();
            overtimeMinutes = Math.max(0, minutes - overtimeGraceMinutes);
        }
        long overtimeHours = overtimeMinutes == 0 ? 0 : (overtimeMinutes + 59) / 60;
        BigDecimal overtimeFee = overtimeFeePerHour.multiply(BigDecimal.valueOf(overtimeHours))
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal otherFee = BigDecimal.ZERO;
        BigDecimal extraFee = exceedFee.add(overtimeFee).add(otherFee);

        BigDecimal rentAmount = order.getRentAmount() == null ? BigDecimal.ZERO : order.getRentAmount();
        BigDecimal originalDeposit = order.getDeposit() == null ? BigDecimal.ZERO : order.getDeposit();

        BigDecimal deductAmount = extraFee.min(originalDeposit);
        BigDecimal depositRefund = originalDeposit.subtract(deductAmount);

        RentalSettlement settlement = new RentalSettlement();
        settlement.setOrderId(order.getOrderId());
        settlement.setOrderNo(order.getOrderNo());
        settlement.setPreMileage(preMileage);
        settlement.setActualMileage(actualMileage);
        settlement.setExceedMileage(exceedMileage);
        settlement.setExceedMileageFee(exceedFee);
        settlement.setOvertimeMinute(overtimeMinutes);
        settlement.setOvertimeFee(overtimeFee);
        settlement.setOtherFee(otherFee);
        settlement.setRentAmount(rentAmount);
        settlement.setTotalSettleAmount(rentAmount.add(extraFee));
        settlement.setOriginalDeposit(originalDeposit);
        settlement.setDepositDeductAmount(deductAmount);
        settlement.setDepositRefundAmount(depositRefund);
        settlement.setStatus(SettlementStatus.PENDING);
        settlement.setRemark("超里程 " + exceedMileage + "km，超时 " + overtimeMinutes + " 分钟");
        settlement.setCreateTime(LocalDateTime.now());
        save(settlement);

        log.info("【还车结算】订单 {} 生成结算单：超里程费 {}，超时费 {}，押金扣罚 {}，押金退还 {}",
                order.getOrderNo(), exceedFee, overtimeFee, deductAmount, depositRefund);
        return settlement;
    }

    @Override
    public RentalSettlement getByOrderId(Long orderId) {
        RentalOrder order = rentalOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        checkOwnerOrAdmin(order);
        return lambdaQuery().eq(RentalSettlement::getOrderId, orderId).one();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RentalSettlement confirm(Long settlementId) {
        RentalSettlement settlement = getById(settlementId);
        if (settlement == null) {
            throw new BusinessException("结算单不存在");
        }
        if (settlement.getStatus() == SettlementStatus.FINISHED) {
            throw new BusinessException("该结算单已完成，请勿重复确认");
        }

        RentalOrder order = rentalOrderMapper.selectById(settlement.getOrderId());
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        checkOwnerOrAdmin(order);

        LocalDateTime now = LocalDateTime.now();
        lambdaUpdate()
                .eq(RentalSettlement::getId, settlementId)
                .set(RentalSettlement::getStatus, SettlementStatus.CONFIRMED)
                .set(RentalSettlement::getConfirmTime, now)
                .update();

        Payment depositPayment = paymentService.lambdaQuery()
                .eq(Payment::getOrderId, order.getOrderId())
                .eq(Payment::getPayType, PayType.DEPOSIT_FROZEN)
                .eq(Payment::getStatus, PaymentStatus.SUCCESS)
                .one();
        Long paymentId = depositPayment == null ? null : depositPayment.getId();

        BigDecimal refundAmount = settlement.getDepositRefundAmount() == null
                ? BigDecimal.ZERO : settlement.getDepositRefundAmount();
        if (refundAmount.compareTo(BigDecimal.ZERO) > 0) {
            refundService.createRefund(paymentId, order.getOrderId(), order.getOrderNo(),
                    RefundType.DEPOSIT_UNFREEZE, refundAmount, "还车结算：押金退还");
        }

        BigDecimal deductAmount = settlement.getDepositDeductAmount() == null
                ? BigDecimal.ZERO : settlement.getDepositDeductAmount();
        if (deductAmount.compareTo(BigDecimal.ZERO) > 0) {
            refundService.createRefund(paymentId, order.getOrderId(), order.getOrderNo(),
                    RefundType.DEPOSIT_DEDUCT, deductAmount, "还车结算：押金扣罚");
        }


        if (paymentId != null) {
            paymentService.markRefunded(paymentId);
        }

        lambdaUpdate()
                .eq(RentalSettlement::getId, settlementId)
                .set(RentalSettlement::getStatus, SettlementStatus.FINISHED)
                .update();

        // 结算完成通知：总额一条，扣罚/退还各一条（金额为 0 的不发，避免刷屏）
        rentalNoticeProducer.publish(RentalNoticeEvent.of(
                order.getUserId(), order.getOrderId(), order.getOrderNo(),
                MessageType.ORDER_SETTLED, "还车结算完成",
                "订单【" + order.getOrderNo() + "】已结算：租金 " + settlement.getRentAmount()
                        + " 元，超里程费 " + settlement.getExceedMileageFee()
                        + " 元，超时费 " + settlement.getOvertimeFee() + " 元。"));

        if (deductAmount.compareTo(BigDecimal.ZERO) > 0) {
            rentalNoticeProducer.publish(RentalNoticeEvent.of(
                    order.getUserId(), order.getOrderId(), order.getOrderNo(),
                    MessageType.FEE_DEDUCT, "押金扣罚通知",
                    "订单【" + order.getOrderNo() + "】因超里程/超时产生费用，已从押金中扣除 "
                            + deductAmount + " 元。"));
        }

        if (refundAmount.compareTo(BigDecimal.ZERO) > 0) {
            rentalNoticeProducer.publish(RentalNoticeEvent.of(
                    order.getUserId(), order.getOrderId(), order.getOrderNo(),
                    MessageType.DEPOSIT_UNFREEZE, "押金已退还",
                    "订单【" + order.getOrderNo() + "】押金 " + refundAmount + " 元已解冻退还。"));
        }

        return getById(settlementId);
    }

    @Override
    public IPage<RentalSettlement> pageMy(long pageNum, long pageSize) {
        Long userId = SecurityUtil.getUserId();
        List<RentalOrder> orders = rentalOrderMapper.selectList(
                new LambdaQueryWrapper<RentalOrder>()
                        .eq(RentalOrder::getUserId, userId)
                        .select(RentalOrder::getOrderId));
        List<Long> orderIds = orders.stream().map(RentalOrder::getOrderId).toList();

        LambdaQueryWrapper<RentalSettlement> wrapper = new LambdaQueryWrapper<>();
        if (orderIds.isEmpty()) {
            wrapper.eq(RentalSettlement::getOrderId, -1L);
        } else {
            wrapper.in(RentalSettlement::getOrderId, orderIds);
        }
        wrapper.orderByDesc(RentalSettlement::getId);
        return page(new Page<>(pageNum, pageSize), wrapper);
    }

    private void checkOwnerOrAdmin(RentalOrder order) {
        User loginUser = SecurityUtil.getLoginUser();
        if (loginUser != null && loginUser.getRole() == UserRole.ADMIN) {
            return;
        }
        if (loginUser != null && !order.getUserId().equals(loginUser.getUserId())) {
            throw new BusinessException(403, "无权查看该订单的结算单");
        }
    }
}
