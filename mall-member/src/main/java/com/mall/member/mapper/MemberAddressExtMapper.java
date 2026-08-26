package com.mall.member.mapper;

import com.mall.mbg.mapper.MemberAddressMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 收货地址扩展 Mapper（mbg 生成的基础 Mapper 不可修改，扩展接口承载业务 SQL）
 * @author renmingl
 * @date 2026-08-26 22:20:03
 */
public interface MemberAddressExtMapper extends MemberAddressMapper {

    /**
     * 原子互斥设默认：单条 UPDATE 将该会员全部地址 default_flag 重置（目标地址置 1，其余置 0）。
     * 并发设置不同默认地址时由数据库行锁串行执行，最终保证同一会员仅一条默认地址（并发双默认修复）
     */
    @Update("UPDATE member_address SET default_flag = IF(id = #{addressId}, 1, 0) WHERE member_id = #{memberId}")
    int setDefaultExclusive(@Param("memberId") Long memberId, @Param("addressId") Long addressId);
}
