package cn.ff26710.carsharingapp.mq.consumer;

import cn.ff26710.carsharingapp.config.RabbitMQConfig;
import cn.ff26710.carsharingapp.mq.event.RentalNoticeEvent;
import cn.ff26710.carsharingapp.service.MessageService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 站内消息消费者：把租赁业务事件落成一条站内消息。
 *
 * <p>两个队列走同一套处理逻辑，只是路由键不同：
 * 普通业务通知走 {@code rental.notice.queue}，逾期提醒走 {@code rental.overdue.queue}
 * （后者单独一条便于以后接短信/推送，不影响业务通知）。
 *
 * <p>手写 ack 的三个要点：
 * <ul>
 *   <li>按「订单 + 消息类型」幂等去重 —— MQ 是至少一次投递，重复消费时必须跳过；</li>
 *   <li>成功 ack；</li>
 *   <li>失败 nack 且 <b>requeue=false</b> —— 失败消息进死信队列，避免无限重投打爆日志。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RentalNoticeConsumer {

    private final MessageService messageService;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NOTICE)
    public void consumeNotice(RentalNoticeEvent event, Channel channel,
                              @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        handle(event, channel, tag, "业务通知");
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_OVERDUE)
    public void consumeOverdue(RentalNoticeEvent event, Channel channel,
                               @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        handle(event, channel, tag, "逾期提醒");
    }

    private void handle(RentalNoticeEvent event, Channel channel, long tag, String scene) throws IOException {
        try {
            if (event.getMessageType() != null
                    && messageService.hasMsg(event.getOrderId(), event.getMessageType())) {
                log.info("[{}]订单 {} 的 {} 消息已存在，跳过重复投递",
                        scene, event.getOrderNo(), event.getMessageType());
                channel.basicAck(tag, false);
                return;
            }

            messageService.sendOrderMsg(event.getUserId(), event.getOrderId(),
                    event.getTitle(), event.getContent(), event.getMessageType());

            log.info("[{}]订单 {} 已生成站内消息: {}", scene, event.getOrderNo(), event.getTitle());
            channel.basicAck(tag, false);
        } catch (Exception e) {
            log.error("[{}]消费失败，消息转入死信队列: {}", scene, event, e);
            // requeue=false：交给死信队列，不然这条消息会被反复投递
            channel.basicNack(tag, false, false);
        }
    }
}
