package cn.ff26710.carsharingapp.config;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.teaopenapi.models.Config;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Getter
public class AliyunConfig {
    @Value("${aliyun.sms.access-key-id:}")
    private String smsAccessKeyId;

    @Value("${aliyun.sms.access-key-secret:}")
    private String smsAccessKeySecret;

    @Value("${aliyun.sms.endpoint:dysmsapi.aliyuncs.com}")
    private String smsEndpoint;

    @Value("${aliyun.sms.sign-name:CAR-SHARING-APP}")
    private String smsSignName;

    @Value("${aliyun.sms.template-code.send-code:}")
    private String templateCodeBySendCode;

    @Bean
    @ConditionalOnProperty(prefix = "aliyun", name = "enabled", havingValue = "true")
    public Client aliyunConfig() throws Exception {
        Config config = new Config();
        config.setAccessKeyId(smsAccessKeyId);
        config.setAccessKeySecret(smsAccessKeySecret);
        config.setEndpoint(smsEndpoint);
        return new Client(config);
    }
}
