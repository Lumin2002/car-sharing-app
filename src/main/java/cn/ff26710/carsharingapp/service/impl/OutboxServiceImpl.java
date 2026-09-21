package cn.ff26710.carsharingapp.service.impl;

import cn.ff26710.carsharingapp.config.RabbitMQConfig;
import cn.ff26710.carsharingapp.entity.OutboxMessage;
import cn.ff26710.carsharingapp.entity.enums.OutboxMessageStatus;
import cn.ff26710.carsharingapp.mapper.OutboxMessageMapper;
import cn.ff26710.carsharingapp.mq.event.MQEvent;
import cn.ff26710.carsharingapp.service.OutboxService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxServiceImpl extends ServiceImpl<OutboxMessageMapper, OutboxMessage> implements OutboxService {

    private final OutboxMessageMapper outboxMessageMapper;
    private final RabbitTemplate rabbitTemplate;

    private static final Integer MAX_RETRY = 10;

    @Override
    public void append(String exchange, String routingKey, MQEvent event, String msgId) {
        try {
            OutboxMessage outbox = new OutboxMessage();
            outbox.setMsgId(msgId);
            outbox.setExchange(exchange);
            outbox.setRoutingKey(routingKey);
            outbox.setPayload(event);
            outbox.setStatus(OutboxMessageStatus.PENDING);
            outbox.setRetryCount(0);
            outbox.setCreatedAt(LocalDateTime.now());
            outboxMessageMapper.insert(outbox);
        } catch (Exception e) {
            throw new IllegalStateException("写入 outbox 失败，msgId=" + msgId, e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<OutboxMessage> listPending(int limit) {
        LocalDateTime now = LocalDateTime.now();
        List<OutboxMessage> pending = lambdaQuery()
                .eq(OutboxMessage::getStatus, OutboxMessageStatus.PENDING)
                .orderByAsc(OutboxMessage::getCreatedAt)
                .last("LIMIT " + limit + " FOR UPDATE SKIP LOCKED")
                .list();
        for (OutboxMessage outbox : pending) {
            lambdaUpdate()
                    .eq(OutboxMessage::getId, outbox.getId())
                    .eq(OutboxMessage::getStatus, OutboxMessageStatus.PENDING)
                    .set(OutboxMessage::getStatus, OutboxMessageStatus.PROCESSING)
                    .set(OutboxMessage::getSentAt, now)
                    .update();
        }
        return pending;
    }

    @Override
    public void sendOutboxMessages(List<OutboxMessage> pending) {
        for (OutboxMessage outbox : pending) {
            if (outbox.getRetryCount() > MAX_RETRY) {
                markFail(outbox.getId(), "重试10次后无法发送 MQ 信息，已标记Fail");
                continue;
            }
            try {
                CorrelationData correlationData = new CorrelationData(outbox.getId().toString());
                rabbitTemplate.convertAndSend(
                        outbox.getExchange(),
                        outbox.getRoutingKey(),
                        outbox.getPayload(), // 消息体字符串
                        msg -> {
                            msg.getMessageProperties().setContentType(MessageProperties.CONTENT_TYPE_JSON);
                            msg.getMessageProperties().setHeader("X-Outbox-Id", outbox.getId());
                            return msg;
                        },
                        correlationData
                );
                log.info("outbox消息已提交发送等待confirm回调, outboxId={}, msgId={}", outbox.getId(), outbox.getMsgId());
            } catch (Exception e) {
                // send同步抛出异常：消息没有提交给Broker，可以重试
                log.error("outbox同步发送异常 msgId={}, outboxId={}", outbox.getMsgId(), outbox.getId(), e);
                incrRetry(outbox.getId());
            }
        }
    }
    @Override
    public Integer markSent(Long id) {
        return lambdaUpdate()
                .eq(OutboxMessage::getId, id)
                .in(OutboxMessage::getStatus,
                        OutboxMessageStatus.PENDING,
                        OutboxMessageStatus.PROCESSING)
                .set(OutboxMessage::getStatus, OutboxMessageStatus.SENT)
                .set(OutboxMessage::getSentAt, LocalDateTime.now())
                .update(null) ? 1 : 0;
    }
    @Override
    public void markFail(Long id, String failMsg) {
        lambdaUpdate()
                .eq(OutboxMessage::getId, id)
                .in(OutboxMessage::getStatus,
                        OutboxMessageStatus.PENDING,
                        OutboxMessageStatus.PROCESSING)
                .set(OutboxMessage::getFailMsg, failMsg)
                .set(OutboxMessage::getStatus, OutboxMessageStatus.FAIL)
                .update(null);
    }
    @Override
    public void markUnknown(Long id, String failMsg) {
        lambdaUpdate()
                .eq(OutboxMessage::getId, id)
                .in(OutboxMessage::getStatus,
                        OutboxMessageStatus.PENDING,
                        OutboxMessageStatus.PROCESSING)
                .set(OutboxMessage::getStatus, OutboxMessageStatus.UNKNOWN)
                .set(OutboxMessage::getFailMsg, failMsg)
                .update(null);
    }

    @Override
    public void markSentButDead(Long id, String failMsg) {
        lambdaUpdate()
                .eq(OutboxMessage::getId, id)
                .in(OutboxMessage::getStatus,
                        OutboxMessageStatus.PROCESSING,
                        OutboxMessageStatus.SENT)
                .set(OutboxMessage::getStatus, OutboxMessageStatus.SENT_BUT_DEAD)
                .set(OutboxMessage::getFailMsg, failMsg)
                .update(null);
    }

    @Override
    public void incrRetry(Long id) {
        lambdaUpdate().eq(OutboxMessage::getId, id)
                .eq(OutboxMessage::getStatus, OutboxMessageStatus.PROCESSING)
                .set(OutboxMessage::getStatus, OutboxMessageStatus.PENDING)
                .setSql("retry_count = retry_count + 1")
                .update();
    }
}
