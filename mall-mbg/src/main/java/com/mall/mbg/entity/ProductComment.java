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
 * 商品评价表
 *
 * @author renmingl
 * @date 2026-08-26 00:27:53
 */
@Getter
@Setter
@ToString
@TableName("product_comment")
public class ProductComment implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 评价ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 订单项ID（唯一键，防重复评价）
     */
    private Long orderItemId;

    /**
     * 订单号
     */
    private String orderSn;

    /**
     * 会员ID
     */
    private Long memberId;

    /**
     * SPU ID
     */
    private Long spuId;

    /**
     * SKU ID
     */
    private Long skuId;

    /**
     * 评分：1~5
     */
    private Byte rating;

    /**
     * 评价内容
     */
    private String content;

    /**
     * 晒图（JSON数组）
     */
    private String pics;

    /**
     * 状态：1正常 0隐藏
     */
    private Byte status;

    /**
     * 商家回复内容（后台评价管理回复）
     */
    private String reply;

    /**
     * 商家回复时间
     */
    private LocalDateTime replyTime;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
