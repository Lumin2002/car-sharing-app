package cn.ff26710.carsharingapp.service;

public interface CaptchaService {
    byte[] getImageCaptcha(String uuid);
    void sendSmsCode(String phone, String imageCode, String imageUuid, String type);
    void verifyImageCaptcha(String imageCode, String imageUuid);
}
