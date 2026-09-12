package cn.ff26710.carsharingapp.controller;

import cn.ff26710.carsharingapp.annotation.OperLogAnnotation;
import cn.ff26710.carsharingapp.convert.RentalOrderConvert;
import cn.ff26710.carsharingapp.dto.rental.RentalCreateDTO;
import cn.ff26710.carsharingapp.dto.rental.RentalPageDTO;
import cn.ff26710.carsharingapp.dto.rental.RentalReturnDTO;
import cn.ff26710.carsharingapp.service.RentalOrderService;
import cn.ff26710.carsharingapp.vo.ResultVO;
import cn.ff26710.carsharingapp.vo.RentalOrderVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rental")
@RequiredArgsConstructor
public class RentalOrderController {

    private final RentalOrderService rentalOrderService;
    @OperLogAnnotation(operType = "RENTAL", operDesc = "下单租车")
    @PostMapping
    public ResultVO<Long> create(@Valid @RequestBody RentalCreateDTO dto) {
        return ResultVO.success(rentalOrderService.createOrder(dto));
    }

    @GetMapping("/my")
    public ResultVO<IPage<RentalOrderVO>> myOrders(@Valid @ModelAttribute RentalPageDTO dto) {
        return ResultVO.success(rentalOrderService.pageMyOrders(
                        dto.getPageNum(), dto.getPageSize(), dto.getStatus())
                .convert(RentalOrderConvert.INSTANCE::toVO));
    }

    @GetMapping("/page")
    @PreAuthorize("hasRole('ADMIN')")
    public ResultVO<IPage<RentalOrderVO>> page(@Valid @ModelAttribute RentalPageDTO dto) {
        return ResultVO.success(rentalOrderService.pageOrders(
                        dto.getPageNum(), dto.getPageSize(), dto.getUserId(), dto.getCarId(), dto.getStatus())
                .convert(RentalOrderConvert.INSTANCE::toVO));
    }

    @GetMapping("/{id}")
    public ResultVO<RentalOrderVO> detail(@PathVariable Long id) {
        return ResultVO.success(RentalOrderConvert.INSTANCE.toVO(
                rentalOrderService.getOrderForCurrentUser(id)));
    }

    @OperLogAnnotation(operType = "RENTAL", operDesc = "办理还车")
    @PutMapping("/{id}/return")
    public ResultVO<Void> returnCar(@PathVariable Long id, @Valid @RequestBody(required = false) RentalReturnDTO dto) {
        rentalOrderService.returnCar(id, dto == null ? new RentalReturnDTO() : dto);
        return ResultVO.success();
    }

    @OperLogAnnotation(operType = "RENTAL", operDesc = "取消订单")
    @PutMapping("/{id}/cancel")
    public ResultVO<Void> cancel(@PathVariable Long id, @RequestParam(required = false) String reason) {
        rentalOrderService.cancelOrder(id, reason);
        return ResultVO.success();
    }
}
