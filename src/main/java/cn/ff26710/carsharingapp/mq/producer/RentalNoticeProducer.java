package cn.ff26710.carsharingapp.mq.producer;

import cn.ff26710.carsharingapp.config.RabbitMQConfig;
import cn.ff26710.carsharingapp.mq.event.RentalNoticeEvent;
import cn.ff26710.carsharingapp.service.OutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RentalNoticeProducer {

    private final OutboxService outboxService;

    public void publish(RentalNoticeEvent event) {
        outboxService.append(
                RabbitMQConfig.RENTAL_EXCHANGE,
                RabbitMQConfig.ROUTING_NOTICE,
                event,
                event.getOrderNo());
    }

    public void publishOverdue(RentalNoticeEvent event) {
        outboxService.append(
                RabbitMQConfig.RENTAL_EXCHANGE,
                RabbitMQConfig.ROUTING_OVERDUE,
                event,
                event.getOrderNo());
    }
}
