package cn.ff26710.carsharingapp.entity;

import cn.ff26710.carsharingapp.entity.enums.OutboxMessageStatus;
import cn.ff26710.carsharingapp.mq.event.MQEvent;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@TableName("outbox_message")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxMessage {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String msgId;

    private String exchange;
    private String routingKey;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private MQEvent payload;

    private OutboxMessageStatus status;
    private Integer retryCount;
    private String failMsg;

    private LocalDateTime createdAt;
    private LocalDateTime sentAt;
}