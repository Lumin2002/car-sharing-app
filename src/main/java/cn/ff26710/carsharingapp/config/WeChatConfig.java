package cn.ff26710.carsharingapp.config;

import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.service.payments.jsapi.JsapiService;
import com.wechat.pay.java.service.refund.RefundService;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Getter
public class WeChatConfig {
    @Value("${wechat.pay.app-id:}")
    private String appId;

    @Value("${wechat.pay.merchant-id:}")
    private String merchantId;

    @Value("${wechat.pay.private-key-path:}")
    private String privateKeyPath;

    @Value("${wechat.pay.merchant-serial-number:}")
    private String merchantSerialNumber;

    @Value("${wechat.pay.api-v3-key:}")
    private String apiV3Key;

    @Value("${wechat.pay.payment-notify-url:}")
    private String paymentNotifyUrl;

    @Value("${wechat.pay.refund-notify-url:}")
    private String refundNotifyUrl;

    @Value("${wechat.oauth.app-secret:}")
    private String appSecret;

    @Bean
    @ConditionalOnProperty(prefix = "wechat", name = "enabled", havingValue = "true")
    public RSAAutoCertificateConfig rsaAutoCertificateConfig() {
        return new RSAAutoCertificateConfig.Builder()
                .merchantId(merchantId)
                .privateKeyFromPath(privateKeyPath)
                .merchantSerialNumber(merchantSerialNumber)
                .apiV3Key(apiV3Key)
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "wechat", name = "enabled", havingValue = "true")
    public NotificationParser notificationParser(RSAAutoCertificateConfig rsaAutoCertificateConfig) {
        return new NotificationParser(rsaAutoCertificateConfig);
    }

    @Bean
    @ConditionalOnProperty(prefix = "wechat", name = "enabled", havingValue = "true")
    public JsapiService jsapiPayService(RSAAutoCertificateConfig rsaAutoCertificateConfig) {
        return new JsapiService.Builder().config(rsaAutoCertificateConfig).build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "wechat", name = "enabled", havingValue = "true")
    public RefundService refundService(RSAAutoCertificateConfig rsaAutoCertificateConfig) {
        return new RefundService.Builder().config(rsaAutoCertificateConfig).build();
    }
}
