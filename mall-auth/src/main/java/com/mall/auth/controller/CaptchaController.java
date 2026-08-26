package com.mall.auth.controller;

import com.mall.auth.service.AuthRedisService;
import com.mall.auth.util.CaptchaUtil;
import com.mall.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 图形验证码接口（12.5 登录/注册防机器）：uuid + base64 图片，Redis 存码 5 分钟
 * @author renmingl
 * @date 2026-08-26 10:20:44
 */
@RestController
@RequestMapping("/api/auth/captcha")
@RequiredArgsConstructor
public class CaptchaController {

    private final AuthRedisService authRedisService;

    /** 获取验证码：{uuid, imgBase64}，登录/注册时携带 uuid + 用户输入 */
    @GetMapping
    public Result<Map<String, Object>> captcha() {
        CaptchaUtil.Captcha captcha = CaptchaUtil.generate();
        String uuid = UUID.randomUUID().toString().replace("-", "");
        authRedisService.saveCaptcha(uuid, captcha.code());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("uuid", uuid);
        data.put("imgBase64", captcha.imgBase64());
        return Result.success(data);
    }

    /** 模拟短信验证码发送（开发期直接返回验证码，真实短信网关后续接入） */
    @GetMapping("/sms")
    public Result<Map<String, Object>> sms(@RequestParam("phone") String phone) {
        String code = authRedisService.saveSmsCode(phone);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("phone", phone);
        data.put("smsCode", code);
        return Result.success(data);
    }
}
