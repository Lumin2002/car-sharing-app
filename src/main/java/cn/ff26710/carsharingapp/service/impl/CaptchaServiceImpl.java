package cn.ff26710.carsharingapp.service.impl;

import cn.ff26710.carsharingapp.exception.BusinessException;
import cn.ff26710.carsharingapp.service.CaptchaService;
import cn.ff26710.carsharingapp.service.SmsService;
import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import cn.hutool.core.util.RandomUtil;
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
    private static final String SMS_RATE_KEY = "captcha:sms:rate:";
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
    public void sendSmsCode(String phone, String imageCode, String imageUuid) {
        verifyImageCaptcha(imageCode, imageUuid);

        String smsRateKey = SMS_RATE_KEY + phone;
        if (stringRedisTemplate.hasKey(smsRateKey)) {
            throw new BusinessException("获取短信验证码过于频繁，请稍后再试");
        }

        String smsCode = RandomUtil.randomNumbers(6);
        String smsCaptchaKey = "captcha:sms:" + phone;
        stringRedisTemplate.opsForValue().set(smsCaptchaKey, smsCode, 5, TimeUnit.MINUTES);
        stringRedisTemplate.opsForValue().set(smsRateKey, "1", 60, TimeUnit.SECONDS);

        Map<String, String> paramMap = new HashMap<>();
        paramMap.put("code", smsCode);
        smsService.sendCode(phone, paramMap);
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