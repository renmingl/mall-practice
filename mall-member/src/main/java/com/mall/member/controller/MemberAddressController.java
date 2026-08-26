package com.mall.member.controller;

import com.mall.common.result.Result;
import com.mall.mbg.entity.MemberAddress;
import com.mall.member.service.MemberAddressService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 买家收货地址接口（1.6 收货地址管理；当前用户经网关透传 X-User-Id）
 * @author renmingl
 * @date 2026-08-26 14:18:31
 */
@RestController
@RequestMapping("/api/member/address")
@RequiredArgsConstructor
public class MemberAddressController {

    private final MemberAddressService memberAddressService;

    /** 地址列表（默认置顶） */
    @GetMapping
    public Result<List<MemberAddress>> list(@RequestHeader(MemberProfileController.HEADER_USER_ID) Long memberId) {
        return Result.success(memberAddressService.list(memberId));
    }

    /** 地址详情 */
    @GetMapping("/{id}")
    public Result<MemberAddress> get(@RequestHeader(MemberProfileController.HEADER_USER_ID) Long memberId,
                                     @PathVariable("id") Long id) {
        return Result.success(memberAddressService.get(memberId, id));
    }

    /** 新增地址（首条自动设为默认） */
    @PostMapping
    public Result<MemberAddress> add(@RequestHeader(MemberProfileController.HEADER_USER_ID) Long memberId,
                                     @RequestBody MemberAddress address) {
        return Result.success(memberAddressService.add(memberId, address));
    }

    /** 修改地址 */
    @PutMapping("/{id}")
    public Result<Void> update(@RequestHeader(MemberProfileController.HEADER_USER_ID) Long memberId,
                               @PathVariable("id") Long id,
                               @RequestBody MemberAddress address) {
        memberAddressService.update(memberId, id, address);
        return Result.success();
    }

    /** 删除地址 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@RequestHeader(MemberProfileController.HEADER_USER_ID) Long memberId,
                               @PathVariable("id") Long id) {
        memberAddressService.delete(memberId, id);
        return Result.success();
    }

    /** 设为默认地址 */
    @PutMapping("/{id}/default")
    public Result<Void> setDefault(@RequestHeader(MemberProfileController.HEADER_USER_ID) Long memberId,
                                   @PathVariable("id") Long id) {
        memberAddressService.setDefault(memberId, id);
        return Result.success();
    }
}
