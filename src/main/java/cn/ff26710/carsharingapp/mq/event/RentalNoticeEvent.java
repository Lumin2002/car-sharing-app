package cn.ff26710.carsharingapp.mq.event;

import cn.ff26710.carsharingapp.entity.enums.MessageType;
import cn.ff26710.carsharingapp.mq.MQEvent;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 租赁业务通知事件 —— 所有「要通知用户」的业务节点统一用这个载荷。
 *
 * <p>标题和正文在生产者侧组装：只有业务代码知道该写多少钱、哪个时间点，
 * 消费端只负责落库成站内消息，不掺业务逻辑。
 *
 * <p>字段刻意保持简单（都能 JSON 序列化），方便以后换成别的 MQ 或加短信/推送消费者。
 */
@Data
public class RentalNoticeEvent implements MQEvent, Serializable {

    private static final long serialVersionUID = 1L;

    /** 接收人 */
    private Long userId;
    /** 关联订单，站内消息列表里用于跳转 */
    private Long orderId;
    private String orderNo;

    /** 消息类型，同时用于消费端幂等去重 */
    private MessageType messageType;

    private String title;
    private String content;

    private LocalDateTime eventTime;

    public static RentalNoticeEvent of(Long userId, Long orderId, String orderNo,
                                       MessageType messageType, String title, String content) {
        RentalNoticeEvent event = new RentalNoticeEvent();
        event.setUserId(userId);
        event.setOrderId(orderId);
        event.setOrderNo(orderNo);
        event.setMessageType(messageType);
        event.setTitle(title);
        event.setContent(content);
        event.setEventTime(LocalDateTime.now());
        return event;
    }
}
