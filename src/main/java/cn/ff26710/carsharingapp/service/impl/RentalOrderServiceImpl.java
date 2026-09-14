package cn.ff26710.carsharingapp.service.impl;

import cn.ff26710.carsharingapp.dto.rental.RentalCreateDTO;
import cn.ff26710.carsharingapp.dto.rental.RentalPickUpDTO;
import cn.ff26710.carsharingapp.dto.rental.RentalReturnDTO;
import cn.ff26710.carsharingapp.entity.Car;
import cn.ff26710.carsharingapp.entity.Payment;
import cn.ff26710.carsharingapp.entity.RentalOrder;
import cn.ff26710.carsharingapp.entity.User;
import cn.ff26710.carsharingapp.entity.enums.CarStatus;
import cn.ff26710.carsharingapp.entity.enums.MessageType;
import cn.ff26710.carsharingapp.entity.enums.PayType;
import cn.ff26710.carsharingapp.entity.enums.PaymentStatus;
import cn.ff26710.carsharingapp.entity.enums.RefundType;
import cn.ff26710.carsharingapp.entity.enums.RentalStatus;
import cn.ff26710.carsharingapp.entity.enums.UserRole;
import cn.ff26710.carsharingapp.exception.BusinessException;
import cn.ff26710.carsharingapp.mapper.RentalOrderMapper;
import cn.ff26710.carsharingapp.mq.producer.RentalNoticeProducer;
import cn.ff26710.carsharingapp.mq.event.RentalNoticeEvent;
import cn.ff26710.carsharingapp.service.*;
import cn.ff26710.carsharingapp.utils.SecurityUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Set;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class RentalOrderServiceImpl extends ServiceImpl<RentalOrderMapper, RentalOrder> implements RentalOrderService {

    private final CarService carService;
    private final RentalSettlementService rentalSettlementService;
    private final PaymentService paymentService;
    private final RefundService refundService;
    private final BatchNotifyService batchNotifyOverdueOrders;
    private final RentalNoticeProducer rentalNoticeProducer;
    private final MessageService messageService;
    private final DriverLicenseService driverLicenseService;
    private final RealnameAuthService realnameAuthService;

    @Value("${app.rental.cancel-grace-minutes:10}")
    private long cancelGraceMinutes;

    @Value("${app.rental.expire-warn-hours:6}")
    private int expireWarnHours;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createOrder(RentalCreateDTO dto) {
        User loginUser = currentUser();
        LocalDateTime now = LocalDateTime.now();
        if (!dto.getEndTime().isAfter(now)) {
            throw new BusinessException("预计还车时间必须晚于当前时间");
        }

        Car car = carService.getById(dto.getCarId());
        if (car == null) {
            throw new BusinessException("车辆不存在");
        }

        List<RentalStatus> forbidStatusList = Arrays.asList(
                RentalStatus.PENDING,
                RentalStatus.RENTING,
                RentalStatus.OVERDUE
        );

        boolean hasUnfinishedOrder = lambdaQuery()
                .eq(RentalOrder::getUserId, loginUser.getUserId())
                .in(RentalOrder::getStatus, forbidStatusList)
                .exists();

        if (hasUnfinishedOrder) {
            throw new BusinessException("您有未完成的订单，无法创建新订单");
        }

        boolean claimed = carService.lambdaUpdate()
                .eq(Car::getCarId, car.getCarId())
                .eq(Car::getStatus, CarStatus.FREE)
                .set(Car::getStatus, CarStatus.RENTED)
                .set(Car::getCurrentTenantId, loginUser.getUserId())
                .set(Car::getUpdateTime, now)
                .update();
        if (!claimed) {
            throw new BusinessException("该车辆已被租用或不可租，请选择其它车辆");
        }

        int rentDays = calcRentDays(now, dto.getEndTime());
        BigDecimal dailyPrice = car.getDailyPrice();
        BigDecimal deposit = car.getDeposit() == null ? BigDecimal.ZERO : car.getDeposit();
        BigDecimal rentAmount = dailyPrice.multiply(BigDecimal.valueOf(rentDays));

        RentalOrder order = new RentalOrder();
        order.setOrderNo(generateOrderNo());
        order.setUserId(loginUser.getUserId());
        order.setCarId(car.getCarId());
        order.setStoreId(car.getStoreId());
        order.setReturnStoreId(dto.getReturnStoreId());
        order.setStartTime(now);
        order.setEndTime(dto.getEndTime());
        order.setDailyPrice(dailyPrice);
        order.setDeposit(deposit);
        order.setRentDays(rentDays);
        order.setRentAmount(rentAmount);
        order.setTotalAmount(rentAmount.add(deposit));
        order.setStatus(RentalStatus.PENDING);
        order.setMileageBefore(car.getMileage());
        order.setRemark(dto.getRemark());
        order.setCreateTime(now);
        order.setUpdateTime(now);
        save(order);

        rentalNoticeProducer.publish(RentalNoticeEvent.of(
                loginUser.getUserId(), order.getOrderId(), order.getOrderNo(),
                MessageType.ORDER_CREATE, "租车订单创建成功",
                "订单【" + order.getOrderNo() + "】已创建，请及时完成支付（租金 " + rentAmount
                        + " 元 + 押金 " + deposit + " 元）。"));
        return order.getOrderId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pickUpCar(Long orderId, RentalPickUpDTO dto) {
        User loginuser = SecurityUtil.getLoginUser();
        RentalOrder order = getById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (loginuser == null) {
            throw new BusinessException(401,"未登录");
        }
        checkOwnerOrAdmin(order);
        if (!RentalStatus.PENDING.equals(order.getStatus())) {
            throw new BusinessException("该订单当前状态不可取车");
        }
        if (!driverLicenseService.isPassed(loginuser.getUserId())
                || !realnameAuthService.isPassed(loginuser.getUserId())) {
            throw new BusinessException("请完成驾照认证和实名认证后再取车");
        }
        if (!paymentService.isPaid(orderId, PayType.RENT_PAY)) {
            throw new BusinessException("请支付订单租金后再取车");
        }
        if (order.getDeposit().compareTo(BigDecimal.ZERO) > 0
                && !paymentService.isPaid(orderId, PayType.DEPOSIT_FROZEN)) {
            throw new BusinessException("请支付订单押金后再取车");
        }
        LocalDateTime now = LocalDateTime.now();
        order.setStatus(RentalStatus.RENTING);
        order.setUpdateTime(now);
        updateById(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void returnCar(Long orderId, RentalReturnDTO dto) {
        RentalOrder order = getById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        checkOwnerOrAdmin(order);
        if (!RentalStatus.RENTING.equals(order.getStatus())) {
            throw new BusinessException("该订单当前状态不可还车");
        }

        LocalDateTime now = LocalDateTime.now();
        int rentDays = calcRentDays(order.getStartTime(), now);
        BigDecimal rentAmount = order.getDailyPrice().multiply(BigDecimal.valueOf(rentDays));
        BigDecimal deposit = order.getDeposit() == null ? BigDecimal.ZERO : order.getDeposit();

        order.setActualReturnTime(now);
        order.setRentDays(rentDays);
        order.setRentAmount(rentAmount);
        order.setTotalAmount(rentAmount.add(deposit));
        order.setStatus(RentalStatus.RETURNED);
        order.setMileageAfter(dto.getMileageAfter());
        if (dto.getRemark() != null) {
            order.setRemark(dto.getRemark());
        }
        order.setUpdateTime(now);
        updateById(order);

        releaseCar(order.getCarId(), order.getUserId(), now);
        if (dto.getMileageAfter() != null) {
            carService.lambdaUpdate()
                    .eq(Car::getCarId, order.getCarId())
                    .set(Car::getMileage, dto.getMileageAfter())
                    .set(Car::getUpdateTime, now)
                    .update();
        }
        rentalSettlementService.createForOrder(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(Long orderId, String reason) {
        RentalOrder order = getById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        checkOwnerOrAdmin(order);
        if (!RentalStatus.PENDING.equals(order.getStatus())) {
            throw new BusinessException("该订单当前状态不可取消");
        }

        LocalDateTime now = LocalDateTime.now();
        order.setStatus(RentalStatus.CANCELLED);
        order.setCancelReason(reason);
        order.setUpdateTime(now);
        updateById(order);

        releaseCar(order.getCarId(), order.getUserId(), now);

        CancelRefundResult refundResult = cancelRefund(order, now);

        rentalNoticeProducer.publish(RentalNoticeEvent.of(
                order.getUserId(), order.getOrderId(), order.getOrderNo(),
                MessageType.ORDER_CANCEL, "租车订单已取消",
                "订单【" + order.getOrderNo() + "】已取消"
                        + (reason == null || reason.isBlank() ? "。" : "，原因：" + reason + "。")));

        if (refundResult.anyRefund()) {
            rentalNoticeProducer.publish(RentalNoticeEvent.of(
                    order.getUserId(), order.getOrderId(), order.getOrderNo(),
                    MessageType.REFUND_SUCCESS, "退款已发起",
                    "订单【" + order.getOrderNo() + "】取消退款：租金退回 "
                            + refundResult.rentRefund() + " 元，押金解冻 "
                            + refundResult.depositUnfreeze() + " 元。"));
        }
    }

    /** 取消订单时产生的退款汇总，用于组装通知文案 */
    private record CancelRefundResult(BigDecimal rentRefund, BigDecimal depositUnfreeze) {
        boolean anyRefund() {
            return rentRefund.signum() > 0 || depositUnfreeze.signum() > 0;
        }
    }

    private CancelRefundResult cancelRefund(RentalOrder order, LocalDateTime now) {
        List<Payment> payments = paymentService.lambdaQuery()
                .eq(Payment::getOrderId, order.getOrderId())
                .eq(Payment::getStatus, PaymentStatus.SUCCESS)
                // 显式按主键排序：不写排序时 MySQL 可能走 (order_id, pay_type) 索引，
                // 导致退款单的生成顺序随机（押金可能排在租金前面）
                .orderByAsc(Payment::getId)
                .list();
        if (payments.isEmpty()) {
            return new CancelRefundResult(BigDecimal.ZERO, BigDecimal.ZERO);
        }

        long usedMinutes = order.getStartTime() == null
                ? 0
                : Math.max(0, Duration.between(order.getStartTime(), now).toMinutes());
        int billableDays = usedMinutes <= cancelGraceMinutes ? 0 : (int) ((usedMinutes + 1439) / 1440);
        BigDecimal dailyPrice = order.getDailyPrice() == null ? BigDecimal.ZERO : order.getDailyPrice();
        BigDecimal totalRentRefund = BigDecimal.ZERO;
        BigDecimal totalDepositUnfreeze = BigDecimal.ZERO;

        for (Payment payment : payments) {
            BigDecimal amount = payment.getAmount() == null ? BigDecimal.ZERO : payment.getAmount();
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                paymentService.markRefunded(payment.getId());
                continue;
            }

            if (payment.getPayType() == PayType.RENT_PAY) {
                BigDecimal deduct = dailyPrice.multiply(BigDecimal.valueOf(billableDays));
                if (deduct.compareTo(amount) > 0) {
                    deduct = amount;
                }
                deduct = deduct.setScale(2, RoundingMode.HALF_UP);
                BigDecimal refundAmount = amount.subtract(deduct).setScale(2, RoundingMode.HALF_UP);

                if (refundAmount.compareTo(BigDecimal.ZERO) > 0) {
                    String reason = billableDays == 0
                            ? "取消订单：宽限期内取消，租金全额退还"
                            : "取消订单：已用" + billableDays + "天，扣除" + deduct
                                    + "元，退回" + refundAmount + "元";
                    refundService.createRefund(payment, order,
                            RefundType.RENT_REFUND, refundAmount, reason);
                    totalRentRefund = totalRentRefund.add(refundAmount);
                }
            } else if (payment.getPayType() == PayType.DEPOSIT_FROZEN) {
                refundService.createRefund(payment, order,
                        RefundType.DEPOSIT_UNFREEZE, amount, "取消订单：押金解冻退回");
                totalDepositUnfreeze = totalDepositUnfreeze.add(amount);
            }
        }
        return new CancelRefundResult(totalRentRefund, totalDepositUnfreeze);
    }

    @Override
    public IPage<RentalOrder> pageMyOrders(long pageNum, long pageSize, RentalStatus status) {
        User loginUser = currentUser();
        LambdaQueryWrapper<RentalOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RentalOrder::getUserId, loginUser.getUserId())
                .eq(status != null, RentalOrder::getStatus, status)
                .orderByDesc(RentalOrder::getCreateTime);
        return page(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public IPage<RentalOrder> pageOrders(long pageNum, long pageSize, Long userId, Long carId, RentalStatus status) {
        LambdaQueryWrapper<RentalOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(userId != null, RentalOrder::getUserId, userId)
                .eq(carId != null, RentalOrder::getCarId, carId)
                .eq(status != null, RentalOrder::getStatus, status)
                .orderByDesc(RentalOrder::getCreateTime);
        return page(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public RentalOrder getOrderForCurrentUser(Long orderId) {
        RentalOrder order = getById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        checkOwnerOrAdmin(order);
        return order;
    }

    @Override
    public void notifyOverdueOrderRecords() {
        LocalDateTime taskNow = LocalDateTime.now();
        int maxLoop = 100;
        int loop = 0;
        while (loop < maxLoop) {
            LambdaQueryWrapper<RentalOrder> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(RentalOrder::getStatus, RentalStatus.RENTING)
                    .lt(RentalOrder::getEndTime, taskNow)
                    .orderByAsc(RentalOrder::getOrderId);

            int count = 20;
            Page<RentalOrder> page = new Page<>(1, count, false);
            Page<RentalOrder> pageResult = page(page, wrapper);
            List<RentalOrder> records = pageResult.getRecords();
            if (CollectionUtils.isEmpty(records)) {
                break;
            }
            batchNotifyOverdueOrders.batchNotifyOverdueOrders(records);
            loop++;
        }
    }

    @Override
    public void notifySoonExpireOrderRecords() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime deadline = now.plusHours(expireWarnHours);
        int batchSize = 20;
        long lastOrderId = 0L;

        while (true) {
            LambdaQueryWrapper<RentalOrder> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(RentalOrder::getStatus, RentalStatus.RENTING)
                    .gt(RentalOrder::getEndTime, now)
                    .le(RentalOrder::getEndTime, deadline)
                    .gt(RentalOrder::getOrderId, lastOrderId)
                    .orderByAsc(RentalOrder::getOrderId);

            List<RentalOrder> records = page(new Page<>(1, batchSize, false), wrapper).getRecords();
            if (CollectionUtils.isEmpty(records)) {
                break;
            }
            lastOrderId = records.get(records.size() - 1).getOrderId();

            Set<Long> alreadyNotified = messageService.findOrderIdsHavingMsg(
                    records.stream().map(RentalOrder::getOrderId).toList(),
                    MessageType.ORDER_SOON_EXPIRE);
            List<RentalOrder> pending = records.stream()
                    .filter(order -> !alreadyNotified.contains(order.getOrderId()))
                    .toList();
            if (!pending.isEmpty()) {
                batchNotifyOverdueOrders.batchNotifySoonExpireOrders(pending);
            }
        }
    }

    private void releaseCar(Long carId, Long userId, LocalDateTime now) {
        carService.lambdaUpdate()
                .eq(Car::getCarId, carId)
                .eq(Car::getStatus, CarStatus.RENTED)
                .eq(Car::getCurrentTenantId, userId)
                .set(Car::getStatus, CarStatus.FREE)
                .set(Car::getCurrentTenantId, null)
                .set(Car::getUpdateTime, now)
                .update();
    }

    private User currentUser() {
        User loginUser = SecurityUtil.getLoginUser();
        if (loginUser == null) {
            throw new BusinessException(401, "未登录");
        }
        return loginUser;
    }

    private void checkOwnerOrAdmin(RentalOrder order) {
        User loginUser = currentUser();
        if (loginUser.getRole() == UserRole.ADMIN) {
            return;
        }
        if (!order.getUserId().equals(loginUser.getUserId())) {
            throw new BusinessException(403, "无权操作该订单");
        }
    }

    private int calcRentDays(LocalDateTime start, LocalDateTime end) {
        long minutes = Duration.between(start, end).toMinutes();
        if (minutes <= 0) {
            return 1;
        }
        return (int) Math.max(1, (minutes + 1439) / 1440);
    }

    private String generateOrderNo() {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int random = ThreadLocalRandom.current().nextInt(1000, 10000);
        return "RO" + time + random;
    }
}
