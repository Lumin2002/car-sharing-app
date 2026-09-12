package cn.ff26710.carsharingapp.dto.message;

import cn.ff26710.carsharingapp.dto.PageDTO;
import cn.ff26710.carsharingapp.entity.enums.MessageStatus;
import cn.ff26710.carsharingapp.entity.enums.MessageType;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 站内消息分页查询条件
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MessagePageDTO extends PageDTO {
    private String keyword;
    private MessageStatus readStatus;
    private MessageType messageType;
}
