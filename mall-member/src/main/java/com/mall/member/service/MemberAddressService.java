package com.mall.member.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mall.common.exception.BizException;
import com.mall.common.result.ResultCode;
import com.mall.mbg.entity.MemberAddress;
import com.mall.member.mapper.MemberAddressExtMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 收货地址服务：增删改查 / 默认地址互斥（同一会员仅一个默认地址）
 * @author renmingl
 * @date 2026-08-26 20:18:19
 */
@Service
@RequiredArgsConstructor
public class MemberAddressService {

    private final MemberAddressExtMapper memberAddressMapper;

    /** 地址列表（默认地址置顶，其余按创建时间倒序） */
    public List<MemberAddress> list(Long memberId) {
        return memberAddressMapper.selectList(new LambdaQueryWrapper<MemberAddress>()
                .eq(MemberAddress::getMemberId, memberId)
                .orderByDesc(MemberAddress::getDefaultFlag)
                .orderByDesc(MemberAddress::getCreateTime));
    }

    /** 地址详情（校验归属，防越权访问他人地址） */
    public MemberAddress get(Long memberId, Long addressId) {
        MemberAddress address = memberAddressMapper.selectById(addressId);
        if (address == null || !address.getMemberId().equals(memberId)) {
            throw new BizException(ResultCode.NOT_FOUND, "地址不存在");
        }
        return address;
    }

    /**
     * 新增地址：会员首条地址自动设为默认；指定默认时走原子互斥更新（并发安全，防双默认）
     */
    @Transactional(rollbackFor = Exception.class)
    public MemberAddress add(Long memberId, MemberAddress address) {
        address.setId(null);
        address.setMemberId(memberId);
        boolean wantDefault = address.getDefaultFlag() != null && address.getDefaultFlag() == 1;
        // 先落非默认，默认标记由下方原子 UPDATE 统一处理，避免并发首条出现双默认
        address.setDefaultFlag((byte) 0);
        memberAddressMapper.insert(address);
        long count = memberAddressMapper.selectCount(
                new LambdaQueryWrapper<MemberAddress>().eq(MemberAddress::getMemberId, memberId));
        if (count == 1 || wantDefault) {
            memberAddressMapper.setDefaultExclusive(memberId, address.getId());
            address.setDefaultFlag((byte) 1);
        }
        return address;
    }

    /** 修改地址（归属校验后按 ID 更新；指定默认时走原子互斥更新） */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long memberId, Long addressId, MemberAddress update) {
        get(memberId, addressId);
        if (update.getDefaultFlag() != null && update.getDefaultFlag() == 1) {
            memberAddressMapper.setDefaultExclusive(memberId, addressId);
            // 默认标记已由原子 SQL 写入，updateById 不再重复更新该列
            update.setDefaultFlag(null);
        }
        update.setId(addressId);
        update.setMemberId(memberId);
        memberAddressMapper.updateById(update);
    }

    /** 删除地址 */
    public void delete(Long memberId, Long addressId) {
        get(memberId, addressId);
        memberAddressMapper.deleteById(addressId);
    }

    /** 设为默认地址（单条原子 UPDATE 互斥：并发设置不同默认地址时仅一条生效） */
    public void setDefault(Long memberId, Long addressId) {
        get(memberId, addressId);
        memberAddressMapper.setDefaultExclusive(memberId, addressId);
    }
}
