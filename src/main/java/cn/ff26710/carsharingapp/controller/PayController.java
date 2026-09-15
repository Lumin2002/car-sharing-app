package cn.ff26710.carsharingapp.controller;

import cn.ff26710.carsharingapp.dto.pay.WxPayCallbackDTO;
import cn.ff26710.carsharingapp.service.WeChatOAuthService;
import cn.ff26710.carsharingapp.service.WeChatPayService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.IOException;

@RestController
@RequestMapping("/api/pay")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "wechat", name = "enabled", havingValue = "true")
public class PayController {
    private final WeChatOAuthService weChatOAuthService;
    private final WeChatPayService weChatPayService;

    @GetMapping("/wechat/openid")
    public String getOpenId(String code) {
        return weChatOAuthService.getOpenId(code);
    }

    @PostMapping("/wechat/payment-notify")
    public Object wechatPaymentNotify(HttpServletRequest request) throws IOException {
        String body = readBody(request);
        String serial = request.getHeader("Wechatpay-Serial");
        String signature = request.getHeader("Wechatpay-Signature");
        String timestamp = request.getHeader("Wechatpay-Timestamp");
        String nonce = request.getHeader("Wechatpay-Nonce");
        String requestType = "Payment";
        WxPayCallbackDTO dto =
                new WxPayCallbackDTO(body, serial, signature, timestamp, nonce, requestType);
        return weChatPayService.handleCallback(dto);
    }

    @PostMapping("/wechat/refund-notify")
    public Object wechatRefundNotify(HttpServletRequest request) throws IOException {
        String body = readBody(request);
        String serial = request.getHeader("Wechatpay-Serial");
        String signature = request.getHeader("Wechatpay-Signature");
        String timestamp = request.getHeader("Wechatpay-Timestamp");
        String nonce = request.getHeader("Wechatpay-Nonce");
        String requestType = "Refund";
        WxPayCallbackDTO dto =
                new WxPayCallbackDTO(body, serial, signature, timestamp, nonce, requestType);
        return weChatPayService.handleCallback(dto);
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
