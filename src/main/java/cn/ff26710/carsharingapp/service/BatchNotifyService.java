package cn.ff26710.carsharingapp.service;

import cn.ff26710.carsharingapp.entity.RentalOrder;
import cn.ff26710.carsharingapp.entity.enums.RentalStatus;
import cn.ff26710.carsharingapp.entity.enums.MessageType;
import cn.ff26710.carsharingapp.mapper.RentalOrderMapper;
import cn.ff26710.carsharingapp.mq.producer.RentalNoticeProducer;
import cn.ff26710.carsharingapp.mq.event.RentalNoticeEvent;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BatchNotifyService {
    private final RentalOrderMapper rentalOrderMapper;
    private final RentalNoticeProducer rentalNoticeProducer;

    @Transactional(rollbackFor = Exception.class)
    public void batchNotifyOverdueOrders(List<RentalOrder> records) {
        for (RentalOrder order : records) {
            int updated = rentalOrderMapper.update(null, new LambdaUpdateWrapper<RentalOrder>()
                    .eq(RentalOrder::getOrderId, order.getOrderId())
                    .eq(RentalOrder::getStatus, RentalStatus.RENTING)
                    .set(RentalOrder::getStatus, RentalStatus.OVERDUE)
                    .set(RentalOrder::getUpdateTime, LocalDateTime.now()));
            if (updated == 0) {
                log.warn("订单{}状态已变更，跳过本次逾期处理", order.getOrderNo());
                continue;
            }
            rentalNoticeProducer.publishOverdue(RentalNoticeEvent.of(
                    order.getUserId(), order.getOrderId(), order.getOrderNo(),
                    MessageType.ORDER_OVERDUE,
                    "租车订单逾期提醒",
                    "您的订单【" + order.getOrderNo() + "】已逾期，请尽快归还车辆，逾期将产生逾期费用。"));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void batchNotifySoonExpireOrders(List<RentalOrder> records) {
        for (RentalOrder order : records) {
            rentalNoticeProducer.publish(RentalNoticeEvent.of(
                    order.getUserId(), order.getOrderId(), order.getOrderNo(),
                    MessageType.ORDER_SOON_EXPIRE,
                    "租车订单即将到期",
                    "订单【" + order.getOrderNo() + "】将于 " + order.getEndTime()
                            + " 到期，请按时归还车辆。"));
        }
    }
}
