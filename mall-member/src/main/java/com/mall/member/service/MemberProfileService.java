package com.mall.member.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.common.exception.BizException;
import com.mall.common.result.ResultCode;
import com.mall.mbg.entity.Member;
import com.mall.mbg.entity.MemberPointLog;
import com.mall.mbg.mapper.MemberMapper;
import com.mall.mbg.mapper.MemberPointLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 买家个人资料 / 会员等级权益 / 积分查询服务
 * 积分写流水（支付返积分 / 退款扣回）随支付阶段实现，本阶段只提供余额与流水查询
 * @author renmingl
 * @date 2026-08-26 12:50:53
 */
@Service
@RequiredArgsConstructor
public class MemberProfileService {

    /** 会员等级权益表（1.5 等级权益：折扣 / 免运费 / 积分倍率，扁平权益模型非 RBAC） */
    private static final Map<Integer, Map<String, Object>> LEVEL_INFO = new LinkedHashMap<>();

    static {
        LEVEL_INFO.put(0, levelInfo(0, "普通会员", 1.00, 1.0, false));
        LEVEL_INFO.put(1, levelInfo(1, "白银会员", 0.98, 1.2, false));
        LEVEL_INFO.put(2, levelInfo(2, "黄金会员", 0.95, 1.5, true));
        LEVEL_INFO.put(3, levelInfo(3, "钻石会员", 0.90, 2.0, true));
    }

    private static Map<String, Object> levelInfo(int level, String name, double discount, double pointsRate, boolean freeShipping) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("level", level);
        info.put("name", name);
        info.put("discount", discount);
        info.put("pointsRate", pointsRate);
        info.put("freeShipping", freeShipping);
        return info;
    }

    private final MemberMapper memberMapper;
    private final MemberPointLogMapper memberPointLogMapper;

    /** 当前会员资料（密码不返回） */
    public Map<String, Object> profile(Long memberId) {
        Member member = getMember(memberId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", member.getId());
        data.put("username", member.getUsername());
        data.put("nickname", member.getNickname());
        data.put("phone", member.getPhone());
        data.put("email", member.getEmail());
        data.put("avatar", member.getAvatar());
        data.put("gender", member.getGender());
        data.put("birthday", member.getBirthday());
        data.put("level", member.getLevel());
        data.put("points", member.getPoints());
        data.put("createTime", member.getCreateTime());
        return data;
    }

    /** 修改资料（仅允许非账号关键字段；手机号唯一键、用户名不可改） */
    public void updateProfile(Long memberId, Member update) {
        Member member = getMember(memberId);
        if (StringUtils.hasText(update.getNickname())) {
            member.setNickname(update.getNickname());
        }
        if (StringUtils.hasText(update.getAvatar())) {
            member.setAvatar(update.getAvatar());
        }
        if (StringUtils.hasText(update.getEmail())) {
            member.setEmail(update.getEmail());
        }
        if (update.getGender() != null) {
            member.setGender(update.getGender());
        }
        if (update.getBirthday() != null) {
            member.setBirthday(update.getBirthday());
        }
        memberMapper.updateById(member);
    }

    /** 会员等级权益说明 */
    public Map<String, Object> levelInfo(Integer level) {
        return LEVEL_INFO.getOrDefault(level == null ? 0 : level, LEVEL_INFO.get(0));
    }

    /** 积分余额 + 等级权益 */
    public Map<String, Object> points(Long memberId) {
        Member member = getMember(memberId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("memberId", member.getId());
        data.put("points", member.getPoints());
        data.put("level", member.getLevel());
        data.put("levelInfo", levelInfo((int) member.getLevel()));
        return data;
    }

    /** 积分流水分页（按时间倒序） */
    public Page<MemberPointLog> pointLogs(Long memberId, long page, long size) {
        return memberPointLogMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<MemberPointLog>()
                        .eq(MemberPointLog::getMemberId, memberId)
                        .orderByDesc(MemberPointLog::getCreateTime));
    }

    private Member getMember(Long memberId) {
        Member member = memberMapper.selectById(memberId);
        if (member == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "账号不存在或已注销");
        }
        return member;
    }
}
