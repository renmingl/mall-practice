package com.mall.mbg.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 会员收藏表
 *
 * @author renmingl
 * @since 2026-08-26 00:27:53
 */
@Getter
@Setter
@ToString
@TableName("member_favorite")
public class MemberFavorite implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 收藏ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 会员ID
     */
    private Long memberId;

    /**
     * SPU ID
     */
    private Long spuId;

    /**
     * 收藏时间
     */
    private LocalDateTime createTime;
}
