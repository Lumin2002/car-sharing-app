package cn.ff26710.carsharingapp.mq.producer;

import cn.ff26710.carsharingapp.annotation.PublishMQAfterCommit;
import cn.ff26710.carsharingapp.config.RabbitMQConfig;
import cn.ff26710.carsharingapp.mq.MQEventHolder;
import cn.ff26710.carsharingapp.mq.event.RentalNoticeEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 通知事件的生产者入口。
 *
 * <p>业务代码只管调用 {@link #publish}，投递时机由 {@code @PublishMQAfterCommit} 保证：
 * 有事务就等事务提交后再发，事务回滚则一条都不发；
 * 没有事务（比如定时任务直接调用）则立即发送。
 *
 * <p>路由键和交换机集中在这里，业务代码不需要知道 MQ 拓扑。
 */
@Slf4j
@Component
public class RentalNoticeProducer {

    @PublishMQAfterCommit
    public void publish(RentalNoticeEvent event) {
        MQEventHolder.add(RabbitMQConfig.RENTAL_EXCHANGE, RabbitMQConfig.ROUTING_NOTICE, event);
    }

    @PublishMQAfterCommit
    public void publishOverdue(RentalNoticeEvent event) {
        MQEventHolder.add(RabbitMQConfig.RENTAL_EXCHANGE, RabbitMQConfig.ROUTING_OVERDUE, event);
    }
}
