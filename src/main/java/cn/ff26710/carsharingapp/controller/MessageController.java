package cn.ff26710.carsharingapp.controller;

import cn.ff26710.carsharingapp.annotation.OperLogAnnotation;
import cn.ff26710.carsharingapp.convert.MessageConvert;
import cn.ff26710.carsharingapp.dto.message.MessagePageDTO;
import cn.ff26710.carsharingapp.service.MessageService;
import cn.ff26710.carsharingapp.vo.MessageVO;
import cn.ff26710.carsharingapp.vo.ResultVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 站内消息（当前登录用户只能操作自己的消息，不存在越权查询的入口）
 */
@RestController
@RequestMapping("/api/message")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    /** 我的消息分页：可按关键字、已读状态、消息类型筛选 */
    @GetMapping("/my")
    public ResultVO<IPage<MessageVO>> my(@Valid @ModelAttribute MessagePageDTO dto) {
        return ResultVO.success(messageService.pageMyMsg(
                        dto.getPageNum(), dto.getPageSize(),
                        dto.getKeyword(), dto.getReadStatus(), dto.getMessageType())
                .convert(MessageConvert.INSTANCE::toVO));
    }

    /** 未读消息数（前端小红点用） */
    @GetMapping("/unread/count")
    public ResultVO<Long> unreadCount() {
        return ResultVO.success(messageService.countUnread());
    }

    /** 标记单条已读 */
    @PutMapping("/{id}/read")
    public ResultVO<Void> read(@PathVariable Long id) {
        messageService.readMsg(id);
        return ResultVO.success();
    }

    /** 全部标记已读 */
    @PutMapping("/read-all")
    public ResultVO<Void> readAll() {
        messageService.readAllMsg();
        return ResultVO.success();
    }

    /** 删除消息（逻辑删除） */
    @OperLogAnnotation(operType = "MESSAGE", operDesc = "删除站内消息")
    @DeleteMapping("/{id}")
    public ResultVO<Void> delete(@PathVariable Long id) {
        messageService.deleteMsg(id);
        return ResultVO.success();
    }
}
