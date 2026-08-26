package com.mall.api.member;

import lombok.Data;

import java.io.Serializable;

/**
 * 密码校验结果（内部契约 DTO）
 * @author renmingl
 * @date 2026-08-26 10:34:49
 */
@Data
public class MemberVerifyResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 是否校验通过（账号存在且密码匹配且状态正常） */
    private boolean success;

    /** 校验失败原因（成功时为 null） */
    private String message;

    /** 校验通过的账号信息（失败时为 null，password 字段仅本契约内使用） */
    private MemberAccountDTO account;
}
