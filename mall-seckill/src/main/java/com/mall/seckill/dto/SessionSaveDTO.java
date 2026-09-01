package com.mall.seckill.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 秒杀场次保存 DTO（后台管理，14.1）
 * @author renmingl
 * @date 2026-09-01 10:00:00
 */
@Getter
@Setter
public class SessionSaveDTO {

    /** 场次 ID（新增为空） */
    private Long id;

    /** 场次名称 */
    @NotBlank(message = "场次名称必填")
    private String name;

    /** 开始时间 */
    @NotNull(message = "开始时间必填")
    private LocalDateTime startTime;

    /** 结束时间 */
    @NotNull(message = "结束时间必填")
    private LocalDateTime endTime;

    /** 状态：1启用 0禁用 */
    private Byte status;
}
