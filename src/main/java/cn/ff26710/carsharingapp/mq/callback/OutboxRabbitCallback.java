package cn.ff26710.carsharingapp.mq.callback;

import cn.ff26710.carsharingapp.service.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRabbitCallback implements RabbitTemplate.ConfirmCallback, RabbitTemplate.ReturnsCallback{
    private final OutboxService outboxService;
    @Override
    public void confirm(CorrelationData correlationData, boolean ack, String cause) {
        if (correlationData == null) {
            log.warn("correlationData为空，无法追踪消息");
            return;
        }
        Long outboxId = Long.valueOf(correlationData.getId());
        if (ack) {
            // Broker已收到消息
            int affected = outboxService.markSent(outboxId);
            if (affected == 0) {
                log.error("MQ 已ACK，但进行markSent更新失败 outboxId={}", outboxId);
                outboxService.markUnknown(outboxId, "MQ 已ACK，但进行markSent更新失败");
            }
        } else {
            // Broker没有收到消息，可以重试
            log.error("MQ 投递NACK msgId={}, cause={}", outboxId, cause);
            // 最多重试5次，退避30秒
            outboxService.incrRetry(outboxId);
        }
    }

    @Override
    public void returnedMessage(ReturnedMessage returnedMessage) {
        Long outboxId = returnedMessage.getMessage().getMessageProperties().getHeader("X-Outbox-Id");
        log.error("Broker已收到，但MQ消息的路由失败了 outboxId={}, replyCode={}, replyText={}",
                        outboxId, returnedMessage.getReplyCode(), returnedMessage.getReplyText());
        // Broker收到消息，只是丢了，标记UNKNOWN，禁止重试
        outboxService.markUnknown(outboxId,
                "Broker已收到，但MQ消息的路由失败了 " +
                        "replyCode=" + returnedMessage.getReplyCode() +", " +
                        "replyText=" + returnedMessage.getReplyText());
    }
}
