package cn.ff26710.carsharingapp.service.impl;

import cn.ff26710.carsharingapp.config.AliyunConfig;
import cn.ff26710.carsharingapp.exception.BusinessException;
import cn.ff26710.carsharingapp.service.SmsService;
import cn.hutool.json.JSONUtil;
import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmsServiceImpl implements SmsService {
    private final ObjectProvider<AliyunConfig> aliyunConfigProvider;
    private final ObjectProvider<Client> clientProvider;

    @Override
    public void sendCode(String phone, Map<String, String> templateParam) throws Exception {
        String param = JSONUtil.toJsonStr(templateParam);
        AliyunConfig aliyunConfig = aliyunConfigProvider.getIfAvailable();
        if (aliyunConfig == null) {
            throw new BusinessException("阿里云短信服务未启用");
        }
        aliyunHandler(phone,aliyunConfig.getTemplateCodeBySendCode(), param);
    }
    private void aliyunHandler(String phone, String templateCode, String templateParam) throws Exception {
        AliyunConfig aliyunConfig = aliyunConfigProvider.getIfAvailable();
        Client client = clientProvider.getIfAvailable();
        if (aliyunConfig == null || client == null) {
            throw new BusinessException("阿里云短信服务未启用");
        }
        SendSmsRequest sendSmsRequest = new SendSmsRequest()
                .setPhoneNumbers(phone)
                .setSignName(aliyunConfig.getSmsSignName())
                .setTemplateCode(templateCode)
                .setTemplateParam(templateParam);
        SendSmsResponse resp = client.sendSms(sendSmsRequest);
        if (!"OK".equals(resp.getBody().getCode())) {
            throw new BusinessException("阿里云短信发送失败，请联系管理员");
        }
    }
}
