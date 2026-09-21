package cn.ff26710.carsharingapp.service;

import cn.ff26710.carsharingapp.entity.OutboxMessage;
import cn.ff26710.carsharingapp.mq.event.MQEvent;

import java.util.List;

public interface OutboxService {
    void append(String exchange, String routingKey, MQEvent event, String msgId);
    List<OutboxMessage> listPending(int limit);
    void sendOutboxMessages(List<OutboxMessage> pending);

    Integer markSent(Long id);
    void markFail(Long id, String failMsg);
    void markUnknown(Long id, String failMsg);
    void markSentButDead(Long id, String failMsg);
    void incrRetry(Long id);
}
