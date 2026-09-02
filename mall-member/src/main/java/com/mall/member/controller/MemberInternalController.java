package com.mall.member.controller;

import com.mall.api.member.MemberAccountDTO;
import com.mall.api.member.MemberRegisterDTO;
import com.mall.api.member.MemberVerifyResult;
import com.mall.api.member.UpdatePasswordByPhoneDTO;
import com.mall.api.member.VerifyPasswordDTO;
import com.mall.common.result.Result;
import com.mall.member.service.MemberAccountService;
import com.mall.member.service.MemberAddressService;
import com.mall.member.service.MemberProfileService;
import com.mall.member.service.MemberStatsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

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
    private final MemberStatsService memberStatsService;
    private final MemberProfileService memberProfileService;
    private final MemberAddressService memberAddressService;

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

    // ==================== 运营数据（10.1 / 10.3，auth 登录写入 / admin 看板聚合） ====================

    /** 登录成功记录在线 + 日活（auth 编排登录/注册后调用） */
    @PostMapping("/record-active")
    public Result<Void> recordActive(@RequestParam("memberId") Long memberId) {
        memberStatsService.recordActive(memberId);
        return Result.success();
    }

    /** 实时在线人数（10.1）：5 分钟窗口 */
    @GetMapping("/stats/online")
    public Result<Long> onlineCount() {
        return Result.success(memberStatsService.onlineCount());
    }

    /** 日活（10.3）：date=yyyyMMdd，缺省今天 */
    @GetMapping("/stats/dau")
    public Result<Long> dau(@RequestParam(value = "date", required = false) String date) {
        return Result.success(memberStatsService.dau(date));
    }

    /** 今日签到人数（看板） */
    @GetMapping("/stats/checkin-today")
    public Result<Long> checkinToday() {
        return Result.success(memberStatsService.checkinToday());
    }

    /** 今日新增注册会员数（看板） */
    @GetMapping("/stats/new-members")
    public Result<Long> newMembersToday() {
        return Result.success(memberStatsService.newMembersToday());
    }

    /** 指定会员当月签到天数（看板会员统计） */
    @GetMapping("/stats/checkin-month")
    public Result<Long> checkinMonthDays(@RequestParam("memberId") Long memberId,
                                         @RequestParam(value = "month", required = false) String month) {
        return Result.success(memberStatsService.checkinMonthDays(memberId, month));
    }

    /** 会员运营总览（看板聚合）：在线/日活/今日签到/今日新增 */
    @GetMapping("/stats/summary")
    public Result<Map<String, Object>> statsSummary() {
        Map<String, Object> row = new java.util.LinkedHashMap<>();
        row.put("online", memberStatsService.onlineCount());
        row.put("dau", memberStatsService.dau(null));
        row.put("checkinToday", memberStatsService.checkinToday());
        row.put("newMembersToday", memberStatsService.newMembersToday());
        return Result.success(row);
    }

    // ==================== AI 问答数据供给（阶段 9 16.3：mall-ai 按需拉取拼上下文） ====================

    /** 会员账户概览：基础资料 + 积分/等级 + 地址数（买家问"我的积分/等级/地址"等） */
    @GetMapping("/account-overview")
    public Result<Map<String, Object>> accountOverview(@RequestParam("memberId") Long memberId) {
        Map<String, Object> row = memberProfileService.profile(memberId);
        row.put("addressCount", memberAddressService.list(memberId).size());
        return Result.success(row);
    }
}
