package cn.ff26710.carsharingapp.mq.producer;

import cn.ff26710.carsharingapp.annotation.PublishMQAfterCommit;
import cn.ff26710.carsharingapp.config.RabbitMQConfig;
import cn.ff26710.carsharingapp.mq.MQEventHolder;
import cn.ff26710.carsharingapp.mq.event.RentalNoticeEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
public class RentalNoticeProducer {

    @PublishMQAfterCommit
    public void publish(RentalNoticeEvent event) {
        MQEventHolder.add(
                RabbitMQConfig.RENTAL_EXCHANGE,
                RabbitMQConfig.ROUTING_NOTICE, event);
    }

    @PublishMQAfterCommit
    public void publishOverdue(RentalNoticeEvent event) {
        MQEventHolder.add(
                RabbitMQConfig.RENTAL_EXCHANGE,
                RabbitMQConfig.ROUTING_OVERDUE, event);
    }
}
