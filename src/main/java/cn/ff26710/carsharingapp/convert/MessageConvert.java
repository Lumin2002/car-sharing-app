package cn.ff26710.carsharingapp.convert;

import cn.ff26710.carsharingapp.entity.Message;
import cn.ff26710.carsharingapp.vo.MessageVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * Message -> MessageVO（对外视图，屏蔽 deleted）
 */
@Mapper
public interface MessageConvert {

    MessageConvert INSTANCE = Mappers.getMapper(MessageConvert.class);

    MessageVO toVO(Message message);
}
