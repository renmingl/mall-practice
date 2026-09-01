package com.mall.api.member;

import com.mall.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * 会员服务内部契约（auth → member 服务间调用，member 表数据归属 mall-member 不动）
 * 说明：登录/注册由认证中心编排，账号数据的创建与密码校验都在 member 侧完成，
 * auth 只负责签发/校验 JWT，避免密码明文跨服务流转。
 * 运营数据（10.1/10.3）：auth 登录成功后调用 recordActive 写在线 + 日活；admin 看板聚合统计
 * @author renmingl
 * @date 2026-08-26 19:38:40
 */
@FeignClient(name = "mall-member", path = "/internal/member", contextId = "memberFeignClient")
public interface MemberFeignClient {

    /** 注册：创建买家账号（username 唯一，密码 BCrypt 加密存储） */
    @PostMapping("/register")
    Result<MemberAccountDTO> register(@RequestBody MemberRegisterDTO request);

    /** 校验密码：核对 BCrypt 密码并返回账号信息（登录用） */
    @PostMapping("/verify")
    Result<MemberVerifyResult> verify(@RequestBody VerifyPasswordDTO request);

    /** 修改密码：按手机号定位账号（找回密码用，验证码校验已在 auth 完成） */
    @PostMapping("/update-password-by-phone")
    Result<Void> updatePasswordByPhone(@RequestBody UpdatePasswordByPhoneDTO request);

    /** 登录成功记录在线 + 日活（10.1/10.3：ZADD online_users + SETBIT active:{yyyyMMdd}） */
    @PostMapping("/record-active")
    Result<Void> recordActive(@RequestParam("memberId") Long memberId);

    /** 实时在线人数（10.1：5 分钟窗口） */
    @GetMapping("/stats/online")
    Result<Long> onlineCount();

    /** 日活（10.3：date=yyyyMMdd，缺省今天） */
    @GetMapping("/stats/dau")
    Result<Long> dau(@RequestParam(value = "date", required = false) String date);

    /** 今日签到人数（看板） */
    @GetMapping("/stats/checkin-today")
    Result<Long> checkinToday();

    /** 今日新增注册会员数（看板） */
    @GetMapping("/stats/new-members")
    Result<Long> newMembersToday();

    /** 会员运营总览（看板聚合：在线/日活/今日签到/今日新增） */
    @GetMapping("/stats/summary")
    Result<Map<String, Object>> statsSummary();
}
