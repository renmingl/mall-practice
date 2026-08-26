package com.mall.member.controller;

import com.mall.api.member.MemberAccountDTO;
import com.mall.api.member.MemberRegisterDTO;
import com.mall.api.member.MemberVerifyResult;
import com.mall.api.member.UpdatePasswordByPhoneDTO;
import com.mall.api.member.VerifyPasswordDTO;
import com.mall.common.result.Result;
import com.mall.member.service.MemberAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 会员内部接口（服务间调用，不经网关）：与 {@code MemberFeignClient} 契约路径一致
 * 认证中心编排注册 / 登录 / 找回密码时调用；密码明文仅在本服务内校验，不跨服务流转
 * @author renmingl
 * @date 2026-08-26 08:03:18
 */
@RestController
@RequestMapping("/internal/member")
@RequiredArgsConstructor
public class MemberInternalController {

    private final MemberAccountService memberAccountService;

    /** 注册：创建买家账号 */
    @PostMapping("/register")
    public Result<MemberAccountDTO> register(@Valid @RequestBody MemberRegisterDTO request) {
        return Result.success(memberAccountService.register(request));
    }

    /** 密码校验：登录用 */
    @PostMapping("/verify")
    public Result<MemberVerifyResult> verify(@Valid @RequestBody VerifyPasswordDTO request) {
        return Result.success(memberAccountService.verify(request.getUsername(), request.getRawPassword()));
    }

    /** 按手机号修改密码：找回密码用 */
    @PostMapping("/update-password-by-phone")
    public Result<Void> updatePasswordByPhone(@Valid @RequestBody UpdatePasswordByPhoneDTO request) {
        memberAccountService.updatePasswordByPhone(request);
        return Result.success();
    }
}
