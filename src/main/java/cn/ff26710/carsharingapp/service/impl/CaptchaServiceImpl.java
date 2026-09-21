package cn.ff26710.carsharingapp.service.impl;

import cn.ff26710.carsharingapp.exception.BusinessException;
import cn.ff26710.carsharingapp.service.CaptchaService;
import cn.ff26710.carsharingapp.service.SmsService;
import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.ReUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class CaptchaServiceImpl implements CaptchaService {
    private final StringRedisTemplate stringRedisTemplate;
    private final SmsService smsService;

    private static final String IMAGE_CAPTCHA_KEY = "captcha:image:";
    private static final String REGISTER_SMS_CODE_KEY = "captcha:sms:register:";
    private static final String REGISTER_SMS_RATE_KEY = "captcha:sms:login:rate:";
    private static final String LOGIN_SMS_CODE_KEY = "captcha:sms:login:";
    private static final String LOGIN_SMS_RATE_KEY = "captcha:sms:login:rate:";
    private static final String RESET_PASSWORD_SMS_CODE_KEY = "captcha:sms:reset-password:";
    private static final String RESET_PASSWORD_SMS_RATE_KEY = "captcha:sms:reset-password:rate:";
    private static final long IMAGE_EXPIRE_SECONDS = 120;

    @Override
    public byte[] getImageCaptcha(String uuid) {
        LineCaptcha lineCaptcha = CaptchaUtil.createLineCaptcha(200, 80, 4, 25);
        String code = lineCaptcha.getCode();
        stringRedisTemplate.opsForValue().set(IMAGE_CAPTCHA_KEY + uuid, code, IMAGE_EXPIRE_SECONDS, TimeUnit.SECONDS);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        lineCaptcha.write(bos);
        return bos.toByteArray();
    }

    @Override
    public void sendSmsCode(String phone, String imageCode, String imageUuid, String type) {
        String phoneReg = "^1[3-9]\\d{9}$";
        if (!ReUtil.isMatch(phoneReg, phone)) {
            throw new BusinessException("手机号不正确");
        }
        verifyImageCaptcha(imageCode, imageUuid);
        if (type == null) {
            throw new BusinessException("短信验证码类型不能为空");
        }
        String smsCaptchaKeyPrefix;
        String smsRateKeyPrefix;
        switch (type) {
            case "REGISTER" -> {
                smsCaptchaKeyPrefix = REGISTER_SMS_CODE_KEY;
                smsRateKeyPrefix = REGISTER_SMS_RATE_KEY;
            }
            case "LOGIN" -> {
                smsCaptchaKeyPrefix = LOGIN_SMS_CODE_KEY;
                smsRateKeyPrefix = LOGIN_SMS_RATE_KEY;
            }
            case "RESET_PASSWORD" -> {
                smsCaptchaKeyPrefix = RESET_PASSWORD_SMS_CODE_KEY;
                smsRateKeyPrefix = RESET_PASSWORD_SMS_RATE_KEY;
            }
            default -> throw new BusinessException("不支持的短信验证码类型");
        }
        String smsCaptchaKey = smsCaptchaKeyPrefix + phone;
        String smsRateKey = smsRateKeyPrefix+ phone;
        Boolean ok = stringRedisTemplate.opsForValue()
                .setIfAbsent(smsRateKey, "1", 60, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(ok)) {
            throw new BusinessException("获取短信验证码过于频繁，请稍后再试");
        }
        String smsCode = RandomUtil.randomNumbers(6);
        //写入验证码，10分钟有效期
        stringRedisTemplate.opsForValue().set(smsCaptchaKey, smsCode, 10, TimeUnit.MINUTES);

        try {
            Map<String, String> paramMap = new HashMap<>();
            paramMap.put("code", smsCode);
            smsService.sendCode(phone, paramMap);
        } catch (Exception e) {
            //短信发送失败，回滚清理Redis
            stringRedisTemplate.delete(smsCaptchaKey);
            stringRedisTemplate.delete(smsRateKey);
            log.error("短信发送失败 phone={}", phone, e);
            throw new BusinessException("短信发送失败，请稍后重试");
        }
    }

    @Override
    public void verifyImageCaptcha(String imageCode, String imageUuid) {
        String redisKey = IMAGE_CAPTCHA_KEY + imageUuid;
        String cacheCode = stringRedisTemplate.opsForValue().get(redisKey);
        if (cacheCode == null || !cacheCode.equalsIgnoreCase(imageCode)) {
            throw new BusinessException("图形验证码错误或已失效");
        }
        stringRedisTemplate.delete(redisKey);
    }
}