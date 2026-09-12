package cn.ff26710.carsharingapp.controller;

import cn.ff26710.carsharingapp.annotation.OperLogAnnotation;
import cn.ff26710.carsharingapp.dto.PageDTO;
import cn.ff26710.carsharingapp.entity.RentalSettlement;
import cn.ff26710.carsharingapp.service.RentalSettlementService;
import cn.ff26710.carsharingapp.vo.ResultVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 还车结算
 */
@RestController
@RequestMapping("/api/settlement")
@RequiredArgsConstructor
public class SettlementController {

    private final RentalSettlementService rentalSettlementService;

    /** 我的结算单分页 */
    @GetMapping("/my")
    public ResultVO<IPage<RentalSettlement>> my(@Valid @ModelAttribute PageDTO dto) {
        return ResultVO.success(rentalSettlementService.pageMy(dto.getPageNum(), dto.getPageSize()));
    }

    /** 某订单的结算单（本人或管理员） */
    @GetMapping("/order/{orderId}")
    public ResultVO<RentalSettlement> byOrder(@PathVariable Long orderId) {
        return ResultVO.success(rentalSettlementService.getByOrderId(orderId));
    }

    /** 确认结算：执行押金退还/扣罚 */
    @OperLogAnnotation(operType = "SETTLEMENT", operDesc = "确认还车结算")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/confirm")
    public ResultVO<RentalSettlement> confirm(@PathVariable Long id) {
        return ResultVO.success(rentalSettlementService.confirm(id));
    }
}
