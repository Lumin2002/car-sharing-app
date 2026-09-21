package cn.ff26710.carsharingapp.mq.callback;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class RabbitCallbackSetup {
    private final RabbitTemplate rabbitTemplate;
    private final OutboxRabbitCallback outboxRabbitCallback;
    @PostConstruct
    public void attachCallback() {
        rabbitTemplate.setConfirmCallback(outboxRabbitCallback);
        rabbitTemplate.setReturnsCallback(outboxRabbitCallback);
    }
}
