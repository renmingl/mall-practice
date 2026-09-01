package com.mall.member.controller;

import com.mall.common.result.Result;
import com.mall.member.service.MemberStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 前台会员运营接口（10.1 / 10.3）：签到 / 签到状态（当前用户经网关透传 X-User-Id）
 * 在线与日活由 auth 登录成功后经内部契约写入（MemberStatsService.recordActive）
 * @author renmingl
 * @date 2026-09-01 10:00:00
 */
@RestController
@RequestMapping("/api/member/stats")
@RequiredArgsConstructor
public class MemberStatsController {

    /** 网关鉴权后透传的当前用户 ID 请求头（与 mall-gateway AuthGlobalFilter 保持一致） */
    public static final String HEADER_USER_ID = "X-User-Id";

    private final MemberStatsService memberStatsService;

    /** 签到（当天重复签到幂等，返回当月天数/连续天数） */
    @PostMapping("/checkin")
    public Result<Map<String, Object>> checkin(@RequestHeader(HEADER_USER_ID) Long memberId) {
        return Result.success(memberStatsService.checkin(memberId));
    }

    /** 签到状态（当月天数 + 今天是否已签 + 连续天数） */
    @GetMapping("/checkin/status")
    public Result<Map<String, Object>> checkinStatus(@RequestHeader(HEADER_USER_ID) Long memberId) {
        return Result.success(memberStatsService.checkinStatus(memberId));
    }
}
