package cn.ff26710.carsharingapp.service;

import cn.ff26710.carsharingapp.config.WxPayV3Config;
import cn.ff26710.carsharingapp.exception.BusinessException;
import cn.ff26710.carsharingapp.utils.AmountUtil;
import com.wechat.pay.java.service.payments.nativepay.NativePayService;
import com.wechat.pay.java.service.payments.nativepay.model.Amount;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayRequest;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayResponse;
import com.wechat.pay.java.service.refund.RefundService;
import com.wechat.pay.java.service.refund.model.AmountReq;
import com.wechat.pay.java.service.refund.model.CreateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@Slf4j
@RequiredArgsConstructor
public class WxPayService {

    private final ObjectProvider<NativePayService> nativePayServiceProvider;
    private final ObjectProvider<RefundService> refundServiceObjectProvider;
    private final WxPayV3Config wxPayV3Config;

    public String createNativePay(String paymentNo, BigDecimal amount, String description) {
        NativePayService nativePayService = nativePayServiceProvider.getIfAvailable();
        if (nativePayService == null) {
            throw new BusinessException("微信支付未启用，请使用其它支付方式");
        }

        PrepayRequest request = new PrepayRequest();
        Amount amountObj = new Amount();
        amountObj.setTotal(AmountUtil.yuanToFenInt(amount));
        request.setAmount(amountObj);
        request.setAppid(wxPayV3Config.getAppId());
        request.setMchid(wxPayV3Config.getMerchantId());
        request.setOutTradeNo(paymentNo);
        request.setDescription(description);
        request.setNotifyUrl(wxPayV3Config.getPaymentNotifyUrl());
        // 调用下单方法，得到应答
        PrepayResponse response = nativePayService.prepay(request);
        log.info("微信预支付下单成功 paymentNo={}, codeUrl={}", paymentNo, response.getCodeUrl());
        return response.getCodeUrl();
    }

    public void createRefundRequest(String paymentNo, String refundNo, String reason,
                                    BigDecimal refundAmount, BigDecimal orderAmount){
        RefundService refundService = refundServiceObjectProvider.getIfAvailable();
        if (refundService == null) {
            throw new BusinessException("微信支付退款未启用，请使用其它支付方式");
        }
        CreateRequest request = new CreateRequest();
        request.setOutTradeNo(paymentNo);
        request.setOutRefundNo(refundNo);
        request.setReason(reason);
        request.setNotifyUrl(wxPayV3Config.getRefundNotifyUrl());

        AmountReq amountReq = new AmountReq();
        amountReq.setRefund(AmountUtil.yuanToFenLong(refundAmount));
        amountReq.setTotal(AmountUtil.yuanToFenLong(orderAmount));
        amountReq.setCurrency("CNY");
        request.setAmount(amountReq);
        refundService.create(request);
    }
}
