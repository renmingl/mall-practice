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
 * 品牌表
 *
 * @author renmingl
 * @since 2026-08-26 00:27:53
 */
@Getter
@Setter
@ToString
@TableName("product_brand")
public class ProductBrand implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 品牌ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 品牌名称
     */
    private String name;

    /**
     * 品牌LOGO
     */
    private String logo;

    /**
     * 品牌简介
     */
    private String description;

    /**
     * 排序
     */
    private Integer sort;

    /**
     * 状态：1启用 0禁用
     */
    private Byte status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
