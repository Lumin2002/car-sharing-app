package cn.ff26710.carsharingapp.entity;

import cn.ff26710.carsharingapp.entity.enums.MessageStatus;
import cn.ff26710.carsharingapp.entity.enums.MessageType;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("message")
public class Message extends BaseLogicEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long orderId;
    private String title;
    private String content;
    private MessageType type;
    private MessageStatus readStatus;
}
