package com.mall.mbg.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 会员表
 *
 * @author renmingl
 * @date 2026-08-26 00:27:53
 */
@Getter
@Setter
@ToString
public class Member implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 会员ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 登录账号
     */
    private String username;

    /**
     * 密码（BCrypt 加密）
     */
    private String password;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 手机号（唯一键；无手机号统一存 NULL，勿存空串）
     */
    private String phone;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 头像地址
     */
    private String avatar;

    /**
     * 性别：0未知 1男 2女
     */
    private Byte gender;

    /**
     * 生日
     */
    private LocalDate birthday;

    /**
     * 状态：1正常 0禁用
     */
    private Byte status;

    /**
     * 会员等级：0普通 1白银 2黄金 3钻石（权益划分，非 RBAC）
     */
    private Byte level;

    /**
     * 积分余额（变动流水见 member_point_log）
     */
    private Integer points;

    /**
     * 注册时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
