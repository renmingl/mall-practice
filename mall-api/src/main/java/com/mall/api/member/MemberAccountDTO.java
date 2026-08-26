package com.mall.api.member;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 会员账号信息（内部契约 DTO：auth 校验登录、网关鉴权透传用）
 * @author renmingl
 * @date 2026-08-26 17:05:37
 */
@Data
public class MemberAccountDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 会员ID */
    private Long id;

    /** 登录账号 */
    private String username;

    /** 密码（BCrypt，仅校验接口内部返回，不落日志） */
    private String password;

    /** 昵称 */
    private String nickname;

    /** 头像 */
    private String avatar;

    /** 手机号 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 状态：1正常 0禁用 */
    private Byte status;

    /** 会员等级：0普通 1白银 2黄金 3钻石 */
    private Byte level;

    /** 积分余额 */
    private Integer points;

    /** 注册时间 */
    private LocalDateTime createTime;
}
