package cn.ff26710.carsharingapp.mq.event;

import cn.ff26710.carsharingapp.mq.MQEvent;
import lombok.Data;

import java.io.Serializable;

@Data
public class WxPayNoticeEvent implements MQEvent, Serializable {
    private static final long serialVersionUID = 1L;
    /** 商户支付单号 paymentNo */
    private String paymentNo;
    /** 微信交易号 */
    private String wxTradeNo;
    /** 交易状态 SUCCESS */
    private String tradeState;
    /** 金额，单位分 */
    private Integer amountCent;
    /** 解密后的原始回调json */
    private String rawCallback;

    public static WxPayNoticeEvent of(String paymentNo, String wxTradeNo, String tradeState,
                                      Integer amountCent, String rawCallback) {
        WxPayNoticeEvent event = new WxPayNoticeEvent();
        event.setPaymentNo(paymentNo);
        event.setWxTradeNo(wxTradeNo);
        event.setTradeState(tradeState);
        event.setAmountCent(amountCent);
        event.setRawCallback(rawCallback);
        return event;
    }
}
