package cn.ff26710.carsharingapp.config;

import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.service.payments.nativepay.NativePayService;
import com.wechat.pay.java.service.refund.RefundService;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Getter
public class WxPayV3Config {
    @Value("${wxpay.v3.app-id:}")
    private String appId;

    @Value("${wxpay.v3.merchant-id:}")
    private String merchantId;

    @Value("${wxpay.v3.private-key-path:}")
    private String privateKeyPath;

    @Value("${wxpay.v3.merchant-serial-number:}")
    private String merchantSerialNumber;

    @Value("${wxpay.v3.api-v3-key:}")
    private String apiV3Key;

    @Value("${wxpay.v3.payment-notify-url:}")
    private String paymentNotifyUrl;

    @Value("${wxpay.v3.refund-notify-url:}")
    private String refundNotifyUrl;

    @Bean
    @ConditionalOnProperty(prefix = "wxpay", name = "enabled", havingValue = "true")
    public RSAAutoCertificateConfig rsaAutoCertificateConfig() {
        return new RSAAutoCertificateConfig.Builder()
                .merchantId(merchantId)
                .privateKeyFromPath(privateKeyPath)
                .merchantSerialNumber(merchantSerialNumber)
                .apiV3Key(apiV3Key)
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "wxpay", name = "enabled", havingValue = "true")
    public NotificationParser notificationParser(RSAAutoCertificateConfig rsaAutoCertificateConfig) {
        return new NotificationParser(rsaAutoCertificateConfig);
    }

    @Bean
    @ConditionalOnProperty(prefix = "wxpay", name = "enabled", havingValue = "true")
    public NativePayService nativePayService(RSAAutoCertificateConfig rsaAutoCertificateConfig) {
        return new NativePayService.Builder().config(rsaAutoCertificateConfig).build();
    }
    @Bean
    @ConditionalOnProperty(prefix = "wxpay", name = "enabled", havingValue = "true")
    public RefundService refundService(RSAAutoCertificateConfig rsaAutoCertificateConfig) {
        return new RefundService.Builder().config(rsaAutoCertificateConfig()).build();
    }
}
