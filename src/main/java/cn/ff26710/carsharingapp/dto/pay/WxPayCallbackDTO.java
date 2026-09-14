package cn.ff26710.carsharingapp.dto.pay;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class WxPayCallbackDTO {
    private String body;
    private String serial;
    private String signature;
    private String timestamp;
    private String nonce;
    private String requestType;
}
