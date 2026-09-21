package cn.ff26710.carsharingapp.mq.consumer;

import cn.ff26710.carsharingapp.config.RabbitMQConfig;
import cn.ff26710.carsharingapp.service.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class DLQConsumer {
    private final OutboxService outboxService;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_DEAD)
    public void consumeDlqMessage(Message message){
        try {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            Map<String,Object> headers = message.getMessageProperties().getHeaders();
            Object outboxIdObj = headers.get("X‑Outbox‑Id");
            if (outboxIdObj == null) {
                log.warn("死信消息缺失X‑Outbox‑Id header，无法关联out记录");
                return;
            }
            Long outboxId = Long.parseLong(outboxIdObj.toString());
            log.error("MQ 消息投递成功了，但消费失败进入了死信队列，body={} , headers={}",
                    body, headers);
            outboxService.markSentButDead(outboxId,
                    "MQ 消息投递成功了，但消费失败进入了死信队列，body="+ body + ",headers=" + headers);
        } catch (Exception e) {
            log.error("处理死信消息异常", e);
        }
    }
}
