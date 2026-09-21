package cn.ff26710.carsharingapp.mq.event;

import lombok.Data;

import java.io.Serializable;

@Data
public class WxPayRefundNoticeEvent implements MQEvent, Serializable {
    private static final long serialVersionUID = 1L;
    private String refundNo;
    private String wxRefundNo;
    private Long amountCent;
    private String refundState;
    private String rawCallback;

    public static WxPayRefundNoticeEvent of(String refundNo, String wxRefundNo, Long amountCent, String refundState,
                                            String rawCallback) {
        WxPayRefundNoticeEvent event = new WxPayRefundNoticeEvent();
        event.setRefundNo(refundNo);
        event.setWxRefundNo(wxRefundNo);
        event.setAmountCent(amountCent);
        event.setRefundState(refundState);
        event.setRawCallback(rawCallback);
        return event;
    }
}
