package cn.ff26710.carsharingapp.vo;

import cn.ff26710.carsharingapp.entity.enums.MessageStatus;
import cn.ff26710.carsharingapp.entity.enums.MessageType;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内消息（对外）。
 * 只去掉继承自 BaseLogicEntity 的 deleted 存储标记。
 */
@Data
public class MessageVO {
    private Long id;
    private Long userId;
    private Long orderId;

    private String title;
    private String content;

    private MessageType type;
    private MessageStatus readStatus;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
