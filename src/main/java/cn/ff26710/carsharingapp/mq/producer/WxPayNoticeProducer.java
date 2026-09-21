package cn.ff26710.carsharingapp.mq.producer;

import cn.ff26710.carsharingapp.config.RabbitMQConfig;
import cn.ff26710.carsharingapp.mq.event.WxPayNoticeEvent;
import cn.ff26710.carsharingapp.mq.event.WxPayRefundNoticeEvent;
import cn.ff26710.carsharingapp.service.OutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WxPayNoticeProducer {

    private final OutboxService outboxService;

    public void publishPaymentNotice(WxPayNoticeEvent event) {
        outboxService.append(
                RabbitMQConfig.RENTAL_EXCHANGE,
                RabbitMQConfig.ROUTING_WX_PAY_PAYMENT,
                event,
                event.getPaymentNo());
    }

    public void publishRefundNotice(WxPayRefundNoticeEvent event) {
        outboxService.append(
                RabbitMQConfig.RENTAL_EXCHANGE,
                RabbitMQConfig.ROUTING_WX_PAY_REFUND,
                event,
                event.getRefundNo());
    }
}
