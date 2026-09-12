package cn.ff26710.carsharingapp.service.impl;

import cn.ff26710.carsharingapp.entity.Message;
import cn.ff26710.carsharingapp.entity.User;
import cn.ff26710.carsharingapp.entity.enums.MessageStatus;
import cn.ff26710.carsharingapp.entity.enums.MessageType;
import cn.ff26710.carsharingapp.exception.BusinessException;
import cn.ff26710.carsharingapp.mapper.MessageMapper;
import cn.ff26710.carsharingapp.service.MessageService;
import cn.ff26710.carsharingapp.utils.SecurityUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MessageServiceImpl extends ServiceImpl<MessageMapper, Message> implements MessageService {
    @Override
    public void sendOrderMsg(Long userId, Long orderId, String title, String content, MessageType messageType) {
        if (userId != null && title != null && content != null && messageType != null) {
            Message message = new Message();
            message.setUserId(userId);
            message.setOrderId(orderId);
            message.setTitle(title);
            message.setContent(content);
            message.setType(messageType);
            message.setReadStatus(MessageStatus.UNREAD);
            message.setCreateTime(LocalDateTime.now());
            save(message);
        } else {
            throw new BusinessException("站内短信有null，请检查");
        }
    }

    @Override
    public IPage<Message> pageMyMsg(long pageNum, long pageSize, String keyword, MessageStatus readStatus, MessageType messageType) {
        Long userId = currentUser().getUserId();
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Message::getUserId, userId);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Message::getTitle, keyword)
                    .or().like(Message::getContent, keyword));
        }
        wrapper.eq(readStatus != null, Message::getReadStatus, readStatus)
                .eq(messageType != null, Message::getType, messageType)
                .orderByDesc(Message::getCreateTime);
        return page(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public void readMsg(Long id) {
        boolean update = lambdaUpdate()
                .eq(Message::getId, id)
                .eq(Message::getUserId, SecurityUtil.getUserId())
                .set(Message::getReadStatus, MessageStatus.READ)
                .set(Message::getUpdateTime, LocalDateTime.now())
                .update();
        if (!update) {
            throw new BusinessException("消息不存在或无权操作");
        }
    }

    @Override
    public void readAllMsg() {
        Long userId = currentUser().getUserId();
        boolean update = lambdaUpdate()
                .eq(Message::getUserId, userId)
                .eq(Message::getReadStatus, MessageStatus.UNREAD)
                .set(Message::getReadStatus, MessageStatus.READ)
                .set(Message::getUpdateTime, LocalDateTime.now())
                .update();
        if (!update) {
            throw new BusinessException("消息不存在或无权操作");
        }
    }

    @Override
    public long countUnread() {
        Long unread = lambdaQuery()
                .eq(Message::getUserId, currentUser().getUserId())
                .eq(Message::getReadStatus, MessageStatus.UNREAD)
                .count();
        return unread == null ? 0L : unread;
    }

    @Override
    public void deleteMsg(Long id) {
        boolean update = lambdaUpdate()
                .eq(Message::getId, id)
                .eq(Message::getUserId, currentUser().getUserId())
                .set(Message::getDeleted, true)
                .set(Message::getUpdateTime, LocalDateTime.now())
                .update();
        if (!update) {
            throw new BusinessException("消息不存在或无权操作");
        }
    }

    @Override
    public boolean hasMsg(Long orderId, MessageType messageType) {
        Long count = lambdaQuery()
                .eq(Message::getType, messageType)
                .eq(Message::getOrderId, orderId)
                .count();
        return count != null && count > 0;
    }

    @Override
    public Set<Long> findOrderIdsHavingMsg(Collection<Long> orderIds, MessageType messageType) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Set.of();
        }
        return lambdaQuery()
                .select(Message::getOrderId)
                .eq(Message::getType, messageType)
                .in(Message::getOrderId, orderIds)
                .list()
                .stream()
                .map(Message::getOrderId)
                .collect(Collectors.toSet());
    }

    private User currentUser() {
        User loginUser = SecurityUtil.getLoginUser();
        if (loginUser == null) {
            throw new BusinessException(401, "未登录");
        }
        return loginUser;
    }
}
