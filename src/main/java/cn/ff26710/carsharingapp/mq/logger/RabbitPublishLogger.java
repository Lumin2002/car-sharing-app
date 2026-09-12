package cn.ff26710.carsharingapp.mq.logger;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 打通「投递结果」的反馈链路。
 *
 * <p>application.yml 里本来就开了 publisher-confirm-type 和 publisher-returns，
 * 但没注册回调，等于白配：消息投丢了也没人知道。这里把两类回调接上：
 * <ul>
 *   <li>ConfirmCallback：Broker 确认收到 / 没收到；</li>
 *   <li>ReturnsCallback：消息到了交换机但没匹配到任何队列（路由失败）。</li>
 * </ul>
 * 两者都只记 ERROR 日志 —— 生产环境通常还要接告警，或者落到一张失败表里重试。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitPublishLogger {

    private final RabbitTemplate rabbitTemplate;

    @PostConstruct
    void registerCallbacks() {
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                log.error("MQ 消息未被 Broker 确认: correlationData={}, cause={}", correlationData, cause);
            }
        });

        rabbitTemplate.setReturnsCallback(returned -> log.error(
                "MQ 消息未路由到任何队列: exchange={}, routingKey={}, replyText={}",
                returned.getExchange(), returned.getRoutingKey(), returned.getReplyText()));
    }
}
