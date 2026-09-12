package cn.ff26710.carsharingapp.aspect;

import cn.ff26710.carsharingapp.annotation.PublishMQAfterCommit;
import cn.ff26710.carsharingapp.mq.MQEventHolder;
import cn.ff26710.carsharingapp.utils.TransactionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class PublishMQAspect {
    private final RabbitTemplate rabbitTemplate;

    @Around("@annotation(publishMQAfterCommit)")
    public Object around(ProceedingJoinPoint jp, PublishMQAfterCommit publishMQAfterCommit) throws Throwable {
        Object result;
        try {
            result = jp.proceed();
        } catch (Throwable e) {
            MQEventHolder.clear();
            throw e;
        }

        List<MQEventHolder> eventList = MQEventHolder.getAndClear();
        if (eventList.isEmpty()) {
            return result;
        }
        TransactionUtil.afterCommitAsync(() -> sendAll(eventList));
        return result;
    }

    private void sendAll(List<MQEventHolder> eventList) {
        for (MQEventHolder item : eventList) {
            try {
                rabbitTemplate.convertAndSend(item.getExchange(), item.getRoutingKey(), item.getEvent());
            } catch (Exception e) {
                log.error("MQ 投递失败（不影响业务数据）: exchange={}, routingKey={}, event={}",
                        item.getExchange(), item.getRoutingKey(), item.getEvent(), e);
            }
        }
    }
}
