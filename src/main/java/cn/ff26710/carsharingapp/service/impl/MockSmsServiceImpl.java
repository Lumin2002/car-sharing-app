package cn.ff26710.carsharingapp.service.impl;

import cn.ff26710.carsharingapp.service.SmsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
public class MockSmsServiceImpl implements SmsService {
    @Override
    public void sendCode(String phone, Map<String, String> templateParam) {
        try {
            String code = templateParam.get("code");
            log.info("【Mock短信】手机号：{}，验证码：{}", phone, code);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
