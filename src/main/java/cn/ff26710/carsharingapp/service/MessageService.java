package cn.ff26710.carsharingapp.service;

import cn.ff26710.carsharingapp.entity.Message;
import cn.ff26710.carsharingapp.entity.enums.MessageStatus;
import cn.ff26710.carsharingapp.entity.enums.MessageType;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

public interface MessageService extends IService<Message> {
    void sendOrderMsg(Long userId, Long orderId, String title, String content, MessageType messageType);

    IPage<Message> pageMyMsg(long pageNum, long pageSize, String keyword, MessageStatus readStatus, MessageType messageType);
    void readMsg(Long id);
    void readAllMsg();
    void deleteMsg(Long id);
    long countUnread();

    /** 消费端幂等去重：判断某个订单是否已经生成过该类型的消息 */
    boolean hasMsg(Long orderId, MessageType messageType);

    /** 批量判断哪些订单已经生成过该类型的消息（用于定时任务避免重复投递） */
    java.util.Set<Long> findOrderIdsHavingMsg(java.util.Collection<Long> orderIds, MessageType messageType);
}
