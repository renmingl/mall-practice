package com.mall.member.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.common.result.Result;
import com.mall.mbg.entity.Member;
import com.mall.mbg.entity.MemberPointLog;
import com.mall.member.service.MemberProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 买家个人中心接口（经网关 → auth 鉴权后透传 X-User-Id 定位当前用户）
 * @author renmingl
 * @date 2026-08-26 08:00:02
 */
@RestController
@RequestMapping("/api/member")
@RequiredArgsConstructor
public class MemberProfileController {

    /** 网关鉴权后透传的当前用户 ID 请求头（与 mall-gateway AuthGlobalFilter 保持一致） */
    public static final String HEADER_USER_ID = "X-User-Id";

    private final MemberProfileService memberProfileService;

    /** 个人资料查询 */
    @GetMapping("/profile")
    public Result<Map<String, Object>> profile(@RequestHeader(HEADER_USER_ID) Long memberId) {
        return Result.success(memberProfileService.profile(memberId));
    }

    /** 修改资料（昵称/头像/邮箱/性别/生日） */
    @PutMapping("/profile")
    public Result<Void> updateProfile(@RequestHeader(HEADER_USER_ID) Long memberId,
                                      @RequestBody Member update) {
        memberProfileService.updateProfile(memberId, update);
        return Result.success();
    }

    /** 会员等级权益说明 */
    @GetMapping("/level-info")
    public Result<Map<String, Object>> levelInfo(@RequestParam(required = false) Integer level) {
        return Result.success(memberProfileService.levelInfo(level));
    }

    /** 积分余额 + 等级权益 */
    @GetMapping("/points")
    public Result<Map<String, Object>> points(@RequestHeader(HEADER_USER_ID) Long memberId) {
        return Result.success(memberProfileService.points(memberId));
    }

    /** 积分流水分页 */
    @GetMapping("/point-logs")
    public Result<Page<MemberPointLog>> pointLogs(@RequestHeader(HEADER_USER_ID) Long memberId,
                                                  @RequestParam(defaultValue = "1") long page,
                                                  @RequestParam(defaultValue = "10") long size) {
        return Result.success(memberProfileService.pointLogs(memberId, page, size));
    }
}
