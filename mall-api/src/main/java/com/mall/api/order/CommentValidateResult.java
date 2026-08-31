package com.mall.api.order;

import lombok.Data;

import java.io.Serializable;

/**
 * 订单项评价校验结果（内部契约 DTO：product 评价前调 order 校验是否可评价）
 * 可评价条件：订单项存在且属于该会员、订单已完成（3）、该订单项未被评价（uk_order_item_id 防重）
 * @author renmingl
 * @date 2026-08-31 10:00:00
 */
@Data
public class CommentValidateResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 订单项 ID（写评价时回传） */
    private Long orderItemId;

    /** 订单号 */
    private String orderSn;

    /** 会员 ID */
    private Long memberId;

    /** SPU ID */
    private Long spuId;

    /** SKU ID */
    private Long skuId;

    /** 商品名称（快照） */
    private String spuName;

    /** 规格描述（快照） */
    private String spec;

    /** 商品图片（快照） */
    private String pic;

    /** 是否可评价 */
    private Boolean canComment;

    /** 不可评价原因 */
    private String reason;
}
