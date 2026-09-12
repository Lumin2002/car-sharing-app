package cn.ff26710.carsharingapp.service;

import java.util.Map;

public interface SmsService {
    void sendCode(String phone, Map<String, String> templateParam);
}
