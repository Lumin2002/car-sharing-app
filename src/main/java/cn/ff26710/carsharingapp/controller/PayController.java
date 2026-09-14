package cn.ff26710.carsharingapp.controller;

import cn.ff26710.carsharingapp.dto.pay.WxPayCallbackDTO;
import cn.ff26710.carsharingapp.service.WxPayService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.io.IOException;

@RestController
@RequestMapping("/api/pay")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "wxpay", name = "enabled", havingValue = "true")
public class PayController {
    private final WxPayService wxPayService;

    @PostMapping("/wx/payment-notify")
    public Object wxPaymentNotify(HttpServletRequest request) throws IOException {
        String body = readBody(request);
        String serial = request.getHeader("Wechatpay-Serial");
        String signature = request.getHeader("Wechatpay-Signature");
        String timestamp = request.getHeader("Wechatpay-Timestamp");
        String nonce = request.getHeader("Wechatpay-Nonce");
        String requestType = "Payment";
        WxPayCallbackDTO dto =
                new WxPayCallbackDTO(body, serial, signature, timestamp, nonce, requestType);
        return wxPayService.handlerCallback(dto);
    }

    @PostMapping("/wx/refund-notify")
    public Object wxRefundNotify(HttpServletRequest request) throws IOException {
        String body = readBody(request);
        String serial = request.getHeader("Wechatpay-Serial");
        String signature = request.getHeader("Wechatpay-Signature");
        String timestamp = request.getHeader("Wechatpay-Timestamp");
        String nonce = request.getHeader("Wechatpay-Nonce");
        String requestType = "Refund";
        WxPayCallbackDTO dto =
                new WxPayCallbackDTO(body, serial, signature, timestamp, nonce, requestType);
        return wxPayService.handlerCallback(dto);
    }

    private String readBody(HttpServletRequest request) throws IOException {
        BufferedReader reader = request.getReader();
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        return sb.toString();
    }

}
