package com.mall.member.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mall.api.member.MemberAccountDTO;
import com.mall.api.member.MemberRegisterDTO;
import com.mall.api.member.MemberVerifyResult;
import com.mall.api.member.UpdatePasswordByPhoneDTO;
import com.mall.common.exception.BizException;
import com.mall.common.util.PasswordUtil;
import com.mall.mbg.entity.Member;
import com.mall.mbg.mapper.MemberMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 买家账号服务：注册 / 密码校验 / 改密（数据归属 member，供 auth 经内部契约编排）
 * @author renmingl
 * @date 2026-08-26 13:15:04
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberAccountService {

    private final MemberMapper memberMapper;

    /** 注册：用户名/手机号唯一校验 → BCrypt 加密入库 */
    public MemberAccountDTO register(MemberRegisterDTO request) {
        // 用户名唯一（uk_username 兜底，先查避免异常堆栈）
        Long usernameCount = memberMapper.selectCount(
                new LambdaQueryWrapper<Member>().eq(Member::getUsername, request.getUsername()));
        if (usernameCount > 0) {
            throw new BizException("用户名已被注册");
        }
        // 手机号唯一（uk_phone 兜底；无手机号存 NULL 不存空串）
        String phone = StringUtils.hasText(request.getPhone()) ? request.getPhone() : null;
        if (phone != null) {
            Long phoneCount = memberMapper.selectCount(
                    new LambdaQueryWrapper<Member>().eq(Member::getPhone, phone));
            if (phoneCount > 0) {
                throw new BizException("该手机号已注册");
            }
        }
        Member member = new Member();
        member.setUsername(request.getUsername());
        member.setPassword(PasswordUtil.encode(request.getPassword()));
        member.setNickname(StringUtils.hasText(request.getNickname()) ? request.getNickname() : request.getUsername());
        member.setPhone(phone);
        member.setEmail(StringUtils.hasText(request.getEmail()) ? request.getEmail() : null);
        member.setStatus((byte) 1);
        member.setLevel((byte) 0);
        member.setPoints(0);
        memberMapper.insert(member);
        log.info("买家注册成功：username={}, memberId={}", member.getUsername(), member.getId());
        return toAccountDTO(member, false);
    }

    /** 密码校验：账号存在 + BCrypt 匹配 + 状态正常，任一不满足返回失败原因（不抛异常，避免暴露账号存在性） */
    public MemberVerifyResult verify(String username, String rawPassword) {
        MemberVerifyResult result = new MemberVerifyResult();
        Member member = memberMapper.selectOne(
                new LambdaQueryWrapper<Member>().eq(Member::getUsername, username));
        if (member == null) {
            result.setSuccess(false);
            // 与密码错误同一提示，不暴露账号是否存在（防枚举）
            result.setMessage("用户名或密码错误");
            return result;
        }
        if (!PasswordUtil.matches(rawPassword, member.getPassword())) {
            result.setSuccess(false);
            result.setMessage("用户名或密码错误");
            return result;
        }
        if (member.getStatus() == null || member.getStatus() != 1) {
            result.setSuccess(false);
            result.setMessage("账号已禁用，请联系客服");
            return result;
        }
        result.setSuccess(true);
        result.setAccount(toAccountDTO(member, false));
        return result;
    }

    /** 按手机号修改密码（找回密码：验证码校验已在 auth 完成，此处只定位账号并更新） */
    public void updatePasswordByPhone(UpdatePasswordByPhoneDTO request) {
        Member member = memberMapper.selectOne(
                new LambdaQueryWrapper<Member>().eq(Member::getPhone, request.getPhone()));
        if (member == null) {
            // 与验证码错误同一提示，不暴露手机号是否注册（防枚举）
            throw new BizException("手机号或验证码有误");
        }
        member.setPassword(PasswordUtil.encode(request.getNewPassword()));
        memberMapper.updateById(member);
        log.info("找回密码成功：memberId={}", member.getId());
    }

    /** 实体 → 契约 DTO；withPassword=false 时密码置空（跨服务不流转密文） */
    private MemberAccountDTO toAccountDTO(Member member, boolean withPassword) {
        MemberAccountDTO dto = new MemberAccountDTO();
        dto.setId(member.getId());
        dto.setUsername(member.getUsername());
        dto.setPassword(withPassword ? member.getPassword() : null);
        dto.setNickname(member.getNickname());
        dto.setAvatar(member.getAvatar());
        dto.setPhone(member.getPhone());
        dto.setEmail(member.getEmail());
        dto.setStatus(member.getStatus());
        dto.setLevel(member.getLevel());
        dto.setPoints(member.getPoints());
        dto.setCreateTime(member.getCreateTime());
        return dto;
    }
}
