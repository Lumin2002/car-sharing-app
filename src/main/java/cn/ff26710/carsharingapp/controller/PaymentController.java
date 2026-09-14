package cn.ff26710.carsharingapp.controller;

import cn.ff26710.carsharingapp.annotation.OperLogAnnotation;
import cn.ff26710.carsharingapp.convert.PaymentConvert;
import cn.ff26710.carsharingapp.dto.payment.PayOrderDTO;
import cn.ff26710.carsharingapp.dto.payment.PaymentPageDTO;
import cn.ff26710.carsharingapp.service.PaymentService;
import cn.ff26710.carsharingapp.vo.PayResultVO;
import cn.ff26710.carsharingapp.vo.PaymentVO;
import cn.ff26710.carsharingapp.vo.ResultVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 支付：下单后支付租金 + 冻结押金
 */
@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @OperLogAnnotation(operType = "PAYMENT", operDesc = "订单款项支付")
    @PostMapping("/create")
    public ResultVO<PayResultVO> create(@Valid @RequestBody PayOrderDTO dto) {
        return ResultVO.success(paymentService.createPayment(dto));
    }

    /** 我的支付记录 */
    @GetMapping("/my")
    public ResultVO<IPage<PaymentVO>> my(@Valid @ModelAttribute PaymentPageDTO dto) {
        return ResultVO.success(paymentService.pageMy(
                dto.getPageNum(), dto.getPageSize(), dto.getStatus())
                .convert(PaymentConvert.INSTANCE::toVO));
    }

    /** 某订单的支付记录（本人或管理员） */
    @GetMapping("/order/{orderId}")
    public ResultVO<List<PaymentVO>> byOrder(@PathVariable Long orderId) {
        return ResultVO.success(paymentService.listByOrder(orderId).stream()
                .map(PaymentConvert.INSTANCE::toVO)
                .toList());
    }
}
