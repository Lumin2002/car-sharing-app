package cn.ff26710.carsharingapp.mq.producer;

import cn.ff26710.carsharingapp.config.RabbitMQConfig;
import cn.ff26710.carsharingapp.mq.event.WxPayNoticeEvent;
import cn.ff26710.carsharingapp.mq.event.WxPayRefundNoticeEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WxPayNoticeProducer {
    private final RabbitTemplate rabbitTemplate;
    public void publishPaymentNotice(WxPayNoticeEvent event) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.RENTAL_EXCHANGE,
                RabbitMQConfig.ROUTING_WX_PAY_PAYMENT, event);
    }
    public void publishRefundNotice(WxPayRefundNoticeEvent event) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.RENTAL_EXCHANGE,
                RabbitMQConfig.ROUTING_WX_PAY_REFUND, event);
    }
}
