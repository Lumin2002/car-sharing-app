package cn.ff26710.carsharingapp.controller;

import cn.ff26710.carsharingapp.convert.RefundConvert;
import cn.ff26710.carsharingapp.dto.PageDTO;
import cn.ff26710.carsharingapp.service.RefundService;
import cn.ff26710.carsharingapp.vo.RefundVO;
import cn.ff26710.carsharingapp.vo.ResultVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 退款 / 押金解冻记录查询
 */
@RestController
@RequestMapping("/api/refund")
@RequiredArgsConstructor
public class RefundController {

    private final RefundService refundService;

    /** 我的退款记录 */
    @GetMapping("/my")
    public ResultVO<IPage<RefundVO>> my(@Valid @ModelAttribute PageDTO dto) {
        return ResultVO.success(refundService.pageMy(dto.getPageNum(), dto.getPageSize())
                .convert(RefundConvert.INSTANCE::toVO));
    }

    /** 某订单的退款记录（本人或管理员） */
    @GetMapping("/order/{orderId}")
    public ResultVO<List<RefundVO>> byOrder(@PathVariable Long orderId) {
        return ResultVO.success(refundService.listByOrder(orderId).stream()
                .map(RefundConvert.INSTANCE::toVO)
                .toList());
    }
}
