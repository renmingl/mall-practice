package com.mall.product.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 发表评价请求（收货后对订单项评价；orderItemId 唯一键防重复评价）
 * @author renmingl
 * @date 2026-08-31 10:00:00
 */
@Data
public class CommentCreateDTO {

    /** 订单项 ID（order 侧校验归属与订单状态） */
    @NotNull(message = "订单项不能为空")
    private Long orderItemId;

    /** 评分：1~5 */
    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分范围为 1~5")
    @Max(value = 5, message = "评分范围为 1~5")
    private Byte rating;

    /** 评价内容 */
    private String content;

    /** 晒图（图片 URL 列表，逗号分隔入库） */
    private List<String> pics;
}
